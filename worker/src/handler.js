/**
 * The portfolio's backend: a visit counter, link clicks, a feedback inbox, private messages, and the owner's app's
 * routes and alerts, on one Cloudflare Worker and one D1 database (migrations/). Everything the site and the app
 * call is here, the push itself in push.js; index.js only hands requests and the daily cron over, so the tests run
 * this file in Node against SQLite without a Workers runtime.
 *
 * Routes:
 *   GET  /                   health
 *   GET  /visits             { total, today, since }    reads the counts without adding to them
 *   POST /visits             { total, today, since }    counts one visit per visitor per day, then answers as GET
 *   POST /clicks             { ok, counted }            one click per visitor per day on a tracked link (JSON, or sendBeacon's text/plain)
 *   POST /events             { ok, counted }            how the page is read: { events: ["section:skills", …] }, each once per visitor per day
 *   GET  /notes              { visits, ratings, notes, total, more, relations }  the public notes, pinned first, then
 *                            newest first, a page at a time (?stars=1..5, ?relation=colleague|friend|special|other,
 *                            ?offset=0..100000, ?limit=1..50; the 1.1 app's ?before=<id>), and every rating counted
 *   POST /feedback           { ok }                     JSON from the page's script, or a plain HTML form post
 *   POST /messages           { ok }                     a private message to the owner: JSON, or a plain HTML form post
 *   GET  /feedback           { visits, items }          owner only: `Authorization: Bearer <ADMIN_TOKEN>`
 *   GET  /inbox              HTML                       owner only: HTTP Basic auth, any user name, password ADMIN_TOKEN;
 *                                                       ?tab=messages (the default) | notes | reading | visits, ?page=2…,
 *                                                       and for messages ?show=archived
 *   POST /inbox/notes/:id    303 → the tab it was on    owner only, from the inbox page: hide, unhide, pin, unpin, delete
 *                                                       (and the 1.0 app's publish, unpublish)
 *   POST /inbox/messages/:id 303 → the tab it was on    owner only, from the inbox page: read, unread, archive, unarchive, delete
 *   /admin/*                 JSON                       the owner's app, Bearer ADMIN_TOKEN: listed at "The owner's app" below
 *
 * A note is public as soon as it arrives: the form says so, and sends `consent`. Notes left before that, without the
 * visitor's tick, keep their words private; their stars still count in the rating, which says how many. The owner
 * takes a note down by hiding it (it then counts nowhere) or deleting it, and may pin a public one so it comes first.
 * The visitor's contact is never public. A message is never public at all.
 *
 * Everything is counted by the owner's local day (TIME_ZONE), and the cron writes that day's report at 23:59.
 *
 * Bindings and settings (wrangler.toml): DB (D1), ALLOWED_ORIGINS, SITE_URL, TIME_ZONE, TRACKED_LINKS. Secrets:
 * ADMIN_TOKEN, VISITOR_SALT, and FCM_SERVICE_ACCOUNT for push (optional: without it alerts are records the app polls).
 */
import { INBOX_PAGE, INBOX_TABS, linkLabel, PAGE_HEADERS, relationLabel, renderInbox, renderNotice, topicLabel } from "./inbox.js";
import { notify } from "./push.js";

export const LIMITS = Object.freeze({
  name: 80,
  contact: 160,
  message: 2000,
  /** Notes one visitor may leave in a day; the next gets a 429. */
  notesPerDay: 5,
  /**
   * And at most one every this many minutes (wrangler.toml's NOTE_GAP_MINUTES overrides it), so a burst from one
   * address is turned away before the day's limit is reached.
   */
  noteGapMinutes: 5,
  /** Messages one visitor may send in a day; the next gets a 429. */
  messagesPerDay: 3,
  /**
   * Notes, and messages, the whole site takes in a day, whoever sends them. The limits above are per network, and a
   * sender with many networks (a routed IPv6 prefix, a proxy pool) is a new visitor from each: without a ceiling
   * they could fill the public notes, the inbox and the owner's phone without end. Far above a real day's notes.
   */
  notesPerDayAll: 40,
  messagesPerDayAll: 40,
  /**
   * Browsers one network may add to a day's visits, and to a day's clicks on one link. A browser's name is whatever
   * the sender says it is, so without this one address could count as any number of visitors.
   */
  visitorsPerNetwork: 30,
  /** Reading events one request may carry, and the different projects a day's events may name (the ids are the sender's words). */
  eventsPerRequest: 16,
  projectsPerDay: 40,
  /** Characters of request body read; more is a 413. */
  body: 16 * 1024,
  /** Public notes a page of `GET /notes` holds, newest first, unless it asks for up to `notesPageMax`. */
  notesPage: 12,
  notesPageMax: 50,
  /** How far into the public notes `GET /notes?offset=` may start: far past any real count, short of a heavy scan. */
  notesOffsetMax: 100_000,
  /** Characters of an FCM token; real ones are under 200. */
  token: 4096,
  /** Characters of a message or note an alert quotes. */
  preview: 120,
  /** Wrong owner passwords one address may try in a window of `ownerWindowMinutes`; the next gets a 429. */
  ownerTries: 10,
  ownerWindowMinutes: 15,
});

export const DEFAULT_TIME_ZONE = "Asia/Manila";
const DEFAULT_LINKS = "linkedin,github";
/** What a message may be about. An empty topic is "other". */
export const TOPICS = Object.freeze(["job", "freelance", "collaboration", "other"]);
/**
 * How a note's writer knows the owner, as the note form asks: optional, and null when they did not say, which the
 * star sheets count as "other". The app's `Relation` has the same wire values, in this order.
 */
export const RELATIONS = Object.freeze(["colleague", "friend", "special", "other"]);
/** The relations a note card shows as a badge, and the owner's alert names: "other" says nothing worth a badge. */
const BADGED = Object.freeze(["colleague", "friend", "special"]);

/**
 * Crawlers, link previews, monitors and CI's Lighthouse run the page's script too. They may read the count, but
 * they are not visits. Real browsers never put a URL in their user agent; most bots do.
 */
const BOT = /bot|crawl|spider|slurp|lighthouse|headless|preview|facebookexternalhit|curl\/|wget\/|python|go-http-client|java\/|https?:\/\//i;

/** A note anyone may read: sent as public, and not taken down. */
const PUBLIC = "consent = 1 AND hidden_at IS NULL";
/** A rating the page counts: any note's that the owner has not hidden, its words public or not. */
const COUNTED = "rating IS NOT NULL AND hidden_at IS NULL";
const ENOUGH_FOR_TODAY = "That is plenty for one day, thank you. Try again tomorrow.";
/** The whole site's day is used up (LIMITS.notesPerDayAll, messagesPerDayAll), whoever is asking. */
const FULL_FOR_TODAY = "The inbox is full for today. Try again tomorrow.";

/** The minutes one address waits between notes: NOTE_GAP_MINUTES when it is a whole number (0 turns it off), else 5. */
function noteGapMinutes(env) {
  const set = String(env.NOTE_GAP_MINUTES ?? "").trim();
  return /^\d+$/.test(set) ? Number(set) : LIMITS.noteGapMinutes;
}

/** The rule's sentence, with the minutes still to wait rounded up: "One note every 5 minutes … in 3 minutes." */
function oneNoteAtATime(gap, waitMs) {
  const minutes = Math.ceil(waitMs / 60000);
  return `One note every ${gap} ${gap === 1 ? "minute" : "minutes"}, thank you. Try again in ${minutes} ${minutes === 1 ? "minute" : "minutes"}.`;
}

/**
 * [ctx] is the Worker's execution context: alerts push in its `waitUntil`, after the visitor has the answer.
 * [now] and [fetch] are for the tests: the clock, and what reaches Google.
 */
export async function handleRequest(request, env, ctx, { now = new Date(), fetch } = {}) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const cors = corsHeaders(request, env);
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  // A page on another site may neither count visits nor leave notes here. A request with no Origin at all (curl,
  // the owner's tools, the owner's app) is fine: the owner's routes have their own lock, and the inbox page's own
  // posts are checked against this worker's origin in inboxAction.
  const origin = request.headers.get("Origin");
  const ownerPage = path.startsWith("/inbox");
  if (origin && !ownerPage && !cors["Access-Control-Allow-Origin"] && request.method !== "GET") {
    return json({ error: "This page may not call the API." }, 403, cors);
  }
  const alerts = { ctx, now, fetch };
  try {
    if (path === "/" && request.method === "GET") return json({ ok: true, service: "aboutyowkey-api" }, 200, cors);
    if (path === "/visits" && request.method === "GET") return json(await visitCounts(env.DB, localDay(env, now)), 200, cors);
    if (path === "/visits" && request.method === "POST") return json(await recordVisit(request, env, now), 200, cors);
    if (path === "/clicks" && request.method === "POST") return await recordClick(request, env, now, cors);
    if (path === "/events" && request.method === "POST") return await recordReading(request, env, now, cors);
    if (path === "/notes" && request.method === "GET") {
      // Short: a visitor who has just left a note reloads the list, and should find it there.
      return json(await publicNotes(env.DB, localDay(env, now), url), 200, { ...cors, "Cache-Control": "public, max-age=15" });
    }
    if (path === "/feedback" && request.method === "POST") return await submitFeedback(request, env, now, cors, alerts);
    if (path === "/feedback" && request.method === "GET") return await listFeedback(request, env, url, now);
    if (path === "/messages" && request.method === "POST") return await submitMessage(request, env, now, cors, alerts);
    if (path === "/inbox" && request.method === "GET") return await inbox(request, env, now, url);
    const action = /^\/inbox\/(notes|messages)\/(\d+)$/.exec(path);
    if (action && request.method === "POST") return await inboxAction(request, env, action[1], Number(action[2]), now, url);
    if (path === "/admin" || path.startsWith("/admin/")) return await admin(request, env, url, path.slice("/admin".length), now, alerts);
    return json({ error: "Not found." }, 404, cors);
  } catch (error) {
    console.error(error); // `npm run tail` shows it; the visitor gets no detail
    return json({ error: "Something broke on the server." }, 500, cors);
  }
}

