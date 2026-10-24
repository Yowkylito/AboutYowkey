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
 *   GET  /notes              { visits, ratings, notes, total, more, relations }  the public notes, pinned first, then
 *                            newest first, a page at a time (?stars=1..5, ?relation=colleague|friend|special|other,
 *                            ?offset=0..100000, ?limit=1..50; the 1.1 app's ?before=<id>), and every rating counted
 *   POST /feedback           { ok }                     JSON from the page's script, or a plain HTML form post
 *   POST /messages           { ok }                     a private message to the owner: JSON, or a plain HTML form post
 *   GET  /feedback           { visits, items }          owner only: `Authorization: Bearer <ADMIN_TOKEN>`
 *   GET  /inbox              HTML                       owner only: HTTP Basic auth, any user name, password ADMIN_TOKEN
 *   POST /inbox/notes/:id    303 → /inbox               owner only, from the inbox page: hide, unhide, pin, unpin, delete
 *                                                       (and the 1.0 app's publish, unpublish)
 *   POST /inbox/messages/:id 303 → /inbox               owner only, from the inbox page: read, unread, archive, unarchive, delete
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
import { linkLabel, relationLabel, renderInbox, renderNotice, topicLabel } from "./inbox.js";
import { notify } from "./push.js";

export const LIMITS = Object.freeze({
  name: 80,
  contact: 160,
  message: 2000,
  /** Notes one visitor may leave in a day; the next gets a 429. */
  notesPerDay: 5,
  /**
   * And at most one every this many minutes (wrangler.toml's NOTE_GAP_MINUTES overrides it), so a burst from one
   * device is turned away before the day's limit is reached.
   */
  noteGapMinutes: 5,
  /** Messages one visitor may send in a day; the next gets a 429. */
  messagesPerDay: 3,
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

/** The minutes one device waits between notes: NOTE_GAP_MINUTES when it is a whole number (0 turns it off), else 5. */
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
    if (path === "/notes" && request.method === "GET") {
      // Short: a visitor who has just left a note reloads the list, and should find it there.
      return json(await publicNotes(env.DB, localDay(env, now), url), 200, { ...cors, "Cache-Control": "public, max-age=15" });
    }
    if (path === "/feedback" && request.method === "POST") return await submitFeedback(request, env, now, cors, alerts);
    if (path === "/feedback" && request.method === "GET") return await listFeedback(request, env, url, now);
    if (path === "/messages" && request.method === "POST") return await submitMessage(request, env, now, cors, alerts);
    if (path === "/inbox" && request.method === "GET") return await inbox(request, env, now);
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

async function visitCounts(db, today) {
  const row = await db
    .prepare(
      "SELECT COALESCE(SUM(count), 0) AS total, MIN(day) AS since, " +
        "COALESCE((SELECT count FROM visits WHERE day = ?1), 0) AS today FROM visits",
    )
    .bind(today)
    .first();
  return { total: row.total, today: row.today, since: row.since };
}

async function recordVisit(request, env, now) {
  const today = localDay(env, now);
  const agent = request.headers.get("User-Agent") || "";
  if (isBot(agent)) return visitCounts(env.DB, today);
  const visitor = await visitorId(request, env, today, agent);
  const inserted = await env.DB.prepare("INSERT OR IGNORE INTO visitors (day, visitor) VALUES (?1, ?2)").bind(today, visitor).run();
  if (inserted.meta.changes === 1) {
    await env.DB
      .prepare("INSERT INTO visits (day, count) VALUES (?1, 1) ON CONFLICT (day) DO UPDATE SET count = count + 1")
      .bind(today)
      .run();
    await env.DB
      .prepare("INSERT INTO visit_countries (day, country, count) VALUES (?1, ?2, 1) ON CONFLICT (day, country) DO UPDATE SET count = count + 1")
      .bind(today, countryOf(request))
      .run();
    // An id carries its day, so an earlier day's rows can never match again: nothing about a visitor outlives the day.
    await env.DB.prepare("DELETE FROM visitors WHERE day <> ?1").bind(today).run();
  }
  return visitCounts(env.DB, today);
}

function isBot(agent) {
  return !agent || BOT.test(agent);
}

/**
 * Who is visiting, for today only: a hash of the day, the address, the browser and a secret salt. The address is
 * never stored, the hash cannot be turned back into it without the salt, and tomorrow the same person gets a new one.
 */
async function visitorId(request, env, today, agent) {
  const address = request.headers.get("CF-Connecting-IP") || "unknown";
  return sha256Hex(`${today}|${address}|${agent}|${env.VISITOR_SALT || ""}`);
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
 * A click on one of the page's tracked links, counted like a visit: once per visitor per day per link, never for a
 * bot, and the day's ids deleted once a new day's click is counted. The page sends it with `navigator.sendBeacon`,
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
  const inserted = await env.DB
    .prepare("INSERT OR IGNORE INTO clickers (day, link, visitor) VALUES (?1, ?2, ?3)")
    .bind(today, link, visitor)
    .run();
  const counted = inserted.meta.changes === 1;
  if (counted) {
    await env.DB
      .prepare("INSERT INTO clicks (day, link, count) VALUES (?1, ?2, 1) ON CONFLICT (day, link) DO UPDATE SET count = count + 1")
      .bind(today, link)
      .run();
    await env.DB.prepare("DELETE FROM clickers WHERE day <> ?1").bind(today).run();
  }
  return json({ ok: true, counted }, 200, cors);
}

/** A day's clicks as `{ linkedin: 2, github: 1 }`: every tracked link, 0 when nobody clicked it, then any others. */
function clickCounts(links, rows) {
  const counts = Object.fromEntries(links.map((link) => [link, 0]));
  for (const row of rows) counts[row.link] = (counts[row.link] || 0) + row.count;
  return counts;
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
  const visitor = await visitorId(request, env, today, request.headers.get("User-Agent") || "");
  const { n, last } = await env.DB
    .prepare("SELECT COUNT(*) AS n, MAX(created_at) AS last FROM feedback WHERE visitor = ?1")
    .bind(visitor)
    .first();
  if (n >= LIMITS.notesPerDay) return reply(body.form, { error: ENOUGH_FOR_TODAY }, 429, cors, env);
  const gap = noteGapMinutes(env);
  const wait = last && gap ? Date.parse(last) + gap * 60000 - now.getTime() : 0;
  if (wait > 0) return reply(body.form, { error: oneNoteAtATime(gap, wait) }, 429, cors, env);
  const note = normalizeFeedback(fields);
  const { meta } = await env.DB
    .prepare(
      "INSERT INTO feedback (created_at, day, name, contact, rating, message, visitor, consent, relation) " +
        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
    )
    .bind(now.toISOString(), today, note.name, note.contact, note.rating, note.message, visitor, note.consent ? 1 : 0, note.relation)
    .run();
  // The owner's alert names the visitor (or "Someone"), never their contact.
  await alertOwner(env, alerts, {
    kind: "feedback",
    title: noteTitle(note),
    body: `${note.name || "Someone"}: ${preview(note.message)}`,
    refId: Number(meta.last_row_id),
  });
  return reply(body.form, { ok: true }, 201, cors, env);
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
  const text = await request.text();
  if (text.length > LIMITS.body) return { form, error: tooLarge, status: 413 };
  if (form) return { form, fields: Object.fromEntries(new URLSearchParams(text)) };
  let fields;
  try {
    fields = JSON.parse(text);
  } catch {
    return { form, error: "The body is not valid JSON.", status: 400 };
  }
  if (!fields || typeof fields !== "object" || Array.isArray(fields)) return { form, error: "Send a JSON object.", status: 400 };
  return { form, fields };
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
  const visitor = await visitorId(request, env, today, request.headers.get("User-Agent") || "");
  const { n } = await env.DB.prepare("SELECT COUNT(*) AS n FROM messages WHERE visitor = ?1").bind(visitor).first();
  if (n >= LIMITS.messagesPerDay) return answer({ error: ENOUGH_FOR_TODAY }, 429);
  const message = normalizeMessage(fields);
  const { meta } = await env.DB
    .prepare("INSERT INTO messages (created_at, day, name, contact, topic, message, visitor) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)")
    .bind(now.toISOString(), today, message.name, message.contact, message.topic, message.message, visitor)
    .run();
  // The one alert with a contact in it, for the owner's phone only: who to write back to.
  await alertOwner(env, alerts, {
    kind: "message",
    title: `New message · ${topicLabel(message.topic)}`,
    body: `${message.name || message.contact}: ${preview(message.message)}`,
    refId: Number(meta.last_row_id),
  });
  return answer({ ok: true }, 201);
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
  const entry = await authorize(bearerToken(request), env);
  if (entry !== true) return entry;
  const [visits, items] = await Promise.all([visitCounts(env.DB, localDay(env, now)), feedbackItems(env.DB, limitOf(url, 100, 500))]);
  return json({ visits, items }, 200, { "Cache-Control": "no-store" });
}

async function inbox(request, env, now) {
  const entry = await authorize(basicPassword(request), env, { basic: true });
  if (entry !== true) return entry;
  const today = localDay(env, now);
  const [visits, items, days, messages, clicks] = await Promise.all([
    visitCounts(env.DB, today),
    feedbackItems(env.DB, 200),
    env.DB.prepare("SELECT day, count FROM visits ORDER BY day DESC LIMIT 14").all().then((result) => result.results),
    messageItems(env.DB, 200),
    env.DB.prepare("SELECT link, count FROM clicks WHERE day = ?1").bind(today).all().then((result) => clickCounts(trackedLinks(env), result.results)),
  ]);
  return html(renderInbox({ visits, items, days, today, timeZone: timeZoneOf(env), messages, clicks }), 200, {
    "Cache-Control": "no-store",
    "X-Robots-Tag": "noindex",
  });
}

/**
 * One of the inbox page's buttons. The browser sends the inbox's Basic credentials with any request to this
 * origin, a form on another site included, so only a post from this origin itself is acted on.
 */
async function inboxAction(request, env, kind, id, now, url) {
  const entry = await authorize(basicPassword(request), env, { basic: true });
  if (entry !== true) return entry;
  if (!fromThisOrigin(request, url)) {
    const things = kind === "notes" ? "Notes" : "Messages";
    return html(renderNotice("Refused", `${things} are acted on from the inbox page only.`, "/inbox"), 403);
  }
  const { action = "" } = Object.fromEntries(new URLSearchParams(await request.text()));
  const outcome = await (kind === "notes" ? noteAction : messageAction)(env.DB, id, action, now);
  if (outcome.error) return html(renderNotice(outcome.title, outcome.error, "/inbox"), outcome.status);
  return new Response(null, { status: 303, headers: { Location: "/inbox" } });
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
      statement = db.prepare("DELETE FROM feedback WHERE id = ?1").bind(id);
      break;
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
    delete: () => db.prepare("DELETE FROM messages WHERE id = ?1").bind(id),
  };
  if (!Object.hasOwn(statements, action)) return refusal(400, "Refused", `There is no "${action}" to do with a message.`);
  await statements[action]().run();
  return { status: 200 };
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
 * owner included: an inbox is never open by default.
 */
async function authorize(secret, env, { basic = false } = {}) {
  if (!env.ADMIN_TOKEN) {
    return json({ error: "ADMIN_TOKEN is not set on the worker: run `npx wrangler secret put ADMIN_TOKEN`." }, 503);
  }
  if (await sameSecret(secret, env.ADMIN_TOKEN)) return true;
  if (!basic) return json({ error: "Unauthorized." }, 401);
  return html(
    renderNotice("Sign in", "The inbox is the site owner's. Any user name; the password is ADMIN_TOKEN.", null),
    401,
    { "WWW-Authenticate": 'Basic realm="Portfolio inbox", charset="UTF-8"' },
  );
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
//   POST   /admin/devices                 { token, platform }  a phone signs in for push
//   DELETE /admin/devices                 { token }            and out
//
// The app is native, so these need no CORS. Every answer, refusals included, is `Cache-Control: no-store`.

/** The token is checked before the route, so a wrong one learns nothing about which routes exist. */
async function admin(request, env, url, route, now, alerts) {
  const entry = await authorize(bearerToken(request), env);
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
  const countOn = (list, date) => list.find((row) => row.day === date)?.count ?? 0;
  const links = trackedLinks(env);
  return days.map((date) => ({
    day: date,
    visits: countOn(visits, date),
    clicks: clickCounts(links, clicks.filter((row) => row.day === date)),
    notes: countOn(notes, date),
    messages: countOn(messages, date),
    countries: countries.filter((row) => row.day === date).map((row) => ({ country: row.country, count: row.count })),
  }));
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
 * The cron's job at 23:59 local time: the day's numbers, kept in `daily_reports` and sent to the owner. Cloudflare
 * may run a cron twice, even at once; the run whose insert makes the day's row sends the alert, and any other
 * rewrites the row with the newer numbers and sends nothing more.
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
    headers: { "Content-Type": "application/json; charset=utf-8", ...headers },
  });
}

function html(markup, status = 200, headers = {}) {
  return new Response(markup, { status, headers: { "Content-Type": "text/html; charset=utf-8", ...headers } });
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