// ---- Days ----

const dayFormats = new Map();

/**
 * The date of [now] in [timeZone], YYYY-MM-DD: the unit visits, clicks, notes, messages and reports are counted
 * in. The owner's day, so "today" ends at the owner's midnight, not UTC's.
 */
export function day(now, timeZone = DEFAULT_TIME_ZONE) {
  let format = dayFormats.get(timeZone);
  if (!format) {
    format = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" });
    dayFormats.set(timeZone, format);
  }
  const parts = Object.fromEntries(format.formatToParts(now).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function timeZoneOf(env) {
  return env.TIME_ZONE || DEFAULT_TIME_ZONE;
}

function localDay(env, now) {
  return day(now, timeZoneOf(env));
}

// ---- Visits ----

/**
 * The rows of any day but today (?1) in `visitors` and `clickers`, whose keys start with the day. Written as two
 * ranges so SQLite seeks them in the key: `day <> ?1` read every one of today's rows, on every new visitor, and D1
 * bills and limits the rows a query reads (5 million a day on the free plan), so a busy day's reads grew as its square.
 */
const NOT_TODAY = "day < ?1 OR day > ?1";

function visitCountsQuery(db, today) {
  return db
    .prepare(
      "SELECT COALESCE(SUM(count), 0) AS total, MIN(day) AS since, " +
        "COALESCE((SELECT count FROM visits WHERE day = ?1), 0) AS today FROM visits",
    )
    .bind(today);
}

function visitTotals(row) {
  return { total: row.total, today: row.today, since: row.since };
}

async function visitCounts(db, today) {
  return visitTotals(await visitCountsQuery(db, today).first());
}

/**
 * Counts the visit unless this visitor is already counted today, then reads the counts: one batch, so one round
 * trip to D1 rather than up to five, and one transaction, so the count, the country and the visitor's id are kept
 * together or not at all (a failure never leaves an id that would keep its visitor from ever being counted today).
 * The counts go up only while the id is not there yet and its network still has room for one more browser today
 * (LIMITS.visitorsPerNetwork); the batch's third statement then puts the id there, on the same two conditions.
 */
async function recordVisit(request, env, now) {
  const today = localDay(env, now);
  const agent = request.headers.get("User-Agent") || "";
  if (isBot(agent)) return visitCounts(env.DB, today);
  const visitor = await visitorId(request, env, today, agent);
  const db = env.DB;
  // ?3 and ?4 are the ends of the network's ids in the key (networkRange), ?5 how many it may have.
  const room = "(SELECT COUNT(*) FROM visitors WHERE day = ?1 AND visitor >= ?3 AND visitor < ?4) < ?5";
  const isNew = `WHERE NOT EXISTS (SELECT 1 FROM visitors WHERE day = ?1 AND visitor = ?2) AND ${room}`;
  const who = [today, visitor, ...networkRange(visitor), LIMITS.visitorsPerNetwork];
  const results = await db.batch([
    db.prepare(`INSERT INTO visits (day, count) SELECT ?1, 1 ${isNew} ON CONFLICT (day) DO UPDATE SET count = count + 1`).bind(...who),
    db
      .prepare(`INSERT INTO visit_countries (day, country, count) SELECT ?1, ?6, 1 ${isNew} ON CONFLICT (day, country) DO UPDATE SET count = count + 1`)
      .bind(...who, countryOf(request)),
    db.prepare(`INSERT OR IGNORE INTO visitors (day, visitor) SELECT ?1, ?2 WHERE ${room}`).bind(...who),
    // An id carries its day, so an earlier day's rows can never match again: nothing about a visitor outlives the day.
    db.prepare(`DELETE FROM visitors WHERE ${NOT_TODAY}`).bind(today),
    visitCountsQuery(db, today),
  ]);
  return visitTotals(results.at(-1).results[0]);
}

function isBot(agent) {
  return !agent || BOT.test(agent);
}

/**
 * Who is visiting, for today only: 64 hex characters in two halves. The first is the network (a hash of the day, the
 * address's network and a secret salt), the same for every browser behind it, so what one network may add to a day
 * is counted in the key (networkRange). The second is the browser on it (a hash of the day, the address, the
 * browser and the salt). The address is never stored, neither half can be turned back into it without the salt,
 * tomorrow the same person gets a new id, and the ids go with their day (recordVisit, recordClick, forgetYesterday).
 */
async function visitorId(request, env, today, agent) {
  const address = request.headers.get("CF-Connecting-IP") || "unknown";
  const salt = env.VISITOR_SALT || "";
  const [network, browser] = await Promise.all([
    sha256Hex(`${today}|visitor|${networkOf(address)}|${salt}`),
    sha256Hex(`${today}|${address}|${agent}|${salt}`),
  ]);
  return network.slice(0, NETWORK_HEX) + browser.slice(0, 64 - NETWORK_HEX);
}

/** How many of a visitor id's 64 hex characters name its network. */
const NETWORK_HEX = 32;

/** Where the ids of [visitor]'s network start and end in the key: from its network half, up to but not including that half followed by "g", which sorts after every hex digit. */
function networkRange(visitor) {
  const network = visitor.slice(0, NETWORK_HEX);
  return [network, `${network}g`];
}

/**
 * Who is writing, for today's limits on notes and messages: a hash of the day, the sender's network and the salt.
 * Not the browser, which a sender changes at will, and for IPv6 the /64 a device takes its addresses from. Kept
 * only for its day (forgetOldSenders): without the browser in it, the salt alone could turn it back into an address.
 */
async function senderId(request, env, today) {
  const address = request.headers.get("CF-Connecting-IP") || "unknown";
  return sha256Hex(`${today}|sender|${networkOf(address)}|${env.VISITOR_SALT || ""}`);
}

/** All of an IPv4 address; the first 64 bits of an IPv6 one, written out in full so every spelling agrees. */
export function networkOf(address) {
  const value = String(address).trim().toLowerCase();
  const mapped = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/.exec(value);
  if (mapped) return mapped[1];
  if (!value.includes(":")) return value;
  const [head, tail] = value.split("::");
  const left = head ? head.split(":") : [];
  const right = tail ? tail.split(":") : [];
  const groups = tail === undefined ? left : [...left, ...Array(Math.max(8 - left.length - right.length, 0)).fill("0"), ...right];
  return `${groups.slice(0, 4).map((group) => group.padStart(4, "0")).join(":")}::/64`;
}

/** Clears the sender ids of [table]'s rows from before [today]: they only ever held a day's limit. */
async function forgetOldSenders(db, table, today) {
  await db.prepare(`UPDATE ${table} SET visitor = '' WHERE visitor <> '' AND day <> ?1`).bind(today).run();
}

/**
 * Drops every id of a day before [today]: the visitors' and the clickers', and the senders' on notes and messages.
 * Each is dropped anyway by the next visit, click, note or message, but on a quiet site that may be days away; the
 * daily cron calls this, so an id never outlives the day after its own. Only the earlier days': a cron that starts
 * a moment late, after midnight, must not drop the new day's ids, or its visitors would be counted twice.
 */
async function forgetYesterday(db, today) {
  await db.batch([
    db.prepare("DELETE FROM visitors WHERE day < ?1").bind(today),
    db.prepare("DELETE FROM clickers WHERE day < ?1").bind(today),
    db.prepare("UPDATE feedback SET visitor = '' WHERE visitor <> '' AND day < ?1").bind(today),
    db.prepare("UPDATE messages SET visitor = '' WHERE visitor <> '' AND day < ?1").bind(today),
  ]);
  // On its own: before migration 0006 has run the table is not there, and that must not keep the others' ids.
  try {
    await db.prepare("DELETE FROM reading_seen WHERE day < ?1").bind(today).run();
  } catch (error) {
    if (!missingTable(error, "reading_seen")) throw error;
  }
}

/**
 * The country Cloudflare places the address in (two letters; "T1" for Tor), or "XX" when it has none. Kept only as
 * a count per day, never next to a visitor.
 */
function countryOf(request) {
  const country = String(request.cf?.country || request.headers.get("CF-IPCountry") || "").trim().toUpperCase();
  return /^[A-Z0-9]{2}$/.test(country) ? country : "XX";
}

// ---- Link clicks ----

/** The links the page reports clicks on, from TRACKED_LINKS ("linkedin,github"): lowercase, in that order. */
export function trackedLinks(env) {
  return String(env.TRACKED_LINKS ?? DEFAULT_LINKS)
    .split(",")
    .map((link) => link.trim().toLowerCase())
    .filter(Boolean);
}

/**
 * A click on one of the page's tracked links, counted like a visit (in one batch, the same way): once per visitor per
 * day per link, never for a bot, no more browsers from one network than a visit allows, and the earlier days' ids deleted. The page sends it with `navigator.sendBeacon`,
 * which posts text/plain (any other type would need a preflight it cannot make), so that is read as JSON too.
 */
async function recordClick(request, env, now, cors) {
  const body = await readBody(request, { form: false, plain: true, tooLarge: "Send only the link." });
  if (body.error) return json({ error: body.error }, body.status, cors);
  const link = text(body.fields.link)?.toLowerCase();
  if (!link || !trackedLinks(env).includes(link)) return json({ error: "That is not a link the page counts." }, 400, cors);
  const agent = request.headers.get("User-Agent") || "";
  if (isBot(agent)) return json({ ok: true, counted: false }, 200, cors);
  const today = localDay(env, now);
  const visitor = await visitorId(request, env, today, agent);
  const db = env.DB;
  // As a visit: ?4 and ?5 are the ends of the network's ids in the key, ?6 how many it may have on this link today.
  const room = "(SELECT COUNT(*) FROM clickers WHERE day = ?1 AND link = ?2 AND visitor >= ?4 AND visitor < ?5) < ?6";
  const who = [today, link, visitor, ...networkRange(visitor), LIMITS.visitorsPerNetwork];
  const [, claimed] = await db.batch([
    db
      .prepare(
        "INSERT INTO clicks (day, link, count) SELECT ?1, ?2, 1 " +
          `WHERE NOT EXISTS (SELECT 1 FROM clickers WHERE day = ?1 AND link = ?2 AND visitor = ?3) AND ${room} ` +
          "ON CONFLICT (day, link) DO UPDATE SET count = count + 1",
      )
      .bind(...who),
    db.prepare(`INSERT OR IGNORE INTO clickers (day, link, visitor) SELECT ?1, ?2, ?3 WHERE ${room}`).bind(...who),
    db.prepare(`DELETE FROM clickers WHERE ${NOT_TODAY}`).bind(today),
  ]);
  return json({ ok: true, counted: claimed.meta.changes === 1 }, 200, cors);
}

/** A day's clicks as `{ linkedin: 2, github: 1 }`: every tracked link, 0 when nobody clicked it, then any others. */
function clickCounts(links, rows) {
  const counts = Object.fromEntries(links.map((link) => [link, 0]));
  for (const row of rows) counts[row.link] = (counts[row.link] || 0) + row.count;
  return counts;
}

// ---- Reading ----

/** The page's sections, top to bottom: the bar's own names, lowercase (the app's `Section`, the static page's ids). */
export const SECTIONS = Object.freeze(["home", "experience", "projects", "skills", "services", "recognition", "contact"]);
/** Seconds of the page in view that are counted as reached. */
export const TIMES = Object.freeze(["10", "30", "60", "180"]);
export const DEVICES = Object.freeze(["phone", "tablet", "desktop"]);
export const SOURCES = Object.freeze(["direct", "linkedin", "github", "google", "facebook", "other"]);
export const VIEWS = Object.freeze(["app", "plain"]);
const READING = Object.freeze({ section: SECTIONS, time: TIMES, device: DEVICES, source: SOURCES, view: VIEWS });
/** A project's id, as portfolio.json's parser allows it: the one value here the worker cannot list. */
const PROJECT_EVENT = /^project:[a-z0-9][a-z0-9-]{0,39}$/;

/** [value] as a reading event the worker counts ("section:skills", "project:vistago"), or null. */
function readingEvent(value) {
  if (typeof value !== "string") return null;
  if (PROJECT_EVENT.test(value)) return value;
  const cut = value.indexOf(":");
  const allowed = cut > 0 ? READING[value.slice(0, cut)] : null;
  return allowed?.includes(value.slice(cut + 1)) ? value : null;
}

/**
 * How the page is being read, from the page's own script: a list of events, each counted like a click (in one
 * batch, once per visitor per day, never for a bot, one network adding no more browsers than a visit allows).
 * Only counts are kept: no order, no times, nothing that says which visitor did what. What is not an event the
 * worker knows is dropped, and a day names at most LIMITS.projectsPerDay projects, since their ids are the
 * sender's words. Before migration 0006 has run there is nowhere to count, and the page is told so quietly.
 */
async function recordReading(request, env, now, cors) {
  const body = await readBody(request, { form: false, plain: true, tooLarge: "Send fewer events." });
  if (body.error) return json({ error: body.error }, body.status, cors);
  if (!Array.isArray(body.fields.events)) return json({ error: "Send { events: [...] }." }, 400, cors);
  const events = [...new Set(body.fields.events.map(readingEvent).filter(Boolean))].slice(0, LIMITS.eventsPerRequest);
  const agent = request.headers.get("User-Agent") || "";
  if (!events.length || isBot(agent)) return json({ ok: true, counted: 0 }, 200, cors);
  const today = localDay(env, now);
  const visitor = await visitorId(request, env, today, agent);
  const db = env.DB;
  // ?4 and ?5 are the ends of the network's ids in the key, ?6 how many it may have on this event today; ?7 is how
  // many projects a day may name, which only a project not yet named today is held to.
  const room =
    "(SELECT COUNT(*) FROM reading_seen WHERE day = ?1 AND event = ?2 AND visitor >= ?4 AND visitor < ?5) < ?6 " +
    "AND (?2 NOT LIKE 'project:%' OR EXISTS (SELECT 1 FROM reading WHERE day = ?1 AND event = ?2) " +
    "OR (SELECT COUNT(*) FROM reading WHERE day = ?1 AND event LIKE 'project:%') < ?7)";
  const statements = () => events.flatMap((event) => {
    const who = [today, event, visitor, ...networkRange(visitor), LIMITS.visitorsPerNetwork, LIMITS.projectsPerDay];
    return [
      db
        .prepare(
          "INSERT INTO reading (day, event, count) SELECT ?1, ?2, 1 " +
            `WHERE NOT EXISTS (SELECT 1 FROM reading_seen WHERE day = ?1 AND event = ?2 AND visitor = ?3) AND ${room} ` +
            "ON CONFLICT (day, event) DO UPDATE SET count = count + 1",
        )
        .bind(...who),
      // Kept only where it was counted: the count's row is there by now, so a project past the day's limit is not.
      db
        .prepare(
          "INSERT OR IGNORE INTO reading_seen (day, event, visitor) SELECT ?1, ?2, ?3 " +
            "WHERE EXISTS (SELECT 1 FROM reading WHERE day = ?1 AND event = ?2) AND " +
            "(SELECT COUNT(*) FROM reading_seen WHERE day = ?1 AND event = ?2 AND visitor >= ?4 AND visitor < ?5) < ?6",
        )
        .bind(...who.slice(0, 6)),
    ];
  });
  try {
    const results = await db.batch([...statements(), db.prepare(`DELETE FROM reading_seen WHERE ${NOT_TODAY}`).bind(today)]);
    const counted = events.filter((_, index) => results[index * 2].meta.changes === 1).length;
    return json({ ok: true, counted }, 200, cors);
  } catch (error) {
    if (!missingTable(error, "reading")) throw error;
    return json({ ok: true, counted: 0 }, 200, cors);
  }
}

/** Whether [error] is the database saying a table whose name starts with [name] is not there: its migration has not run. */
function missingTable(error, name) {
  return new RegExp(`no such table: ${name}`, "i").test(String(error?.message ?? error));
}

/**
 * What the owner reads of it: each event's count over the last [days] local days up to [today], and the visits of
 * the same days to set them against. Empty before migration 0006 has run.
 */
async function readingSummary(db, today, days) {
  const from = new Date(Date.parse(`${today}T00:00:00Z`) - (days - 1) * 86400000).toISOString().slice(0, 10);
  const visits = await db.prepare("SELECT COALESCE(SUM(count), 0) AS n FROM visits WHERE day BETWEEN ?1 AND ?2").bind(from, today).first("n");
  let rows = [];
  try {
    rows = (await db.prepare("SELECT event, SUM(count) AS count FROM reading WHERE day BETWEEN ?1 AND ?2 GROUP BY event").bind(from, today).all()).results;
  } catch (error) {
    if (!missingTable(error, "reading")) throw error;
  }
  const of = (kind) =>
    rows
      .filter((row) => row.event.startsWith(`${kind}:`))
      .map((row) => ({ name: row.event.slice(kind.length + 1), count: row.count }));
  const listed = (kind, names) => {
    const counts = new Map(of(kind).map((row) => [row.name, row.count]));
    return names.map((name) => ({ name, count: counts.get(name) ?? 0 }));
  };
  return {
    from,
    to: today,
    days,
    visits,
    // The sections the page has, in its order: one nobody has reached in all these days is one the page does not have.
    sections: listed("section", SECTIONS).filter((row, index) => index === 0 || row.count > 0),
    times: listed("time", TIMES),
    devices: listed("device", DEVICES),
    sources: listed("source", SOURCES),
    views: listed("view", VIEWS),
    projects: of("project").sort((a, b) => b.count - a.count || a.name.localeCompare(b.name)),
  };
}

// ---- Public notes ----

/** What a public note shows: never the contact, nor when it was pinned, only that it is. */
const NOTE_COLUMNS = "id, name, rating, message, COALESCE(day, substr(created_at, 1, 10)) AS date, relation, pinned_at IS NOT NULL AS pinned";
/** The order notes are listed in: the pinned first, the newest pin first, then the newest note first. */
const PINNED_FIRST = "pinned_at IS NULL, pinned_at DESC, id DESC";

/**
 * What the page shows: the visits; the rating, star by star, from every note the owner has not hidden; and a page
 * of the public notes, pinned first, then newest first: all of them, or those of [stars] and of [relation]. The
 * rating is always the whole site's, whatever the list asks for; `private` (and `privateStars`, star by star) says
 * how many of its ratings came with words the visitor kept private, so the page can say why it shows fewer notes
 * than ratings. `total` is how many notes match, so `offset` can page on; `relations` is how many of the star's
 * notes each relation has, for the star sheet's sorter. The 1.1 app pages with `before` instead: the notes older
 * than that id, newest first, leaving out the pinned ones, which its first page (pinned first) has already shown.
 * Never a contact.
 */
async function publicNotes(db, today, url) {
  const params = url.searchParams;
  const stars = wholeNumber(params.get("stars"), 1, 5);
  const relation = relationParam(params.get("relation"));
  const before = wholeNumber(params.get("before"), 1, Number.MAX_SAFE_INTEGER);
  const offset = wholeNumber(params.get("offset"), 0, LIMITS.notesOffsetMax) ?? 0;
  const limit = wholeNumber(params.get("limit"), 1, LIMITS.notesPageMax) ?? LIMITS.notesPage;
  const ofStar = noteFilter({ stars });
  const matching = noteFilter({ stars, relation });
  const next = matching.values.length + 1;
  const page = before
    ? db
        .prepare(`SELECT ${NOTE_COLUMNS} FROM feedback WHERE ${matching.where} AND pinned_at IS NULL AND id < ?${next} ORDER BY id DESC LIMIT ?${next + 1}`)
        .bind(...matching.values, before, limit + 1)
    : db
        .prepare(`SELECT ${NOTE_COLUMNS} FROM feedback WHERE ${matching.where} ORDER BY ${PINNED_FIRST} LIMIT ?${next} OFFSET ?${next + 1}`)
        .bind(...matching.values, limit, offset);
  const [visits, { results: counted }, { n: total }, { results: said }, { results: rows }] = await Promise.all([
    visitCounts(db, today),
    db.prepare(`SELECT rating, COUNT(*) AS n, SUM(consent = 0) AS private FROM feedback WHERE ${COUNTED} GROUP BY rating`).all(),
    db.prepare(`SELECT COUNT(*) AS n FROM feedback WHERE ${matching.where}`).bind(...matching.values).first(),
    db
      .prepare(`SELECT COALESCE(relation, 'other') AS relation, COUNT(*) AS n FROM feedback WHERE ${ofStar.where} GROUP BY COALESCE(relation, 'other')`)
      .bind(...ofStar.values)
      .all(),
    page.all(),
  ]);
  const notes = rows.slice(0, limit).map((row) => ({ ...row, pinned: Boolean(row.pinned) }));
  // `before` reads one row more than it shows, to know; by offset, the count says.
  const more = before ? rows.length > limit : offset + notes.length < total;
  return { visits, ratings: ratingSummary(counted), notes, total, more, relations: relationCounts(said) };
}

/**
 * The public notes of [stars] and of [relation] (null for any), as SQL and the values it binds, ?1 on, in order:
 * nothing from the request is written into the SQL itself. A note that did not say how they know the owner is "other".
 */
function noteFilter({ stars = null, relation = null }) {
  const clauses = [PUBLIC];
  const values = [];
  if (stars) clauses.push(`rating = ?${values.push(stars)}`);
  if (relation) clauses.push(`COALESCE(relation, 'other') = ?${values.push(relation)}`);
  return { where: clauses.join(" AND "), values };
}

/** `{ colleague, friend, special, other }`, 0 for a relation no note has. */
function relationCounts(rows) {
  const counts = Object.fromEntries(RELATIONS.map((relation) => [relation, 0]));
  for (const row of rows) if (Object.hasOwn(counts, row.relation)) counts[row.relation] = row.n;
  return counts;
}

/**
 * `{ count, average, stars: { "1": n … "5": n }, private, privateStars: { "1": n … "5": n } }` from the counted
 * ratings grouped by star: `privateStars` is `private` star by star, what each star's sheet cannot show.
 */
function ratingSummary(rows) {
  const stars = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  const privateStars = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  let count = 0;
  let sum = 0;
  let kept = 0;
  for (const row of rows) {
    stars[row.rating] = row.n;
    privateStars[row.rating] = row.private;
    count += row.n;
    sum += row.rating * row.n;
    kept += row.private;
  }
  return { count, average: count ? Math.round((sum / count) * 10) / 10 : null, stars, private: kept, privateStars };
}

/** A `relation` query parameter: one of RELATIONS, whatever its case; null when it is missing or anything else. */
function relationParam(value) {
  const relation = String(value ?? "").trim().toLowerCase();
  return RELATIONS.includes(relation) ? relation : null;
}

/** A query parameter as a whole number from [min] to [max]; null when it is missing or anything else. */
function wholeNumber(value, min, max) {
  if (value == null || !/^\d+$/.test(value)) return null;
  const number = Number(value);
  return number >= min && number <= max ? number : null;
}

// ---- Feedback ----

const NOTE_THANKS = "Your note is in; I read every one.";

async function submitFeedback(request, env, now, cors, alerts) {
  const body = await readBody(request);
  if (body.error) return reply(body.form, { error: body.error }, body.status, cors, env);
  const fields = body.fields;
  // The trap: a field people never see, which bots that fill every field fill. Say yes and keep nothing.
  if (fields.website) return reply(body.form, { ok: true }, 201, cors, env);
  const problems = validateFeedback(fields);
  if (problems.length) return reply(body.form, { error: problems[0], problems }, 400, cors, env);

  const today = localDay(env, now);
  const visitor = await senderId(request, env, today);
  const note = normalizeFeedback(fields);
  if (await resent(env.DB, "feedback", visitor, note.message, note.rating)) return reply(body.form, { ok: true }, 201, cors, env);
  const refused = await noteRefusal(env, visitor, now, today);
  if (refused) return reply(body.form, { error: refused }, 429, cors, env);
  const since = new Date(now.getTime() - noteGapMinutes(env) * 60000).toISOString();
  // The limits again, in the insert itself: notes sent at once all pass the check above before any is kept.
  const { meta } = await env.DB
    .prepare(
      "INSERT INTO feedback (created_at, day, name, contact, rating, message, visitor, consent, relation) " +
        "SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9 WHERE (SELECT COUNT(*) FROM feedback WHERE visitor = ?7) < ?10 " +
        "AND NOT EXISTS (SELECT 1 FROM feedback WHERE visitor = ?7 AND created_at > ?11) " +
        "AND (SELECT COUNT(*) FROM feedback WHERE day = ?2) < ?12",
    )
    .bind(
      now.toISOString(), today, note.name, note.contact, note.rating, note.message, visitor, note.consent ? 1 : 0, note.relation,
      LIMITS.notesPerDay, since, LIMITS.notesPerDayAll,
    )
    .run();
  if (meta.changes !== 1) return reply(body.form, { error: (await noteRefusal(env, visitor, now, today)) || ENOUGH_FOR_TODAY }, 429, cors, env);
  await forgetOldSenders(env.DB, "feedback", today);
  // The owner's alert names the visitor (or "Someone"), never their contact.
  await alertOwner(env, alerts, {
    kind: "feedback",
    title: noteTitle(note),
    body: `${note.name || "Someone"}: ${preview(note.message)}`,
    refId: Number(meta.last_row_id),
  });
  return reply(body.form, { ok: true }, 201, cors, env);
}

/**
 * Whether this sender (today's id) already sent these words today, with the same stars for a note: a resend after an
 * answer that never arrived (a timeout, a lost connection, a second press of a plain form's button). It is answered
 * as the first was, before the limits, and kept once: no second row, no second alert, no 429 for a note that is in.
 * A note whose stars changed is a new note, and meets the limits.
 */
async function resent(db, table, visitor, message, rating) {
  const sameStars = table === "feedback" ? " AND rating IS ?3" : "";
  const statement = db.prepare(`SELECT 1 AS sent FROM ${table} WHERE visitor = ?1 AND message = ?2${sameStars} LIMIT 1`);
  const row = await (table === "feedback" ? statement.bind(visitor, message, rating) : statement.bind(visitor, message)).first();
  return Boolean(row);
}

/**
 * Why [visitor] may not leave a note at [now]: their day's notes used up, the last one too recent, or the whole
 * site's notes for [today] used up. Null if they may.
 */
async function noteRefusal(env, visitor, now, today) {
  const { n, last, all } = await env.DB
    .prepare(
      "SELECT COUNT(*) AS n, MAX(created_at) AS last, (SELECT COUNT(*) FROM feedback WHERE day = ?2) AS \"all\" " +
        "FROM feedback WHERE visitor = ?1",
    )
    .bind(visitor, today)
    .first();
  if (n >= LIMITS.notesPerDay) return ENOUGH_FOR_TODAY;
  if (all >= LIMITS.notesPerDayAll) return FULL_FOR_TODAY;
  const gap = noteGapMinutes(env);
  const wait = last && gap ? Date.parse(last) + gap * 60000 - now.getTime() : 0;
  return wait > 0 ? oneNoteAtATime(gap, wait) : null;
}

/** "New note (★5) · Colleague": the stars when given, and who it is from when that is worth a badge. */
function noteTitle({ rating, relation }) {
  const title = rating ? `New note (★${rating})` : "New note";
  return BADGED.includes(relation) ? `${title} · ${relationLabel(relation)}` : title;
}

/**
 * JSON from the page's script, or the plain form a browser without it posts; with [plain], text/plain read as JSON
 * (sendBeacon's type). Anything else is turned away.
 */
async function readBody(request, { form: forms = true, plain = false, tooLarge = `Keep the note under ${LIMITS.message} characters.` } = {}) {
  const type = (request.headers.get("Content-Type") || "").split(";")[0].trim().toLowerCase();
  const form = forms && type === "application/x-www-form-urlencoded";
  const isJson = type === "application/json" || (plain && type === "text/plain");
  if (!form && !isJson) return { form: false, error: forms ? "Send JSON or a URL-encoded form." : "Send JSON.", status: 415 };
  // The most bytes an allowed body takes: UTF-8 is at most 3 bytes a character, and a form writes each of those
  // bytes as three ("%E4"). A body that says it is larger is never read; one that does not say (a chunked upload)
  // is read only that far.
  const most = LIMITS.body * (form ? 9 : 3);
  if (Number(request.headers.get("Content-Length")) > most) return { form, error: tooLarge, status: 413 };
  const text = await readText(request, most);
  if (text === null) return { form, error: tooLarge, status: 413 };
  if (form) {
    // A form's limit is on what it says, not on how it is written: non-Latin text is three to nine times longer encoded.
    const pairs = Array.from(new URLSearchParams(text));
    const said = pairs.reduce((sum, [name, value]) => sum + name.length + value.length, 0);
    return said > LIMITS.body ? { form, error: tooLarge, status: 413 } : { form, fields: Object.fromEntries(pairs) };
  }
  if (text.length > LIMITS.body) return { form, error: tooLarge, status: 413 };
  let fields;
  try {
    fields = JSON.parse(text);
  } catch {
    return { form, error: "The body is not valid JSON.", status: 400 };
  }
  if (!fields || typeof fields !== "object" || Array.isArray(fields)) return { form, error: "Send a JSON object.", status: 400 };
  return { form, fields };
}

/**
 * [request]'s body as text, or null once it is past [most] bytes: read a piece at a time and dropped there, where
 * `request.text()` would first take in all of a body that gave no Content-Length.
 */
async function readText(request, most) {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const pieces = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > most) {
      await reader.cancel().catch(() => {});
      return null;
    }
    pieces.push(value);
  }
  const bytes = new Uint8Array(size);
  let at = 0;
  for (const piece of pieces) {
    bytes.set(piece, at);
    at += piece.byteLength;
  }
  return new TextDecoder().decode(bytes);
}

/** A field as text: trimmed, a number spelled out, nothing for null; null for anything that is not a value. */
function text(value) {
  if (value == null) return "";
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  return null;
}

/** The consent box: JSON `true`, or what a form or the page's script sends for a ticked box. */
export function isYes(value) {
  return value === true || ["1", "true", "on", "yes"].includes(String(value ?? "").trim().toLowerCase());
}

/**
 * What stops a note from being kept, message first, in sentences for the visitor. The same rules as the app's
 * `FeedbackDraft.problems`, so the form says it before anything is sent; this is the check that counts.
 */
export function validateFeedback(fields) {
  const problems = [];
  const message = text(fields.message);
  const name = text(fields.name);
  const contact = text(fields.contact);
  const rating = text(fields.rating);
  const relation = text(fields.relation);
  if ([message, name, contact, rating, relation].includes(null)) return ["Each field is text."];
  if (!message) problems.push("Write a message first.");
  else if (message.length > LIMITS.message) problems.push(`Keep the message under ${LIMITS.message} characters.`);
  if (name.length > LIMITS.name) problems.push(`Keep the name under ${LIMITS.name} characters.`);
  if (contact.length > LIMITS.contact) problems.push(`Keep the email under ${LIMITS.contact} characters.`);
  if (rating !== "" && !/^[1-5]$/.test(rating)) problems.push("The rating is 1 to 5.");
  if (relation && !RELATIONS.includes(relation.toLowerCase())) problems.push("Pick one of the ways you know me.");
  return problems;
}

/**
 * The note as stored: trimmed, the optional fields null when empty, the rating a number, the consent a boolean,
 * the relation lowercase.
 */
export function normalizeFeedback(fields) {
  const rating = text(fields.rating);
  return {
    name: text(fields.name) || null,
    contact: text(fields.contact) || null,
    rating: rating ? Number(rating) : null,
    message: text(fields.message),
    consent: isYes(fields.consent),
    relation: text(fields.relation).toLowerCase() || null,
  };
}

// ---- Messages ----

const MESSAGE_THANKS = "Your message is in; I will write back to the address you left.";

/**
 * A private message to the owner: never shown on the site, kept with the contact it must have, and limited per
 * visitor per day like notes. The same trap field as the note form.
 */
async function submitMessage(request, env, now, cors, alerts) {
  const body = await readBody(request, { tooLarge: `Keep the message under ${LIMITS.message} characters.` });
  const answer = (payload, status) => reply(body.form, payload, status, cors, env, MESSAGE_THANKS);
  if (body.error) return answer({ error: body.error }, body.status);
  const fields = body.fields;
  if (fields.website) return answer({ ok: true }, 201);
  const problems = validateMessage(fields);
  if (problems.length) return answer({ error: problems[0], problems }, 400);

  const today = localDay(env, now);
  const visitor = await senderId(request, env, today);
  const message = normalizeMessage(fields);
  if (await resent(env.DB, "messages", visitor, message.message)) return answer({ ok: true }, 201);
  const refused = () => messageRefusal(env.DB, visitor, today);
  const refusedNow = await refused();
  if (refusedNow) return answer({ error: refusedNow }, 429);
  // The limits again, in the insert itself, for messages sent at once.
  const { meta } = await env.DB
    .prepare(
      "INSERT INTO messages (created_at, day, name, contact, topic, message, visitor) " +
        "SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7 WHERE (SELECT COUNT(*) FROM messages WHERE visitor = ?7) < ?8 " +
        "AND (SELECT COUNT(*) FROM messages WHERE day = ?2) < ?9",
    )
    .bind(now.toISOString(), today, message.name, message.contact, message.topic, message.message, visitor, LIMITS.messagesPerDay, LIMITS.messagesPerDayAll)
    .run();
  if (meta.changes !== 1) return answer({ error: (await refused()) || ENOUGH_FOR_TODAY }, 429);
  await forgetOldSenders(env.DB, "messages", today);
  // The one alert with a contact in it, for the owner's phone only: who to write back to.
  await alertOwner(env, alerts, {
    kind: "message",
    title: `New message · ${topicLabel(message.topic)}`,
    body: `${message.name || message.contact}: ${preview(message.message)}`,
    refId: Number(meta.last_row_id),
  });
  return answer({ ok: true }, 201);
}

/** Why [visitor] may not send a message: their day's messages used up, or the whole site's for [today]. Null if they may. */
async function messageRefusal(db, visitor, today) {
  const { n, all } = await db
    .prepare("SELECT COUNT(*) AS n, (SELECT COUNT(*) FROM messages WHERE day = ?2) AS \"all\" FROM messages WHERE visitor = ?1")
    .bind(visitor, today)
    .first();
  if (n >= LIMITS.messagesPerDay) return ENOUGH_FOR_TODAY;
  return all >= LIMITS.messagesPerDayAll ? FULL_FOR_TODAY : null;
}

/** What stops a message from being kept, message first, then the contact it needs, in sentences for the visitor. */
export function validateMessage(fields) {
  const problems = [];
  const message = text(fields.message);
  const contact = text(fields.contact);
  const name = text(fields.name);
  const topic = text(fields.topic);
  if ([message, contact, name, topic].includes(null)) return ["Each field is text."];
  if (!message) problems.push("Write a message first.");
  else if (message.length > LIMITS.message) problems.push(`Keep the message under ${LIMITS.message} characters.`);
  if (!contact) problems.push("Leave an email or another way to reach you.");
  else if (contact.length > LIMITS.contact) problems.push(`Keep the email under ${LIMITS.contact} characters.`);
  if (name.length > LIMITS.name) problems.push(`Keep the name under ${LIMITS.name} characters.`);
  if (topic && !TOPICS.includes(topic.toLowerCase())) problems.push("Pick one of the topics.");
  return problems;
}

/** The message as stored: trimmed, no name null, the topic lowercase and "other" when none was picked. */
export function normalizeMessage(fields) {
  return {
    name: text(fields.name) || null,
    contact: text(fields.contact),
    topic: text(fields.topic).toLowerCase() || "other",
    message: text(fields.message),
  };
}

// ---- Alerts ----

/** The start of what a visitor wrote, for an alert: whole characters, so an emoji is never cut in half. */
function preview(message) {
  return Array.from(message).slice(0, LIMITS.preview).join("");
}

/**
 * An alert for the owner, once the visitor's words are kept. A failure here is logged, never the visitor's problem:
 * their note or message is in either way, and the inbox shows it.
 */
async function alertOwner(env, { ctx, now, fetch }, alert) {
  try {
    await notify(env, ctx, alert, { now, fetch });
  } catch (error) {
    console.error("Alert not recorded:", error);
  }
}

// ---- The owner ----

async function listFeedback(request, env, url, now) {
  const entry = await authorize(bearerToken(request), env, { request, now });
  if (entry !== true) return entry;
  const [visits, items] = await Promise.all([visitCounts(env.DB, localDay(env, now)), feedbackItems(env.DB, limitOf(url, 100, 500))]);
  return json({ visits, items }, 200, { "Cache-Control": "no-store" });
}

/**
 * The owner's page, a tab at a time, so a tab reads only what it shows: the counts every tab's header has (one
 * query), then the tab's own rows, a page of INBOX_PAGE at a time. Messages are the first tab: they are what waits
 * on the owner.
 */
async function inbox(request, env, now, url) {
  const entry = await authorize(basicPassword(request), env, { basic: true, request, now });
  if (entry !== true) return entry;
  const db = env.DB;
  const today = localDay(env, now);
  const asked = url.searchParams.get("tab");
  const tab = INBOX_TABS.includes(asked) ? asked : INBOX_TABS[0];
  const page = wholeNumber(url.searchParams.get("page"), 1, 100000) ?? 1;
  const archived = url.searchParams.get("show") === "archived";
  const all = (statement) => statement.all().then((result) => result.results);
  const head = db
    .prepare(
      "SELECT (SELECT COALESCE(SUM(count), 0) FROM visits) AS visits, (SELECT MIN(day) FROM visits) AS since, " +
        "COALESCE((SELECT count FROM visits WHERE day = ?1), 0) AS today, " +
        "(SELECT COUNT(*) FROM messages WHERE archived_at IS NULL) AS waiting, " +
        "(SELECT COUNT(*) FROM messages WHERE archived_at IS NOT NULL) AS archived, " +
        "(SELECT COUNT(*) FROM messages WHERE read_at IS NULL AND archived_at IS NULL) AS unread, " +
        `(SELECT COUNT(*) FROM feedback) AS notes, (SELECT COUNT(*) FROM feedback WHERE ${PUBLIC}) AS shown, ` +
        `(SELECT ROUND(AVG(rating), 1) FROM feedback WHERE ${COUNTED}) AS rating`,
    )
    .bind(today)
    .first();
  const offset = (page - 1) * INBOX_PAGE;
  // One more than a page: whether there is a next one, without counting.
  const paged = (rows) => ({ rows: rows.slice(0, INBOX_PAGE), more: rows.length > INBOX_PAGE });
  const tabs = {
    messages: async () => ({
      archived,
      ...paged(
        await all(
          db
            .prepare(
              "SELECT id, created_at, name, contact, topic, message, read_at, archived_at FROM messages " +
                `WHERE archived_at IS ${archived ? "NOT NULL" : "NULL"} ORDER BY id DESC LIMIT ?1 OFFSET ?2`,
            )
            .bind(INBOX_PAGE + 1, offset),
        ),
      ),
    }),
    notes: async () =>
      paged(
        await all(
          db
            .prepare(
              "SELECT id, created_at, name, contact, rating, message, consent, published_at, hidden_at, relation, pinned_at FROM feedback " +
                `ORDER BY ${PINNED_FIRST} LIMIT ?1 OFFSET ?2`,
            )
            .bind(INBOX_PAGE + 1, offset),
        ),
      ),
    reading: () => readingSummary(db, today, 30),
    visits: async () => {
      const [days, clicks, countries] = await Promise.all([
        all(db.prepare("SELECT day, count FROM visits ORDER BY day DESC LIMIT 30")),
        all(db.prepare("SELECT link, SUM(count) AS total, COALESCE(SUM(CASE WHEN day = ?1 THEN count END), 0) AS today FROM clicks GROUP BY link").bind(today)),
        all(db.prepare("SELECT country, SUM(count) AS count FROM visit_countries GROUP BY country ORDER BY count DESC, country LIMIT 12")),
      ]);
      const byLink = new Map(clicks.map((row) => [row.link, row]));
      return {
        days,
        countries,
        clicks: trackedLinks(env).map((link) => ({ link, total: byLink.get(link)?.total ?? 0, today: byLink.get(link)?.today ?? 0 })),
      };
    },
  };
  const [counts, data] = await Promise.all([head, tabs[tab]()]);
  return html(renderInbox({ tab, page, counts, data, today, timeZone: timeZoneOf(env) }), 200, {
    "Cache-Control": "no-store",
    "X-Robots-Tag": "noindex",
  });
}

/**
 * One of the inbox page's buttons. The browser sends the inbox's Basic credentials with any request to this
 * origin, a form on another site included, so only a post from this origin itself is acted on.
 */
async function inboxAction(request, env, kind, id, now, url) {
  const entry = await authorize(basicPassword(request), env, { basic: true, request, now });
  if (entry !== true) return entry;
  if (!fromThisOrigin(request, url)) {
    const things = kind === "notes" ? "Notes" : "Messages";
    return html(renderNotice("Refused", `${things} are acted on from the inbox page only.`, "/inbox"), 403);
  }
  const { action = "", back = "" } = Object.fromEntries(new URLSearchParams(await request.text()));
  const outcome = await (kind === "notes" ? noteAction : messageAction)(env.DB, id, action, now);
  // Back where the button was: the tab, its page, and the card itself unless it is gone.
  const tab = kind === "notes" ? "/inbox?tab=notes" : "/inbox";
  const from = backTo(back) ?? tab;
  if (outcome.error) return html(renderNotice(outcome.title, outcome.error, from), outcome.status);
  const card = action === "delete" ? "" : `#${kind === "notes" ? "n" : "m"}${id}`;
  return new Response(null, { status: 303, headers: { Location: from + card } });
}

/** The inbox address a button says it was pressed on, when it is one: `/inbox` and its own query, nothing else. */
function backTo(back) {
  return /^\/inbox(\?[a-z0-9=&]{1,60})?$/.test(back) ? back : null;
}

/**
 * What the owner may do to a note, from the inbox's buttons or the app: one set of rules for both. A note is public
 * on arrival, so the owner's levers are hide and unhide (and delete). "publish" and "unpublish" are the 1.0 app's
 * names for those two, kept so its buttons still work; publishing a note sent without the visitor's tick is
 * refused, as before. "make-public" is the owner's own word that the writer of a private note (one sent before
 * notes were public, without the tick) has since agreed: it goes on the site like any other. Pin puts a note first on the site, so only a note on the site can be pinned; hiding one
 * unpins it, and unhiding leaves it unpinned: the owner pins it again if it should still come first. A second pin
 * keeps the first time. Returns `{ status: 200 }`, or a status with a title and the sentence saying why not.
 */
async function noteAction(db, id, action, now) {
  const note = await db.prepare("SELECT id, consent, hidden_at FROM feedback WHERE id = ?1").bind(id).first();
  if (!note) return refusal(404, "Not found", `There is no note ${id}.`);
  const stamp = now.toISOString();
  let statement;
  switch (action) {
    case "publish":
      if (!note.consent) return refusal(409, "Not published", "The visitor did not agree to this note being shown.");
      statement = db.prepare("UPDATE feedback SET published_at = COALESCE(published_at, ?1), hidden_at = NULL WHERE id = ?2").bind(stamp, id);
      break;
    case "make-public":
      statement = db.prepare("UPDATE feedback SET consent = 1, hidden_at = NULL WHERE id = ?1").bind(id);
      break;
    case "unpublish":
    case "hide":
      statement = db.prepare("UPDATE feedback SET hidden_at = ?1, pinned_at = NULL WHERE id = ?2").bind(stamp, id);
      break;
    case "unhide":
      statement = db.prepare("UPDATE feedback SET hidden_at = NULL WHERE id = ?1").bind(id);
      break;
    case "pin":
      if (!note.consent || note.hidden_at) return refusal(409, "Not pinned", "Only a note on the site can be pinned.");
      statement = db.prepare("UPDATE feedback SET pinned_at = COALESCE(pinned_at, ?1) WHERE id = ?2").bind(stamp, id);
      break;
    case "unpin":
      statement = db.prepare("UPDATE feedback SET pinned_at = NULL WHERE id = ?1").bind(id);
      break;
    case "delete":
      // With its alert: the record quotes the note, and "deleted" means its words are gone from here.
      await db.batch([db.prepare("DELETE FROM feedback WHERE id = ?1").bind(id), forgetAlerts(db, "feedback", id)]);
      return { status: 200 };
    default:
      return refusal(400, "Refused", `There is no "${action}" to do with a note.`);
  }
  await statement.run();
  return { status: 200 };
}

/**
 * What the owner may do to a message, from the inbox's buttons or the app. Read and archived are separate marks; a
 * second "read" keeps the first time it was read.
 */
async function messageAction(db, id, action, now) {
  const message = await db.prepare("SELECT id FROM messages WHERE id = ?1").bind(id).first();
  if (!message) return refusal(404, "Not found", `There is no message ${id}.`);
  const stamp = now.toISOString();
  const statements = {
    read: () => db.prepare("UPDATE messages SET read_at = COALESCE(read_at, ?1) WHERE id = ?2").bind(stamp, id),
    unread: () => db.prepare("UPDATE messages SET read_at = NULL WHERE id = ?1").bind(id),
    archive: () => db.prepare("UPDATE messages SET archived_at = COALESCE(archived_at, ?1) WHERE id = ?2").bind(stamp, id),
    unarchive: () => db.prepare("UPDATE messages SET archived_at = NULL WHERE id = ?1").bind(id),
  };
  if (action === "delete") {
    // With its alert: the record quotes the message and names who sent it, and "deleted" means both are gone from here.
    await db.batch([db.prepare("DELETE FROM messages WHERE id = ?1").bind(id), forgetAlerts(db, "message", id)]);
    return { status: 200 };
  }
  if (!Object.hasOwn(statements, action)) return refusal(400, "Refused", `There is no "${action}" to do with a message.`);
  await statements[action]().run();
  return { status: 200 };
}

/** Deletes the alert records about the note or message [id] ([kind] is the record's: "feedback" or "message"). */
function forgetAlerts(db, kind, id) {
  return db.prepare("DELETE FROM notifications WHERE kind = ?1 AND ref_id = ?2").bind(kind, id);
}

function refusal(status, title, error) {
  return { status, title, error };
}

/** Browsers say where a request came from: `Sec-Fetch-Site` when they know it, else the `Origin` of a post. */
function fromThisOrigin(request, url) {
  const site = request.headers.get("Sec-Fetch-Site");
  if (site) return site === "same-origin";
  return request.headers.get("Origin") === url.origin;
}

/**
 * The owner's list of notes, for the app and the inbox: in the site's order, pinned first, so a pinned note older
 * than the newest [limit] is still on it.
 */
async function feedbackItems(db, limit) {
  const { results } = await db
    .prepare(
      "SELECT id, created_at, name, contact, rating, message, consent, published_at, hidden_at, relation, pinned_at FROM feedback " +
        `ORDER BY ${PINNED_FIRST} LIMIT ?1`,
    )
    .bind(limit)
    .all();
  return results;
}

async function messageItems(db, limit) {
  const { results } = await db
    .prepare("SELECT id, created_at, name, contact, topic, message, read_at, archived_at FROM messages ORDER BY id DESC LIMIT ?1")
    .bind(limit)
    .all();
  return results;
}

/** A `limit` query parameter: a whole number from 1 to [max], [fallback] when it is missing or not a number. */
function limitOf(url, fallback, max) {
  const n = Math.floor(Number(url.searchParams.get("limit")));
  return Math.min(Math.max(n || fallback, 1), max);
}

/**
 * True for the owner, else the response that turns the caller away. With no ADMIN_TOKEN set nobody gets in, the
 * owner included: an inbox is never open by default. An address that has given LIMITS.ownerTries wrong passwords
 * is turned away, the right one included, until the window ends (ownerTry).
 */
async function authorize(secret, env, { basic = false, request, now }) {
  if (!env.ADMIN_TOKEN) {
    return json({ error: "ADMIN_TOKEN is not set on the worker: run `npx wrangler secret put ADMIN_TOKEN`." }, 503);
  }
  const attempt = typeof secret === "string" && secret ? await ownerTry(request, env, now) : null;
  if (attempt?.uncounted) {
    const sentence = "The sign-in could not be checked just now. Try again in a moment.";
    const headers = { "Retry-After": "60", "Cache-Control": "no-store" };
    return basic ? html(renderNotice("Not now", sentence, null), 503, headers) : json({ error: sentence }, 503, headers);
  }
  if (attempt?.wait) {
    const minutes = Math.ceil(attempt.wait / 60);
    const sentence = `Too many wrong passwords from here. Try again in ${minutes} ${minutes === 1 ? "minute" : "minutes"}.`;
    const headers = { "Retry-After": String(attempt.wait), "Cache-Control": "no-store" };
    return basic ? html(renderNotice("Not now", sentence, null), 429, headers) : json({ error: sentence }, 429, headers);
  }
  if (attempt && (await sameSecret(secret, env.ADMIN_TOKEN))) {
    await attempt.right();
    return true;
  }
  if (!basic) return json({ error: "Unauthorized." }, 401);
  return html(
    renderNotice("Sign in", "The inbox is the site owner's. Any user name; the password is ADMIN_TOKEN.", null),
    401,
    { "WWW-Authenticate": 'Basic realm="Portfolio inbox", charset="UTF-8"', "Cache-Control": "no-store" },
  );
}

/**
 * One password try from [request]'s address, counted in `owner_tries` before it is checked, so tries sent at once
 * cannot all pass under the limit; `right()` gives it back. `wait` is the seconds left in the window once the
 * address is over the limit; a try turned away there is given back too, so the count never passes the limit and the
 * owner's own requests, sent at once, cannot lock the owner out. Without the table (migration 0005 not applied yet)
 * nothing is counted. Any other failure to count is `uncounted`, and the password is not tried: a database that
 * cannot count must not become a way around the limit.
 */
async function ownerTry(request, env, now) {
  const window = LIMITS.ownerWindowMinutes * 60000;
  const start = Math.floor(now.getTime() / window) * window;
  const since = new Date(start).toISOString();
  const address = request.headers.get("CF-Connecting-IP") || "unknown";
  const client = await sha256Hex(`owner|${networkOf(address)}|${env.VISITOR_SALT || ""}`);
  const tidy = () => env.DB.prepare("DELETE FROM owner_tries WHERE count <= 0 OR since <> ?1").bind(since);
  const giveBack = () => env.DB.prepare("UPDATE owner_tries SET count = count - 1 WHERE client = ?1 AND since = ?2").bind(client, since);
  try {
    const count = await env.DB
      .prepare(
        "INSERT INTO owner_tries (client, since, count) VALUES (?1, ?2, 1) " +
          "ON CONFLICT (client, since) DO UPDATE SET count = count + 1 RETURNING count",
      )
      .bind(client, since)
      .first("count");
    if (count > LIMITS.ownerTries) {
      await env.DB.batch([giveBack(), tidy()]);
      return { wait: Math.ceil((start + window - now.getTime()) / 1000) };
    }
  } catch (error) {
    console.error("Owner tries not counted:", error);
    return /no such table: owner_tries/i.test(String(error?.message ?? error)) ? { wait: 0, right: async () => {} } : { uncounted: true };
  }
  return {
    wait: 0,
    // One round trip: the try given back, and the rows that hold nothing any more (this one's, earlier windows') gone.
    right: () => env.DB.batch([giveBack(), tidy()]),
  };
}

function bearerToken(request) {
  const match = /^Bearer\s+(\S+)$/i.exec(request.headers.get("Authorization") || "");
  return match ? match[1] : null;
}

function basicPassword(request) {
  const match = /^Basic\s+(\S+)$/i.exec(request.headers.get("Authorization") || "");
  if (!match) return null;
  try {
    const decoded = new TextDecoder().decode(Uint8Array.from(atob(match[1]), (c) => c.charCodeAt(0)));
    const colon = decoded.indexOf(":");
    return colon < 0 ? null : decoded.slice(colon + 1);
  } catch {
    return null;
  }
}

/** Compares digests of equal length, so neither the secret's length nor an early mismatch shows in the timing. */
async function sameSecret(given, expected) {
  if (typeof given !== "string" || !given) return false;
  const [a, b] = await Promise.all([sha256(given), sha256(expected)]);
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

async function sha256(input) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input)));
}

async function sha256Hex(input) {
  return Array.from(await sha256(input), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

// ---- The owner's app ----
//
//   GET    /admin/summary                 today's and all-time numbers, and whether push is set up
//   GET    /admin/days?limit=30           one item per local day with any data, newest first, with its countries
//   GET    /admin/feedback?limit=100      the notes, pinned first, then newest first, with their relation and pinned_at
//   POST   /admin/feedback/:id            { action: hide | unhide | pin | unpin | delete, or the 1.0 app's publish | unpublish }
//   GET    /admin/messages?limit=100      the messages, newest first
//   POST   /admin/messages/:id            { action: read | unread | archive | unarchive | delete }
//   GET    /admin/notifications?after=<id>&limit=50   the alerts' records: newest first, or after an id oldest first
//   GET    /admin/reports?limit=60        the cron's daily reports, newest first
//   GET    /admin/reading?limit=30        how the page was read over that many days: sections reached, projects opened, time, device, source
//   POST   /admin/devices                 { token, platform }  a phone signs in for push
//   DELETE /admin/devices                 { token }            and out
//
// The app is native, so these need no CORS. Every answer, refusals included, is `Cache-Control: no-store`.

/** The token is checked before the route, so a wrong one learns nothing about which routes exist. */
async function admin(request, env, url, route, now, alerts) {
  const entry = await authorize(bearerToken(request), env, { request, now });
  if (entry !== true) {
    entry.headers.set("Cache-Control", "no-store");
    return entry;
  }
  const method = request.method;
  if (route === "/summary" && method === "GET") return ownerJson(await summary(env, now));
  if (route === "/days" && method === "GET") return ownerJson({ items: await dayItems(env, limitOf(url, 30, 366)) });
  if (route === "/feedback" && method === "GET") return ownerJson({ items: await feedbackItems(env.DB, limitOf(url, 100, 500)) });
  if (route === "/messages" && method === "GET") return ownerJson({ items: await messageItems(env.DB, limitOf(url, 100, 500)) });
  if (route === "/notifications" && method === "GET") return await notifications(env.DB, url);
  if (route === "/reports" && method === "GET") return ownerJson({ items: await reportItems(env.DB, limitOf(url, 60, 366)) });
  if (route === "/reading" && method === "GET") return ownerJson(await readingSummary(env.DB, localDay(env, now), limitOf(url, 30, 366)));
  if (route === "/devices" && method === "POST") return await addDevice(request, env, now);
  if (route === "/devices" && method === "DELETE") return await removeDevice(request, env);
  const target = /^\/(feedback|messages)\/(\d+)$/.exec(route);
  if (target && method === "POST") return await adminAction(request, env, target[1], Number(target[2]), now);
  return ownerJson({ error: "Not found." }, 404);
}

function ownerJson(payload, status = 200) {
  return json(payload, status, { "Cache-Control": "no-store" });
}

/**
 * The app's first screen. `messages.unread` and `messages.hiring` leave archived messages out: they are the ones
 * still waiting on the owner. `hiring` is a job or freelance message.
 */
async function summary(env, now) {
  const db = env.DB;
  const today = localDay(env, now);
  const [visits, clicks, notes, messages, devices] = await Promise.all([
    visitCounts(db, today),
    db
      .prepare("SELECT link, SUM(count) AS total, COALESCE(SUM(CASE WHEN day = ?1 THEN count END), 0) AS today FROM clicks GROUP BY link")
      .bind(today)
      .all(),
    db
      .prepare(
        "SELECT COUNT(*) AS total, COUNT(CASE WHEN day = ?1 THEN 1 END) AS today, " +
          `COUNT(CASE WHEN ${PUBLIC} THEN 1 END) AS published FROM feedback`,
      )
      .bind(today)
      .first(),
    db
      .prepare(
        "SELECT COUNT(*) AS total, COUNT(CASE WHEN day = ?1 THEN 1 END) AS today, " +
          "COUNT(CASE WHEN read_at IS NULL AND archived_at IS NULL THEN 1 END) AS unread, " +
          "COUNT(CASE WHEN topic IN ('job', 'freelance') AND archived_at IS NULL THEN 1 END) AS hiring FROM messages",
      )
      .bind(today)
      .first(),
    db.prepare("SELECT COUNT(*) AS n FROM devices").first("n"),
  ]);
  const byLink = new Map(clicks.results.map((row) => [row.link, row]));
  return {
    day: today,
    timeZone: timeZoneOf(env),
    visits,
    clicks: trackedLinks(env).map((link) => ({ link, total: byLink.get(link)?.total ?? 0, today: byLink.get(link)?.today ?? 0 })),
    notes: { total: notes.total, today: notes.today, published: notes.published },
    messages: { total: messages.total, today: messages.today, unread: messages.unread, hiring: messages.hiring },
    push: { configured: Boolean(env.FCM_SERVICE_ACCOUNT), devices },
  };
}

/** The last [limit] local days that have any visit, click, note, message or country, newest first. */
async function dayItems(env, limit) {
  const { results } = await env.DB
    .prepare(
      "SELECT day FROM visits UNION SELECT day FROM visit_countries UNION SELECT day FROM clicks " +
        "UNION SELECT day FROM feedback WHERE day IS NOT NULL UNION SELECT day FROM messages ORDER BY day DESC LIMIT ?1",
    )
    .bind(limit)
    .all();
  return numbersFor(env, results.map((row) => row.day));
}

/**
 * Each of [days]' numbers: visits, clicks per link, notes and messages received (and still kept), and the visits'
 * countries, most first. Read in one pass over the span the days cover.
 */
async function numbersFor(env, days) {
  if (!days.length) return [];
  const db = env.DB;
  const span = [days.reduce((a, b) => (a < b ? a : b)), days.reduce((a, b) => (a > b ? a : b))];
  const rows = (sql) => db.prepare(sql).bind(...span).all().then((result) => result.results);
  const [visits, clicks, notes, messages, countries] = await Promise.all([
    rows("SELECT day, count FROM visits WHERE day BETWEEN ?1 AND ?2"),
    rows("SELECT day, link, count FROM clicks WHERE day BETWEEN ?1 AND ?2"),
    rows("SELECT day, COUNT(*) AS count FROM feedback WHERE day BETWEEN ?1 AND ?2 GROUP BY day"),
    rows("SELECT day, COUNT(*) AS count FROM messages WHERE day BETWEEN ?1 AND ?2 GROUP BY day"),
    rows("SELECT day, country, count FROM visit_countries WHERE day BETWEEN ?1 AND ?2 ORDER BY count DESC, country"),
  ]);
  // Each list grouped by day once: looking each day up in every list read a year's rows 366 times (~50 ms of CPU, where
  // the free plan allows a request 10).
  const [visitsOn, clicksOn, notesOn, messagesOn, countriesOn] = [visits, clicks, notes, messages, countries].map(byDay);
  const countOn = (rowsOn, date) => rowsOn.get(date)?.[0].count ?? 0;
  const links = trackedLinks(env);
  return days.map((date) => ({
    day: date,
    visits: countOn(visitsOn, date),
    clicks: clickCounts(links, clicksOn.get(date) ?? []),
    notes: countOn(notesOn, date),
    messages: countOn(messagesOn, date),
    countries: (countriesOn.get(date) ?? []).map((row) => ({ country: row.country, count: row.count })),
  }));
}

/** [rows] by their `day`, each day's in the order they came. */
function byDay(rows) {
  const days = new Map();
  for (const row of rows) {
    const list = days.get(row.day);
    if (list) list.push(row);
    else days.set(row.day, [row]);
  }
  return days;
}

async function adminAction(request, env, kind, id, now) {
  const body = await readBody(request);
  if (body.error) return ownerJson({ error: body.error }, body.status);
  const action = text(body.fields.action) ?? "";
  const outcome = await (kind === "feedback" ? noteAction : messageAction)(env.DB, id, action, now);
  return outcome.error ? ownerJson({ error: outcome.error }, outcome.status) : ownerJson({ ok: true });
}

/**
 * The alerts' records. Without `after`, the latest, newest first (a list to read). With it, only the newer ones,
 * oldest first: the app's backup poll shows them in the order they happened and remembers the last id.
 */
async function notifications(db, url) {
  const limit = limitOf(url, 50, 200);
  const after = url.searchParams.get("after");
  const columns = "SELECT id, created_at, kind, title, body, ref_id FROM notifications";
  if (after === null || after === "") {
    const { results } = await db.prepare(`${columns} ORDER BY id DESC LIMIT ?1`).bind(limit).all();
    return ownerJson({ items: results });
  }
  if (!/^\d+$/.test(after)) return ownerJson({ error: "`after` is the id of the last record seen." }, 400);
  const { results } = await db.prepare(`${columns} WHERE id > ?1 ORDER BY id ASC LIMIT ?2`).bind(Number(after), limit).all();
  return ownerJson({ items: results });
}

async function reportItems(db, limit) {
  const { results } = await db
    .prepare("SELECT day, visits, clicks, notes, messages, created_at FROM daily_reports ORDER BY day DESC LIMIT ?1")
    .bind(limit)
    .all();
  return results.map((row) => ({ ...row, clicks: parsedClicks(row.clicks) }));
}

function parsedClicks(stored) {
  try {
    const clicks = JSON.parse(stored);
    return clicks && typeof clicks === "object" && !Array.isArray(clicks) ? clicks : {};
  } catch {
    return {};
  }
}

/** A phone signing in for push, or signing in again: the token is kept once, and when it was last seen moves on. */
async function addDevice(request, env, now) {
  const body = await readBody(request);
  if (body.error) return ownerJson({ error: body.error }, body.status);
  const token = text(body.fields.token);
  const platform = text(body.fields.platform);
  if (!token || token.length > LIMITS.token) return ownerJson({ error: "Send the phone's FCM token." }, 400);
  if (platform === null || (platform && !/^[a-z]{1,16}$/.test(platform))) {
    return ownerJson({ error: "The platform is a short lowercase name, like android." }, 400);
  }
  const stamp = now.toISOString();
  await env.DB
    .prepare(
      "INSERT INTO devices (token, platform, created_at, last_seen_at) VALUES (?1, ?2, ?3, ?3) " +
        "ON CONFLICT (token) DO UPDATE SET platform = excluded.platform, last_seen_at = excluded.last_seen_at",
    )
    .bind(token, platform || "android", stamp)
    .run();
  return ownerJson({ ok: true });
}

/** Signing out: the phone gets no more pushes. A token that is already gone is fine. */
async function removeDevice(request, env) {
  const body = await readBody(request);
  if (body.error) return ownerJson({ error: body.error }, body.status);
  const token = text(body.fields.token);
  if (!token) return ownerJson({ error: "Send the phone's FCM token." }, 400);
  await env.DB.prepare("DELETE FROM devices WHERE token = ?1").bind(token).run();
  return ownerJson({ ok: true });
}

// ---- The daily report ----

/**
 * The cron's job at 23:59 local time: the day's numbers, kept in `daily_reports` and sent to the owner, and the
 * earlier days' visitor and sender ids dropped (forgetYesterday). Cloudflare may run a cron twice, even at once;
 * the run whose insert makes the day's row sends the alert, and any other rewrites the row with the newer numbers
 * and sends nothing more.
 */
export async function dailyReport(env, ctx, { now = new Date(), fetch } = {}) {
  const today = localDay(env, now);
  const [{ visits, clicks, notes, messages }] = await numbersFor(env, [today]);
  const values = [today, visits, JSON.stringify(clicks), notes, messages];
  // Claiming the day is one statement, so two runs cannot both see it unclaimed and both send.
  const { meta } = await env.DB
    .prepare("INSERT INTO daily_reports (day, visits, clicks, notes, messages, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6) ON CONFLICT (day) DO NOTHING")
    .bind(...values, now.toISOString())
    .run();
  const first = meta.changes === 1;
  if (!first) {
    await env.DB.prepare("UPDATE daily_reports SET visits = ?2, clicks = ?3, notes = ?4, messages = ?5 WHERE day = ?1").bind(...values).run();
  }
  const report = { day: today, visits, clicks, notes, messages };
  if (first) await notify(env, ctx, { kind: "daily", ...dailyText(report), refId: null }, { now, fetch });
  // After the report, and never its failure: the ids are dropped by the next visit or note anyway.
  await forgetYesterday(env.DB, today).catch((error) => console.error("Old ids not dropped:", error));
  return report;
}

/** "Today: 8 visits" over "LinkedIn 2 · GitHub 1 · 1 note · 1 message". */
export function dailyText({ visits, clicks, notes, messages }) {
  const links = Object.entries(clicks).map(([link, count]) => `${linkLabel(link)} ${count}`);
  return {
    title: `Today: ${counted(visits, "visit")}`,
    body: [...links, counted(notes, "note"), counted(messages, "message")].join(" · "),
  };
}

function counted(n, word) {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

// ---- Responses ----

function json(payload, status = 200, headers = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    // A visitor's words in JSON are never read as a page, whatever a browser would guess.
    headers: { "Content-Type": "application/json; charset=utf-8", "X-Content-Type-Options": "nosniff", ...headers },
  });
}

/** Every page, the inbox, its sign-in and the notices, with PAGE_HEADERS: never framed, no script but its own. */
function html(markup, status = 200, headers = {}) {
  return new Response(markup, { status, headers: { "Content-Type": "text/html; charset=utf-8", ...PAGE_HEADERS, ...headers } });
}

/** JSON for the page's script; a page for a browser that posted the plain form without it. */
function reply(form, payload, status, cors, env, thanks = NOTE_THANKS) {
  if (!form) return json(payload, status, cors);
  const ok = status < 400;
  return html(renderNotice(ok ? "Thank you" : "Not sent", ok ? thanks : payload.error, env.SITE_URL), status);
}

/** The site's own pages, named in ALLOWED_ORIGINS, may call the API from a browser; the headers say so to theirs. */
function corsHeaders(request, env) {
  const origin = request.headers.get("Origin");
  const allowed = String(env.ALLOWED_ORIGINS || "").split(",").map((entry) => entry.trim()).filter(Boolean);
  const headers = { Vary: "Origin" };
  if (origin && allowed.includes(origin)) {
    Object.assign(headers, {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Access-Control-Max-Age": "86400",
    });
  }
  return headers;
}
