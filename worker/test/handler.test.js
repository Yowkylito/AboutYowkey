import { beforeEach, mock, test } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dailyReport, day, handleRequest, LIMITS } from "../src/handler.js";
import { forgetAccessToken } from "../src/push.js";
import worker from "../src/index.js";
import { openDatabase } from "./d1.js";

const MIGRATIONS = fileURLToPath(new URL("../migrations", import.meta.url));
const SITE = "https://yowkylito.github.io";
const CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const DAY_ONE = "2026-10-24T10:00:00.000Z";
const DAY_TWO = "2026-10-25T09:00:00.000Z";
/** 00:30 on 24 October in Manila, still the 23rd in UTC. */
const MANILA_MIDNIGHT = "2026-10-23T16:30:00.000Z";
/** When the cron runs: 23:59 in Manila. */
const REPORT_TIME = "2026-10-24T15:59:00.000Z";

let env;
/** What the worker hands to `ctx.waitUntil`: the pushes, which the tests await with settle(). */
let pending;
/** The only way out to the network in the tests; it fails the test unless a test puts a stub in its place. */
let network;
beforeEach(() => {
  env = {
    DB: openDatabase(MIGRATIONS),
    ALLOWED_ORIGINS: `${SITE}, http://localhost:8080`,
    SITE_URL: `${SITE}/AboutYowkey/`,
    ADMIN_TOKEN: "owner-secret",
    VISITOR_SALT: "salt",
    TIME_ZONE: "Asia/Manila",
    TRACKED_LINKS: "linkedin,github",
  };
  pending = [];
  network = async (url) => assert.fail(`a test reached the network: ${url}`);
  forgetAccessToken();
});

const ctx = { waitUntil: (promise) => pending.push(promise) };
const settle = () => Promise.all(pending.splice(0));
const fetchStub = (...args) => network(...args);

/** One request as the page would make it: from the site, a real browser, one address, at a given moment. */
function call(path, { method = "GET", headers = {}, body, address = "203.0.113.7", agent = CHROME, origin = SITE, now = DAY_ONE, cf } = {}) {
  const h = new Headers(headers);
  if (agent !== null) h.set("User-Agent", agent);
  if (address) h.set("CF-Connecting-IP", address);
  if (origin) h.set("Origin", origin);
  const request = new Request(`https://api.example${path}`, { method, headers: h, body });
  if (cf) Object.defineProperty(request, "cf", { value: cf }); // what Cloudflare adds to a real request
  return handleRequest(request, env, ctx, { now: new Date(now), fetch: fetchStub });
}

const post = (path, payload, options = {}) =>
  call(path, { ...options, method: "POST", headers: { "Content-Type": "application/json", ...options.headers }, body: JSON.stringify(payload) });

const owner = (path, options = {}) => call(path, { ...options, headers: { Authorization: `Bearer ${env.ADMIN_TOKEN}`, ...options.headers } });

const basic = (password, user = "me") => ({ Authorization: `Basic ${btoa(`${user}:${password}`)}` });

/** One of the inbox page's buttons, pressed by the owner on the inbox page itself. */
const act = (id, action, options = {}) =>
  call(`/inbox/notes/${id}`, {
    ...options,
    method: "POST",
    origin: "https://api.example",
    headers: { ...basic(env.ADMIN_TOKEN), "Content-Type": "application/x-www-form-urlencoded", "Sec-Fetch-Site": "same-origin", ...options.headers },
    body: `action=${action}`,
  });

const publicNotes = async (options = {}) => (await call("/notes", options)).json();

/** Rows as plain objects: node:sqlite's have no prototype, which strict deep equality would reject. */
const query = (sql) => env.DB.raw.prepare(sql).all().map((row) => ({ ...row }));
const feedbackRows = () => query("SELECT * FROM feedback ORDER BY id");

// ---- Visits ----

test("a visit counts once per visitor per day, and a day's first visit starts the count", async () => {
  assert.deepEqual(await (await call("/visits")).json(), { total: 0, today: 0, since: null });

  assert.deepEqual(await (await call("/visits", { method: "POST" })).json(), { total: 1, today: 1, since: "2026-10-24" });
  assert.deepEqual(await (await call("/visits", { method: "POST" })).json(), { total: 1, today: 1, since: "2026-10-24" }, "a reload is not a visit");
  assert.equal((await (await call("/visits", { method: "POST", address: "198.51.100.2" })).json()).total, 2, "another address is");
  assert.equal((await (await call("/visits", { method: "POST", agent: CHROME + " Edg/130" })).json()).total, 3, "so is another browser");

  const nextDay = await (await call("/visits", { method: "POST", now: DAY_TWO })).json();
  assert.deepEqual(nextDay, { total: 4, today: 1, since: "2026-10-24" }, "the same person is a new visit the next day");
  assert.equal((await (await call("/visits", { now: DAY_TWO })).json()).total, 4, "GET reads without counting");
});

test("crawlers, previews and Lighthouse read the count but are not counted", async () => {
  for (const agent of [
    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
    "Mozilla/5.0 (Linux; Android 11; moto g power (2022)) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36 Chrome-Lighthouse",
    "facebookexternalhit/1.1",
    "curl/8.4.0",
    "",
  ]) {
    const response = await call("/visits", { method: "POST", agent });
    assert.equal(response.status, 200, agent);
    assert.equal((await response.json()).total, 0, `counted "${agent}"`);
  }
  const noAgent = await call("/visits", { method: "POST", agent: null });
  assert.equal((await noAgent.json()).total, 0, "no user agent at all");
});

test("yesterday's visitor ids are dropped when a new day is counted, and the address is never stored", async () => {
  await call("/visits", { method: "POST" });
  await call("/visits", { method: "POST", address: "198.51.100.2" });
  assert.equal(env.DB.raw.prepare("SELECT COUNT(*) AS n FROM visitors").get().n, 2);
  for (const { visitor } of query("SELECT visitor FROM visitors")) {
    assert.match(visitor, /^[0-9a-f]{64}$/);
    assert.ok(!visitor.includes("203.0.113.7"));
  }
  await call("/visits", { method: "POST", now: DAY_TWO });
  assert.deepEqual(query("SELECT day, COUNT(*) AS n FROM visitors GROUP BY day"), [{ day: "2026-10-25", n: 1 }]);
  assert.deepEqual(query("SELECT day, count FROM visits ORDER BY day"), [
    { day: "2026-10-24", count: 2 },
    { day: "2026-10-25", count: 1 },
  ]);
});

// ---- Browsers and origins ----

test("the site's pages get CORS headers, a preflight a 204, and another page is turned away", async () => {
  const fromSite = await call("/visits", { method: "POST" });
  assert.equal(fromSite.headers.get("Access-Control-Allow-Origin"), SITE);
  assert.equal(fromSite.headers.get("Vary"), "Origin");

  const local = await call("/visits", { method: "POST", origin: "http://localhost:8080" });
  assert.equal(local.headers.get("Access-Control-Allow-Origin"), "http://localhost:8080", "origins are trimmed");

  const preflight = await call("/feedback", { method: "OPTIONS", headers: { "Access-Control-Request-Method": "POST" } });
  assert.equal(preflight.status, 204);
  assert.equal(preflight.headers.get("Access-Control-Allow-Headers"), "Content-Type");

  const elsewhere = await post("/feedback", { message: "hi" }, { origin: "https://evil.example" });
  assert.equal(elsewhere.status, 403);
  assert.equal(elsewhere.headers.get("Access-Control-Allow-Origin"), null);
  assert.equal(feedbackRows().length, 0);

  const read = await call("/visits", { origin: "https://evil.example" });
  assert.equal(read.status, 200, "a GET is harmless; the browser withholds the reply anyway");
  assert.equal(read.headers.get("Access-Control-Allow-Origin"), null);

  const tool = await call("/visits", { method: "POST", origin: null });
  assert.equal(tool.status, 200, "no Origin at all (a tool, not a page) is fine");
});

// ---- Feedback ----

test("a note is stored trimmed, the optional fields null when left out, and never the visitor's address", async () => {
  const response = await post("/feedback", { name: "  Ada ", message: "  Nice site.\nHire me?  ", rating: "5", contact: "" });
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { ok: true });

  const [row] = feedbackRows();
  assert.equal(row.created_at, DAY_ONE);
  assert.equal(row.name, "Ada");
  assert.equal(row.contact, null);
  assert.equal(row.rating, 5);
  assert.equal(row.message, "Nice site.\nHire me?");
  assert.match(row.visitor, /^[0-9a-f]{64}$/);

  await post("/feedback", { message: "Second" });
  const listed = await (await owner("/feedback")).json();
  assert.deepEqual(listed.visits, { total: 0, today: 0, since: null });
  assert.deepEqual(listed.items.map((item) => item.message), ["Second", "Nice site.\nHire me?"], "newest first");
  assert.deepEqual(Object.keys(listed.items[0]).sort(), ["consent", "contact", "created_at", "hidden_at", "id", "message", "name", "pinned_at", "published_at", "rating", "relation"]);
  assert.deepEqual(listed.items[1], {
    id: 1, created_at: DAY_ONE, name: "Ada", contact: null, rating: 5, message: "Nice site.\nHire me?", consent: 0, published_at: null, hidden_at: null,
    relation: null, pinned_at: null,
  });
});

test("the consent box is read however it arrives, and defaults to no", async () => {
  const cases = [[true, 1], ["1", 1], ["on", 1], ["YES", 1], [false, 0], ["0", 0], ["", 0], [undefined, 0], ["maybe", 0]];
  for (const [consent, stored] of cases) {
    const payload = { message: `consent ${JSON.stringify(consent)}` };
    if (consent !== undefined) payload.consent = consent;
    assert.equal((await post("/feedback", payload, { address: `203.0.113.${stored + 10 + cases.indexOf([consent, stored])}` })).status, 201);
  }
  assert.deepEqual(feedbackRows().map((row) => row.consent), cases.map(([, stored]) => stored));
  const form = await call("/feedback", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "message=ticked&consent=on", address: "198.51.100.9",
  });
  assert.equal(form.status, 201);
  assert.equal(feedbackRows().at(-1).consent, 1, "a ticked checkbox posts 'on'");
});

test("a note is public as it arrives; the rating counts every star not hidden, and says how many notes were private", async () => {
  const empty = await call("/notes");
  assert.equal(empty.headers.get("Cache-Control"), "public, max-age=15");
  assert.equal(empty.headers.get("Access-Control-Allow-Origin"), SITE);
  assert.deepEqual(await empty.json(), {
    visits: { total: 0, today: 0, since: null },
    ratings: { count: 0, average: null, stars: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }, private: 0, privateStars: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } },
    notes: [],
    total: 0,
    more: false,
    relations: { colleague: 0, friend: 0, special: 0, other: 0 },
  });

  await post("/feedback", { name: "Ada", contact: "ada@example.com", rating: 5, message: "Great", consent: true });
  await post("/feedback", { rating: 4, message: "Good", consent: "1" }, { address: "198.51.100.2", now: DAY_TWO });
  await post("/feedback", { name: "Quiet", rating: 1, message: "Before notes were public", consent: false }, { address: "198.51.100.3" });
  await post("/feedback", { message: "No stars", consent: true }, { address: "198.51.100.4", now: DAY_TWO });
  const shown = await publicNotes({ now: DAY_TWO });
  assert.deepEqual(shown.ratings, {
    count: 3, average: 3.3, stars: { 1: 1, 2: 0, 3: 0, 4: 1, 5: 1 }, private: 1, privateStars: { 1: 1, 2: 0, 3: 0, 4: 0, 5: 0 },
  },
    "a note without stars does not rate; a private note's stars do, and are counted as private");
  assert.deepEqual(shown.notes, [
    { id: 4, name: null, rating: null, message: "No stars", date: "2026-10-25", relation: null, pinned: false },
    { id: 2, name: null, rating: 4, message: "Good", date: "2026-10-25", relation: null, pinned: false },
    { id: 1, name: "Ada", rating: 5, message: "Great", date: "2026-10-24", relation: null, pinned: false },
  ], "no owner's click needed; the private note's words never show");
  assert.equal(shown.total, 3);
  assert.equal(shown.more, false);
  assert.ok(!JSON.stringify(shown).includes("ada@example.com"), "the contact is never public");

  const refused = await act(3, "publish");
  assert.equal(refused.status, 409, "a note sent without the tick stays private");
  assert.match(await refused.text(), /did not agree/);

  assert.equal((await act(1, "hide")).status, 303);
  const afterHide = await publicNotes();
  assert.deepEqual(afterHide.ratings, {
    count: 2, average: 2.5, stars: { 1: 1, 2: 0, 3: 0, 4: 1, 5: 0 }, private: 1, privateStars: { 1: 1, 2: 0, 3: 0, 4: 0, 5: 0 },
  }, "hidden: shown nowhere, counted nowhere");
  assert.deepEqual(afterHide.notes.map((note) => note.id), [4, 2]);
  assert.equal((await act(1, "unhide")).status, 303);
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [4, 2, 1]);
  assert.equal((await act(2, "unpublish")).status, 303, "the 1.0 app's Unpublish hides");
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [4, 1]);
  assert.equal((await act(2, "publish")).status, 303, "and its Publish unhides");
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [4, 2, 1]);

  assert.equal((await act(3, "delete")).status, 303);
  assert.deepEqual(feedbackRows().map((row) => row.id), [1, 2, 4]);
  assert.equal((await act(1, "explode")).status, 400);
  assert.equal((await act(99, "hide")).status, 404);
});

test("the public notes come a page at a time, all of them or one star's", async () => {
  for (let i = 1; i <= 15; i++) {
    await post("/feedback", { rating: (i % 5) + 1, message: `Note ${i}`, consent: true }, { address: `198.51.100.${i}` });
  }
  const first = await publicNotes();
  assert.equal(first.notes.length, LIMITS.notesPage);
  assert.deepEqual(first.notes.slice(0, 2).map((note) => note.id), [15, 14], "newest first");
  assert.equal(first.total, 15);
  assert.equal(first.more, true);
  const rest = await (await call(`/notes?before=${first.notes.at(-1).id}`)).json();
  assert.deepEqual(rest.notes.map((note) => note.id), [3, 2, 1]);
  assert.equal(rest.more, false);
  assert.deepEqual(rest.ratings, first.ratings, "the rating is the same on every page");

  const fives = await (await call("/notes?stars=5")).json();
  assert.deepEqual(fives.notes.map((note) => note.rating), [5, 5, 5]);
  assert.equal(fives.total, 3);
  assert.equal(fives.ratings.count, 15, "filtering the list does not filter the rating");
  const two = await (await call("/notes?stars=5&limit=2")).json();
  assert.deepEqual([two.notes.length, two.more], [2, true]);
  const ignored = await (await call("/notes?stars=9&before=abc&limit=0")).json();
  assert.deepEqual([ignored.notes.length, ignored.total], [LIMITS.notesPage, 15], "a parameter out of range is ignored");
});

test("the inbox's buttons need the password and must be pressed on the inbox page itself", async () => {
  await post("/feedback", { message: "x", consent: true });
  const form = { "Content-Type": "application/x-www-form-urlencoded" };
  const noAuth = await call("/inbox/notes/1", { method: "POST", headers: form, body: "action=publish", origin: "https://api.example" });
  assert.equal(noAuth.status, 401);
  const forged = await call("/inbox/notes/1", {
    method: "POST", headers: { ...basic(env.ADMIN_TOKEN), ...form, "Sec-Fetch-Site": "cross-site" }, body: "action=publish", origin: "https://evil.example",
  });
  assert.equal(forged.status, 403, "a form on another site, with the browser's saved credentials");
  const forgedOldBrowser = await call("/inbox/notes/1", {
    method: "POST", headers: { ...basic(env.ADMIN_TOKEN), ...form }, body: "action=publish", origin: "https://evil.example",
  });
  assert.equal(forgedOldBrowser.status, 403, "no Sec-Fetch-Site: the Origin decides");
  assert.equal(feedbackRows()[0].published_at, null);

  const pressed = await act(1, "publish");
  assert.equal(pressed.status, 303);
  assert.equal(pressed.headers.get("Location"), "/inbox");
  assert.equal(feedbackRows()[0].published_at, DAY_ONE);
  const oldBrowser = await call("/inbox/notes/1", {
    method: "POST", headers: { ...basic(env.ADMIN_TOKEN), ...form }, body: "action=hide", origin: "https://api.example",
  });
  assert.equal(oldBrowser.status, 303, "no Sec-Fetch-Site, but our own Origin");
});

test("a note is checked before it is kept, and the first problem is the answer", async () => {
  const cases = [
    [{ message: "  " }, 400, "Write a message first."],
    [{ message: "x".repeat(LIMITS.message + 1) }, 400, `Keep the message under ${LIMITS.message} characters.`],
    [{ message: "ok", name: "n".repeat(LIMITS.name + 1) }, 400, `Keep the name under ${LIMITS.name} characters.`],
    [{ message: "ok", contact: "c".repeat(LIMITS.contact + 1) }, 400, `Keep the email under ${LIMITS.contact} characters.`],
    [{ message: "ok", rating: 7 }, 400, "The rating is 1 to 5."],
    [{ message: "ok", rating: "3.5" }, 400, "The rating is 1 to 5."],
    [{ message: ["not", "text"] }, 400, "Each field is text."],
    [{ message: "ok", name: { nested: true } }, 400, "Each field is text."],
    [["a list"], 400, "Send a JSON object."],
  ];
  for (const [payload, status, error] of cases) {
    const response = await post("/feedback", payload);
    assert.equal(response.status, status, JSON.stringify(payload));
    assert.equal((await response.json()).error, error, JSON.stringify(payload));
  }
  const broken = await call("/feedback", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{not json" });
  assert.equal(broken.status, 400);
  const wrongType = await call("/feedback", { method: "POST", headers: { "Content-Type": "text/plain" }, body: "hello" });
  assert.equal(wrongType.status, 415);
  const huge = await post("/feedback", { message: "x".repeat(LIMITS.body) });
  assert.equal(huge.status, 413);
  assert.equal(feedbackRows().length, 0, "nothing of the above was kept");

  const numeric = await post("/feedback", { message: 42, rating: 4 });
  assert.equal(numeric.status, 201, "numbers are spelled out, not refused");
  assert.equal(feedbackRows()[0].message, "42");
});

test("the trap field keeps bots out without telling them", async () => {
  const response = await post("/feedback", { message: "Buy now", website: "https://spam.example" });
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(feedbackRows().length, 0);
});

test(`a visitor may leave ${LIMITS.notesPerDay} notes a day`, async () => {
  for (let i = 1; i <= LIMITS.notesPerDay; i++) assert.equal((await post("/feedback", { message: `note ${i}` })).status, 201);
  const oneMore = await post("/feedback", { message: "one more" });
  assert.equal(oneMore.status, 429);
  assert.match((await oneMore.json()).error, /tomorrow/);
  assert.equal((await post("/feedback", { message: "someone else" }, { address: "198.51.100.2" })).status, 201);
  assert.equal((await post("/feedback", { message: "next day" }, { now: DAY_TWO })).status, 201);
  assert.equal(feedbackRows().length, LIMITS.notesPerDay + 2);
});

test("a plain form post gets a page back, with the way home", async () => {
  const form = (body, options = {}) =>
    call("/feedback", { ...options, method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body });
  const sent = await form("name=Ada&contact=ada%40example.com&rating=4&message=Hello+there&website=");
  assert.equal(sent.status, 201);
  assert.match(sent.headers.get("Content-Type"), /^text\/html/);
  const page = await sent.text();
  assert.match(page, /Thank you/);
  assert.match(page, new RegExp(`href="${env.SITE_URL}"`));
  assert.deepEqual(feedbackRows().map(({ name, contact, rating, message }) => ({ name, contact, rating, message })), [
    { name: "Ada", contact: "ada@example.com", rating: 4, message: "Hello there" },
  ]);

  const empty = await form("message=");
  assert.equal(empty.status, 400);
  assert.match(await empty.text(), /Write a message first\./);
  const trapped = await form("message=spam&website=x");
  assert.equal(trapped.status, 201);
  assert.equal(feedbackRows().length, 1);
});

// ---- The owner ----

test("the owner's routes need the token, and are closed when none is set", async () => {
  assert.equal((await call("/feedback")).status, 401);
  assert.equal((await call("/feedback", { headers: { Authorization: "Bearer wrong" } })).status, 401);
  assert.equal((await call("/feedback", { headers: { Authorization: `Bearer ${env.ADMIN_TOKEN}x` } })).status, 401, "a longer token with the right prefix");
  assert.equal((await owner("/feedback")).status, 200);
  assert.equal((await owner("/feedback", { origin: null })).status, 200, "the owner's tools send no Origin");

  for (let i = 0; i < 3; i++) await post("/feedback", { message: `note ${i}` });
  assert.equal((await (await owner("/feedback?limit=2")).json()).items.length, 2);

  env.ADMIN_TOKEN = "";
  assert.equal((await call("/feedback", { headers: { Authorization: "Bearer " } })).status, 503);
  assert.equal((await call("/inbox", { headers: basic("") })).status, 503, "an empty password never matches an unset token");
});

test("the inbox asks for the password, then shows the notes and the visits, escaped", async () => {
  const locked = await call("/inbox");
  assert.equal(locked.status, 401);
  assert.match(locked.headers.get("WWW-Authenticate"), /^Basic realm=/);
  assert.equal((await call("/inbox", { headers: basic("wrong") })).status, 401);
  assert.equal((await call("/inbox", { headers: { Authorization: "Basic not-base64!" } })).status, 401);

  await call("/visits", { method: "POST" });
  await call("/visits", { method: "POST", now: DAY_TWO, address: "198.51.100.2" });
  await post("/feedback", { name: "<b>Ada</b>", contact: "ada@example.com", rating: 3, message: "Great <script>alert(1)</script>", consent: true });
  await post("/feedback", { contact: "linkedin.com/in/ada", message: "No rating" });
  await post("/feedback", { message: "Taken down", consent: true }, { address: "198.51.100.5" });
  await act(3, "hide");

  const open = await call("/inbox", { headers: basic(env.ADMIN_TOKEN, "whoever"), now: DAY_TWO });
  assert.equal(open.status, 200);
  assert.equal(open.headers.get("X-Robots-Tag"), "noindex");
  assert.equal(open.headers.get("Cache-Control"), "no-store");
  const page = await open.text();
  assert.match(page, /2 visits since 2026-10-24 · 1 today \(2026-10-25, Asia\/Manila\) · 3 notes, 1 on the site/);
  assert.match(page, /<strong>On the site<\/strong>/, "a note sent as public is up at once");
  assert.match(page, /<span class="dim">Private<\/span>/, "the note without a tick is private");
  assert.match(page, /Hidden<br>/);
  assert.match(page, /<form method="post" action="\/inbox\/notes\/1"><button name="action" value="hide">/);
  assert.match(page, /<form method="post" action="\/inbox\/notes\/3"><button name="action" value="unhide">/);
  assert.doesNotMatch(page, /value="publish"/, "nothing waits for a Publish click");
  assert.match(page, /&lt;b&gt;Ada&lt;\/b&gt;/);
  assert.match(page, /Great &lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.doesNotMatch(page, /<script>alert/);
  assert.match(page, /href="mailto:ada@example.com"/);
  assert.match(page, /<br>linkedin\.com\/in\/ada</, "a contact that is not an address is text, not a link");
  assert.match(page, /★★★<span class="dim">★★<\/span>/);
  assert.match(page, /<span>2026-10-25<\/span><strong>1<\/strong>/);
  assert.match(page, /<meta name="robots" content="noindex, nofollow">/);
});

// ---- Edges ----

test("health, unknown routes and a broken database", async () => {
  assert.deepEqual(await (await call("/")).json(), { ok: true, service: "aboutyowkey-api" });
  assert.equal((await call("/nope")).status, 404);
  assert.equal((await call("/visits/", { method: "POST" })).status, 200, "a trailing slash is the same route");
  assert.equal((await call("/visits", { method: "DELETE" })).status, 404);

  const quiet = mock.method(console, "error", () => {});
  env.DB = { prepare() { throw new Error("D1_ERROR: no such table: visits"); } };
  const broken = await call("/visits", { method: "POST" });
  assert.equal(broken.status, 500);
  assert.deepEqual(await broken.json(), { error: "Something broke on the server." });
  assert.equal(quiet.mock.callCount(), 1, "the cause is logged for `wrangler tail`");
  quiet.mock.restore();
});

// ---- The owner's day ----

test("days are the owner's local dates: 00:30 in Manila is already the next day", async () => {
  assert.equal(day(new Date(MANILA_MIDNIGHT)), "2026-10-24", "Asia/Manila is the default");
  assert.equal(day(new Date(MANILA_MIDNIGHT), "UTC"), "2026-10-23");
  assert.equal(day(new Date(REPORT_TIME), "Asia/Manila"), "2026-10-24", "the cron's 15:59 UTC is 23:59 the same day");

  await call("/visits", { method: "POST", now: MANILA_MIDNIGHT });
  await post("/clicks", { link: "github" }, { now: MANILA_MIDNIGHT });
  await post("/feedback", { message: "Late night" }, { now: MANILA_MIDNIGHT });
  await post("/messages", { contact: "ada@example.com", message: "Hello" }, { now: MANILA_MIDNIGHT });
  assert.deepEqual(query("SELECT day FROM visits"), [{ day: "2026-10-24" }]);
  assert.deepEqual(query("SELECT day FROM clicks"), [{ day: "2026-10-24" }]);
  assert.deepEqual(query("SELECT day, created_at FROM feedback"), [{ day: "2026-10-24", created_at: MANILA_MIDNIGHT }], "created_at stays UTC");
  assert.deepEqual(query("SELECT day FROM messages"), [{ day: "2026-10-24" }]);
  assert.equal((await (await call("/visits", { now: MANILA_MIDNIGHT })).json()).today, 1);

  env.TIME_ZONE = "UTC";
  await call("/visits", { method: "POST", now: MANILA_MIDNIGHT, address: "198.51.100.2" });
  assert.deepEqual(query("SELECT day, count FROM visits ORDER BY day"), [
    { day: "2026-10-23", count: 1 },
    { day: "2026-10-24", count: 1 },
  ], "TIME_ZONE decides");
});

test("the notes kept before the local day existed get theirs from created_at, in Manila time", () => {
  const db = new DatabaseSync(":memory:");
  const file = (name) => readFileSync(new URL(`../migrations/${name}`, import.meta.url), "utf8");
  db.exec(file("0001_init.sql"));
  db.exec(file("0002_public_notes.sql"));
  const insert = db.prepare("INSERT INTO feedback (created_at, message, visitor) VALUES (?1, 'old', 'v')");
  insert.run(MANILA_MIDNIGHT);
  insert.run("2026-10-23T15:59:59.000Z");
  db.exec(file("0003_clicks_messages_push.sql"));
  assert.deepEqual(db.prepare("SELECT day FROM feedback ORDER BY id").all().map((row) => row.day), ["2026-10-24", "2026-10-23"]);
});

// ---- Countries ----

test("a counted visit adds one to its country for the day, from Cloudflare's guess, and nothing else", async () => {
  await call("/visits", { method: "POST", cf: { country: "PH" }, headers: { "CF-IPCountry": "US" } });
  await call("/visits", { method: "POST", cf: { country: "PH" } }); // a reload
  await call("/visits", { method: "POST", address: "198.51.100.2", headers: { "CF-IPCountry": "us" } });
  await call("/visits", { method: "POST", address: "198.51.100.3" });
  await call("/visits", { method: "POST", address: "198.51.100.4", headers: { "CF-IPCountry": "<script>" } });
  await call("/visits", { method: "POST", address: "198.51.100.5", cf: { country: "PH" }, agent: "curl/8.4.0" });
  await call("/visits", { method: "POST", address: "198.51.100.6", cf: { country: "PH" }, now: DAY_TWO });
  assert.deepEqual(query("SELECT day, country, count FROM visit_countries ORDER BY day, country"), [
    { day: "2026-10-24", country: "PH", count: 1 },
    { day: "2026-10-24", country: "US", count: 1 },
    { day: "2026-10-24", country: "XX", count: 2 },
    { day: "2026-10-25", country: "PH", count: 1 },
  ]);
});

// ---- Link clicks ----

const click = (link, options = {}) => post("/clicks", { link }, options);

test("a click counts once per visitor per day per link, and again the next day", async () => {
  const first = await click("linkedin");
  assert.equal(first.status, 200);
  assert.equal(first.headers.get("Access-Control-Allow-Origin"), SITE);
  assert.deepEqual(await first.json(), { ok: true, counted: true });
  assert.deepEqual(await (await click("linkedin")).json(), { ok: true, counted: false }, "a second click the same day");
  assert.equal((await (await click("github")).json()).counted, true, "another link is its own count");
  assert.equal((await (await click("linkedin", { address: "198.51.100.2" })).json()).counted, true, "another visitor");
  assert.equal((await (await click("LinkedIn", { now: DAY_TWO })).json()).counted, true, "the next day, the same person counts again");

  assert.deepEqual(query("SELECT day, link, count FROM clicks ORDER BY day, link"), [
    { day: "2026-10-24", link: "github", count: 1 },
    { day: "2026-10-24", link: "linkedin", count: 2 },
    { day: "2026-10-25", link: "linkedin", count: 1 },
  ]);
  const ids = query("SELECT day, visitor FROM clickers");
  assert.deepEqual(ids.map((row) => row.day), ["2026-10-25"], "yesterday's ids are deleted with the first click of a new day");
  assert.match(ids[0].visitor, /^[0-9a-f]{64}$/);
});

test("sendBeacon's text/plain counts; bots and unknown links do not", async () => {
  const beacon = await call("/clicks", { method: "POST", headers: { "Content-Type": "text/plain;charset=UTF-8" }, body: '{"link":"github"}' });
  assert.equal(beacon.status, 200);
  assert.deepEqual(await beacon.json(), { ok: true, counted: true });

  for (const agent of ["Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "curl/8.4.0", "", null]) {
    const bot = await click("linkedin", { agent, address: "198.51.100.9" });
    assert.equal(bot.status, 200, String(agent));
    assert.deepEqual(await bot.json(), { ok: true, counted: false }, `counted "${agent}"`);
  }

  for (const payload of [{ link: "twitter" }, { link: "" }, {}, { link: 5 }, { link: ["github"] }]) {
    const refused = await post("/clicks", payload);
    assert.equal(refused.status, 400, JSON.stringify(payload));
    assert.equal((await refused.json()).error, "That is not a link the page counts.");
  }
  env.TRACKED_LINKS = "github";
  assert.equal((await click("linkedin", { address: "198.51.100.2" })).status, 400, "TRACKED_LINKS is the list");

  const plainBroken = await call("/clicks", { method: "POST", headers: { "Content-Type": "text/plain" }, body: "linkedin" });
  assert.equal(plainBroken.status, 400);
  const form = await call("/clicks", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "link=github" });
  assert.equal(form.status, 415);
  assert.equal((await click("github", { origin: "https://evil.example", address: "198.51.100.3" })).status, 403);
  assert.equal((await call("/feedback", { method: "POST", headers: { "Content-Type": "text/plain" }, body: "{}" })).status, 415, "only clicks read text/plain");

  assert.deepEqual(query("SELECT link, count FROM clicks"), [{ link: "github", count: 1 }], "only the beacon counted");
});

// ---- Messages ----

const messageRows = () => query("SELECT * FROM messages ORDER BY id");
const notificationRows = () => query("SELECT id, kind, title, body, ref_id FROM notifications ORDER BY id");

test("a message is kept trimmed, with its topic and local day, and becomes the owner's alert", async () => {
  const sent = await post("/messages", { name: " Ada ", contact: " ada@example.com ", topic: "Job", message: "  Are you free in November?  " });
  assert.equal(sent.status, 201);
  assert.equal(sent.headers.get("Access-Control-Allow-Origin"), SITE);
  assert.deepEqual(await sent.json(), { ok: true });
  await post("/messages", { contact: "linkedin.com/in/bob", message: "x".repeat(LIMITS.message) }, { address: "198.51.100.2" });

  const [first, second] = messageRows();
  assert.deepEqual(
    { ...first, visitor: undefined },
    {
      id: 1, created_at: DAY_ONE, day: "2026-10-24", name: "Ada", contact: "ada@example.com", topic: "job",
      message: "Are you free in November?", visitor: undefined, read_at: null, archived_at: null,
    },
  );
  assert.match(first.visitor, /^[0-9a-f]{64}$/);
  assert.equal(second.topic, "other", "no topic is other");
  assert.equal(second.name, null);

  assert.deepEqual(notificationRows(), [
    { id: 1, kind: "message", title: "New message · Job", body: "Ada: Are you free in November?", ref_id: 1 },
    { id: 2, kind: "message", title: "New message · Other", body: `linkedin.com/in/bob: ${"x".repeat(LIMITS.preview)}`, ref_id: 2 },
  ], "no name: the contact says who it is from; the message is cut at 120 characters");
  assert.deepEqual(pending, [], "no push without FCM_SERVICE_ACCOUNT");
});

test("a message is checked before it is kept, in the contract's sentences", async () => {
  const ok = { contact: "ada@example.com", message: "Hello" };
  const cases = [
    [{ contact: "ada@example.com", message: "  " }, "Write a message first."],
    [{ message: "Hello" }, "Leave an email or another way to reach you."],
    [{ ...ok, contact: "   " }, "Leave an email or another way to reach you."],
    [{ ...ok, message: "x".repeat(LIMITS.message + 1) }, "Keep the message under 2000 characters."],
    [{ ...ok, name: "n".repeat(LIMITS.name + 1) }, "Keep the name under 80 characters."],
    [{ ...ok, contact: "c".repeat(LIMITS.contact + 1) }, "Keep the email under 160 characters."],
    [{ ...ok, topic: "spam" }, "Pick one of the topics."],
    [{ ...ok, message: ["not", "text"] }, "Each field is text."],
  ];
  for (const [payload, error] of cases) {
    const response = await post("/messages", payload);
    assert.equal(response.status, 400, JSON.stringify(payload));
    const answer = await response.json();
    assert.equal(answer.error, error, JSON.stringify(payload));
    assert.ok(Array.isArray(answer.problems), "the same shape as /feedback");
  }
  assert.deepEqual((await (await post("/messages", {})).json()).problems, ["Write a message first.", "Leave an email or another way to reach you."]);
  for (const topic of ["job", "freelance", "collaboration", "other", "", " Freelance "]) {
    assert.equal((await post("/messages", { ...ok, topic }, { address: `198.51.100.${topic.length + 20}` })).status, 201, topic);
  }
  assert.equal((await post("/messages", { message: "x".repeat(LIMITS.body) })).status, 413);
  assert.equal(notificationRows().length, 6, "only the kept messages alert");
});

test("the trap field takes a message from a bot and keeps nothing", async () => {
  const response = await post("/messages", { contact: "bot@spam.example", message: "Buy now", website: "https://spam.example" });
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(messageRows().length, 0);
  assert.equal(notificationRows().length, 0);
});

test(`a visitor may send ${LIMITS.messagesPerDay} messages a day`, async () => {
  const send = (message, options) => post("/messages", { contact: "ada@example.com", message }, options);
  for (let i = 1; i <= LIMITS.messagesPerDay; i++) assert.equal((await send(`message ${i}`)).status, 201);
  const oneMore = await send("one more");
  assert.equal(oneMore.status, 429);
  assert.match((await oneMore.json()).error, /tomorrow/);
  assert.equal((await send("someone else", { address: "198.51.100.2" })).status, 201);
  assert.equal((await send("next day", { now: DAY_TWO })).status, 201);
  assert.equal(messageRows().length, LIMITS.messagesPerDay + 2);
});

test("a plain form message gets a page back, with the way home", async () => {
  const form = (body) => call("/messages", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body });
  const sent = await form("name=Ada&contact=ada%40example.com&topic=freelance&message=Hello+there&website=");
  assert.equal(sent.status, 201);
  assert.match(sent.headers.get("Content-Type"), /^text\/html/);
  const page = await sent.text();
  assert.match(page, /Thank you/);
  assert.match(page, /Your message is in/);
  assert.match(page, new RegExp(`href="${env.SITE_URL}"`));
  assert.deepEqual(messageRows().map(({ name, contact, topic, message }) => ({ name, contact, topic, message })), [
    { name: "Ada", contact: "ada@example.com", topic: "freelance", message: "Hello there" },
  ]);

  const noContact = await form("message=Hi");
  assert.equal(noContact.status, 400);
  assert.match(await noContact.text(), /Leave an email or another way to reach you\./);
  const trapped = await form("contact=x&message=spam&website=x");
  assert.equal(trapped.status, 201);
  assert.equal(messageRows().length, 1);
});

// ---- The owner's app ----

const bearer = (token = env.ADMIN_TOKEN) => ({ Authorization: `Bearer ${token}` });

/** The owner's app: a native client, so no Origin, and JSON both ways. */
function app(path, { method = "GET", payload, token, ...options } = {}) {
  const headers = { ...bearer(token), ...(payload === undefined ? {} : { "Content-Type": "application/json" }) };
  return call(path, { origin: null, ...options, method, headers, body: payload === undefined ? undefined : JSON.stringify(payload) });
}
const appJson = async (path, options) => (await app(path, options)).json();

const ADMIN_ROUTES = [
  ["GET", "/admin/summary"],
  ["GET", "/admin/days"],
  ["GET", "/admin/feedback"],
  ["POST", "/admin/feedback/1", { action: "hide" }],
  ["GET", "/admin/messages"],
  ["POST", "/admin/messages/1", { action: "read" }],
  ["GET", "/admin/notifications"],
  ["GET", "/admin/reports"],
  ["POST", "/admin/devices", { token: "t" }],
  ["DELETE", "/admin/devices", { token: "t" }],
  ["GET", "/admin/no-such-route"],
];

test("every /admin route needs the token, and nothing it answers is cached", async () => {
  await post("/feedback", { message: "x" });
  await post("/messages", { contact: "x", message: "x" });
  for (const [method, path, payload] of ADMIN_ROUTES) {
    const headers = payload ? { "Content-Type": "application/json" } : {};
    const body = payload ? JSON.stringify(payload) : undefined;
    const none = await call(path, { method, headers, body, origin: null });
    assert.equal(none.status, 401, `${method} ${path} without a token`);
    assert.deepEqual(await none.json(), { error: "Unauthorized." });
    assert.equal(none.headers.get("Cache-Control"), "no-store");
    assert.equal((await app(path, { method, payload, token: "wrong" })).status, 401, `${method} ${path} with a wrong token`);
    assert.equal((await app(path, { method, payload, token: `${env.ADMIN_TOKEN}x` })).status, 401, `${method} ${path}, a longer token`);
    const basicInstead = await call(path, { method, headers: { ...headers, ...basic(env.ADMIN_TOKEN) }, body, origin: null });
    assert.equal(basicInstead.status, 401, `${method} ${path}: the inbox's Basic password is not the app's Bearer token`);
    const allowed = await app(path, { method, payload });
    assert.equal(allowed.status, path.endsWith("no-such-route") ? 404 : 200, `${method} ${path} with the token`);
    assert.equal(allowed.headers.get("Cache-Control"), "no-store", `${method} ${path}`);
    assert.equal(allowed.headers.get("Access-Control-Allow-Origin"), null, "a native app needs no CORS");
  }
  assert.equal(messageRows()[0].read_at, DAY_ONE, "the token's actions did happen");

  env.ADMIN_TOKEN = "";
  assert.equal((await app("/admin/summary", { token: "" })).status, 503, "closed while no token is set");
});

test("the app acts on notes with the inbox's rules: 404, 409 without consent, 400 for an unknown action", async () => {
  await post("/feedback", { name: "Ada", contact: "ada@example.com", rating: 5, message: "Great", consent: true });
  await post("/feedback", { message: "Private", consent: false }, { address: "198.51.100.2" });
  const act = (id, action, options) => app(`/admin/feedback/${id}`, { method: "POST", payload: { action }, ...options });

  const listed = await appJson("/admin/feedback");
  assert.deepEqual(listed.items.map((item) => item.id), [2, 1], "newest first");
  assert.deepEqual(Object.keys(listed.items[0]).sort(), ["consent", "contact", "created_at", "hidden_at", "id", "message", "name", "pinned_at", "published_at", "rating", "relation"]);
  assert.equal((await appJson("/admin/feedback?limit=1")).items.length, 1);

  const published = await act(1, "publish", { now: DAY_TWO });
  assert.equal(published.status, 200);
  assert.deepEqual(await published.json(), { ok: true });
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [1]);
  assert.equal(feedbackRows()[0].published_at, DAY_TWO);

  const refused = await act(2, "publish");
  assert.equal(refused.status, 409);
  assert.deepEqual(await refused.json(), { error: "The visitor did not agree to this note being shown." });
  const missing = await act(99, "hide");
  assert.equal(missing.status, 404);
  assert.deepEqual(await missing.json(), { error: "There is no note 99." });
  const unknown = await act(1, "explode");
  assert.equal(unknown.status, 400);
  assert.deepEqual(await unknown.json(), { error: 'There is no "explode" to do with a note.' });
  assert.equal((await app("/admin/feedback/1", { method: "POST", payload: ["hide"] })).status, 400, "not a JSON object");

  assert.equal((await act(1, "hide")).status, 200);
  assert.deepEqual((await publicNotes()).notes, []);
  assert.equal((await act(1, "unhide")).status, 200);
  assert.equal((await act(1, "unpublish")).status, 200);
  assert.deepEqual((await publicNotes()).notes, []);
  assert.equal((await act(2, "delete")).status, 200);
  assert.deepEqual(feedbackRows().map((row) => row.id), [1]);
});

test("the app and the inbox act on messages: read, unread, archive, unarchive, delete", async () => {
  for (const [i, topic] of ["job", "collaboration", "other"].entries()) {
    await post("/messages", { name: `Sender ${i + 1}`, contact: `s${i + 1}@example.com`, topic, message: `Message ${i + 1}` }, { address: `198.51.100.${i + 1}` });
  }
  const act = (id, action, options) => app(`/admin/messages/${id}`, { method: "POST", payload: { action }, ...options });

  const listed = await appJson("/admin/messages");
  assert.deepEqual(listed.items.map((item) => item.id), [3, 2, 1], "newest first");
  assert.deepEqual(listed.items[2], {
    id: 1, created_at: DAY_ONE, name: "Sender 1", contact: "s1@example.com", topic: "job", message: "Message 1", read_at: null, archived_at: null,
  });
  assert.equal((await appJson("/admin/messages?limit=2")).items.length, 2);

  assert.deepEqual(await (await act(1, "read")).json(), { ok: true });
  assert.equal((await act(1, "read", { now: DAY_TWO })).status, 200);
  assert.equal(messageRows()[0].read_at, DAY_ONE, "a second read keeps the first time");
  assert.equal((await act(1, "unread")).status, 200);
  assert.equal(messageRows()[0].read_at, null);
  assert.equal((await act(2, "archive")).status, 200);
  assert.equal(messageRows()[1].archived_at, DAY_ONE);
  assert.equal((await act(2, "unarchive")).status, 200);
  assert.equal(messageRows()[1].archived_at, null);

  const missing = await act(99, "read");
  assert.equal(missing.status, 404);
  assert.deepEqual(await missing.json(), { error: "There is no message 99." });
  const unknown = await act(1, "publish");
  assert.equal(unknown.status, 400);
  assert.deepEqual(await unknown.json(), { error: 'There is no "publish" to do with a message.' });
  assert.equal((await act(1, "toString")).status, 400, "only the five actions, not whatever an object has");
  assert.equal((await act(3, "delete")).status, 200);
  assert.deepEqual(messageRows().map((row) => row.id), [1, 2]);

  // The inbox page's buttons: the same actions, the same rules, and only from the page itself.
  const press = (id, action, headers = { "Sec-Fetch-Site": "same-origin" }) =>
    call(`/inbox/messages/${id}`, {
      method: "POST", origin: "https://api.example", body: `action=${action}`,
      headers: { ...basic(env.ADMIN_TOKEN), "Content-Type": "application/x-www-form-urlencoded", ...headers },
    });
  const pressed = await press(2, "read");
  assert.equal(pressed.status, 303);
  assert.equal(pressed.headers.get("Location"), "/inbox");
  assert.equal(messageRows()[1].read_at, DAY_ONE);
  assert.equal((await press(1, "archive", { "Sec-Fetch-Site": "cross-site" })).status, 403, "a form on another site");
  assert.equal(messageRows()[0].archived_at, null);
  assert.equal((await press(99, "read")).status, 404);
  assert.equal((await press(1, "explode")).status, 400);
  assert.equal((await call("/inbox/messages/1", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "action=read" })).status, 401);
});

test("the inbox shows the messages, escaped, with their buttons, and today's link clicks", async () => {
  await post("/messages", { name: "<b>Ada</b>", contact: "ada@example.com", topic: "freelance", message: "Hi <script>alert(1)</script>" });
  await post("/messages", { contact: "linkedin.com/in/bob", message: "Archived one" }, { address: "198.51.100.2" });
  await app("/admin/messages/2", { method: "POST", payload: { action: "archive" } });
  await click("linkedin");
  await click("linkedin", { address: "198.51.100.2" });

  const page = await (await call("/inbox", { headers: basic(env.ADMIN_TOKEN) })).text();
  assert.match(page, /· 0 notes, 0 on the site · 2 messages, 1 unread/);
  assert.match(page, /<h2>Link clicks today<\/h2>\n<ul class="days"><li><span>LinkedIn<\/span><strong>2<\/strong><\/li><li><span>GitHub<\/span><strong>0<\/strong><\/li><\/ul>/);
  assert.match(page, /<h2>Messages<\/h2>/);
  assert.match(page, /<td class="topic">Freelance<\/td>/);
  assert.match(page, /&lt;b&gt;Ada&lt;\/b&gt;<br><a href="mailto:ada@example.com">/);
  assert.match(page, /Hi &lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.doesNotMatch(page, /<script>alert/);
  assert.match(page, /<form method="post" action="\/inbox\/messages\/1"><button name="action" value="read" class="primary">Mark read<\/button><button name="action" value="archive">/);
  assert.match(page, /<tr class="hidden">\n  <td class="when">[^<]+<\/td>\n  <td class="topic">Other<\/td>/, "an archived message is dimmed");
  assert.match(page, /action="\/inbox\/messages\/2"><button name="action" value="read" class="primary">Mark read<\/button><button name="action" value="unarchive">/);
  assert.match(page, /<strong>Unread<\/strong>/);
});

test("the notification records: newest first, or after an id oldest first", async () => {
  await post("/feedback", { name: "Ada", rating: 4, message: "Lovely" });
  await post("/feedback", { message: "No name, no stars", contact: "secret@example.com" }, { address: "198.51.100.2" });
  await post("/messages", { contact: "bob@example.com", topic: "collaboration", message: "Let's build" });
  await dailyReport(env, ctx, { now: new Date(REPORT_TIME), fetch: fetchStub });

  const all = await appJson("/admin/notifications");
  assert.deepEqual(all.items.map((item) => item.id), [4, 3, 2, 1]);
  assert.deepEqual(all.items.at(-1), { id: 1, created_at: DAY_ONE, kind: "feedback", title: "New note (★4)", body: "Ada: Lovely", ref_id: 1 });
  assert.deepEqual(all.items.map(({ kind, title, ref_id }) => ({ kind, title, ref_id })), [
    { kind: "daily", title: "Today: 0 visits", ref_id: null },
    { kind: "message", title: "New message · Collaboration", ref_id: 1 },
    { kind: "feedback", title: "New note", ref_id: 2 },
    { kind: "feedback", title: "New note (★4)", ref_id: 1 },
  ]);
  assert.equal(all.items[2].body, "Someone: No name, no stars", "a note's alert never carries the contact");

  assert.deepEqual((await appJson("/admin/notifications?after=2")).items.map((item) => item.id), [3, 4], "only newer, oldest first");
  assert.deepEqual((await appJson("/admin/notifications?after=0&limit=2")).items.map((item) => item.id), [1, 2]);
  assert.deepEqual((await appJson("/admin/notifications?after=4")).items, []);
  assert.deepEqual((await appJson("/admin/notifications?limit=1")).items.map((item) => item.id), [4]);
  assert.equal((await app("/admin/notifications?after=two")).status, 400);
});

test("the days: one item per local day with any data, newest first, with clicks and countries", async () => {
  assert.deepEqual(await appJson("/admin/days"), { items: [] });
  await call("/visits", { method: "POST", cf: { country: "PH" } });
  await call("/visits", { method: "POST", address: "198.51.100.2", cf: { country: "US" } });
  await call("/visits", { method: "POST", address: "198.51.100.3", cf: { country: "PH" } });
  await click("linkedin");
  await click("github", { address: "198.51.100.2" });
  await post("/feedback", { message: "Nice" });
  await post("/messages", { contact: "a@example.com", message: "Hello" }, { now: DAY_TWO });
  await click("linkedin", { now: "2026-10-27T03:00:00.000Z" });

  const { items } = await appJson("/admin/days?limit=30");
  assert.deepEqual(items, [
    { day: "2026-10-27", visits: 0, clicks: { linkedin: 1, github: 0 }, notes: 0, messages: 0, countries: [] },
    { day: "2026-10-25", visits: 0, clicks: { linkedin: 0, github: 0 }, notes: 0, messages: 1, countries: [] },
    {
      day: "2026-10-24", visits: 3, clicks: { linkedin: 1, github: 1 }, notes: 1, messages: 0,
      countries: [{ country: "PH", count: 2 }, { country: "US", count: 1 }],
    },
  ]);
  assert.deepEqual((await appJson("/admin/days?limit=1")).items.map((item) => item.day), ["2026-10-27"]);
  assert.equal((await appJson("/admin/days?limit=0")).items.length, 3, "a limit that is not one is the default");
});

test("the summary: today's and all-time numbers, hiring, and whether push is set up", async () => {
  await call("/visits", { method: "POST" });
  await call("/visits", { method: "POST", now: DAY_TWO });
  await call("/visits", { method: "POST", now: DAY_TWO, address: "198.51.100.2" });
  await click("linkedin");
  await click("linkedin", { now: DAY_TWO });
  await click("github", { now: DAY_TWO, address: "198.51.100.2" });
  await post("/feedback", { message: "One", consent: true });
  await post("/feedback", { message: "Two", consent: true }, { now: DAY_TWO });
  await app("/admin/feedback/1", { method: "POST", payload: { action: "hide" } });
  const message = (topic, address, options) => post("/messages", { contact: "x@example.com", topic, message: topic }, { address, ...options });
  await message("job", "198.51.100.11");
  await message("freelance", "198.51.100.12");
  await message("freelance", "198.51.100.13", { now: DAY_TWO });
  await message("collaboration", "198.51.100.14", { now: DAY_TWO });
  await app("/admin/messages/2", { method: "POST", payload: { action: "archive" } });
  await app("/admin/messages/1", { method: "POST", payload: { action: "read" } });
  await app("/admin/devices", { method: "POST", payload: { token: "phone-1", platform: "android" } });

  assert.deepEqual(await appJson("/admin/summary", { now: DAY_TWO }), {
    day: "2026-10-25",
    timeZone: "Asia/Manila",
    visits: { total: 3, today: 2, since: "2026-10-24" },
    clicks: [{ link: "linkedin", total: 2, today: 1 }, { link: "github", total: 1, today: 1 }],
    notes: { total: 2, today: 1, published: 1 },
    messages: { total: 4, today: 2, unread: 2, hiring: 2 },
    push: { configured: false, devices: 1 },
  });
  env.FCM_SERVICE_ACCOUNT = "{}";
  delete env.TIME_ZONE;
  const later = await appJson("/admin/summary", { now: DAY_TWO });
  assert.deepEqual(later.push, { configured: true, devices: 1 });
  assert.equal(later.timeZone, "Asia/Manila", "the default");
});

test("phones sign in and out for push", async () => {
  const add = (payload, options) => app("/admin/devices", { method: "POST", payload, ...options });
  assert.deepEqual(await (await add({ token: "phone-1", platform: "android" })).json(), { ok: true });
  assert.equal((await add({ token: "phone-1" }, { now: DAY_TWO })).status, 200, "signing in again");
  assert.equal((await add({ token: "phone-2" })).status, 200);
  assert.deepEqual(query("SELECT token, platform, created_at, last_seen_at FROM devices ORDER BY token"), [
    { token: "phone-1", platform: "android", created_at: DAY_ONE, last_seen_at: DAY_TWO },
    { token: "phone-2", platform: "android", created_at: DAY_ONE, last_seen_at: DAY_ONE },
  ]);
  for (const payload of [{}, { token: "" }, { token: "t".repeat(LIMITS.token + 1) }, { token: { nested: true } }, { token: "t", platform: "Android OS" }]) {
    assert.equal((await add(payload)).status, 400, JSON.stringify(payload).slice(0, 60));
  }
  const out = await app("/admin/devices", { method: "DELETE", payload: { token: "phone-1" } });
  assert.deepEqual(await out.json(), { ok: true });
  assert.equal((await app("/admin/devices", { method: "DELETE", payload: { token: "phone-1" } })).status, 200, "already gone is fine");
  assert.equal((await app("/admin/devices", { method: "DELETE", payload: {} })).status, 400);
  assert.deepEqual(query("SELECT token FROM devices"), [{ token: "phone-2" }]);
});

// ---- The daily report ----

test("the daily report: today's numbers kept once per day, and one alert however often the cron runs", async () => {
  await call("/visits", { method: "POST" });
  await call("/visits", { method: "POST", address: "198.51.100.2" });
  await click("linkedin");
  await post("/feedback", { message: "Nice" });
  await post("/messages", { contact: "a@example.com", message: "Hello" });
  await call("/visits", { method: "POST", now: DAY_TWO }); // tomorrow's visit is not today's

  const report = await dailyReport(env, ctx, { now: new Date(REPORT_TIME), fetch: fetchStub });
  assert.deepEqual(report, { day: "2026-10-24", visits: 2, clicks: { linkedin: 1, github: 0 }, notes: 1, messages: 1 });
  const alerts = () => query("SELECT kind, title, body, ref_id FROM notifications WHERE kind = 'daily'");
  assert.deepEqual(alerts(), [{ kind: "daily", title: "Today: 2 visits", body: "LinkedIn 1 · GitHub 0 · 1 note · 1 message", ref_id: null }]);

  await call("/visits", { method: "POST", address: "198.51.100.3", now: REPORT_TIME });
  await dailyReport(env, ctx, { now: new Date("2026-10-24T15:59:30.000Z"), fetch: fetchStub });
  assert.deepEqual(query("SELECT day, visits, clicks, notes, messages, created_at FROM daily_reports"), [
    { day: "2026-10-24", visits: 3, clicks: '{"linkedin":1,"github":0}', notes: 1, messages: 1, created_at: REPORT_TIME },
  ], "a second run rewrites the numbers and keeps the first time");
  assert.equal(alerts().length, 1, "and sends nothing more");

  await dailyReport(env, ctx, { now: new Date("2026-10-25T15:59:00.000Z"), fetch: fetchStub });
  assert.deepEqual(alerts().at(-1), { kind: "daily", title: "Today: 1 visit", body: "LinkedIn 0 · GitHub 0 · 0 notes · 0 messages", ref_id: null });

  assert.deepEqual(await appJson("/admin/reports"), {
    items: [
      { day: "2026-10-25", visits: 1, clicks: { linkedin: 0, github: 0 }, notes: 0, messages: 0, created_at: "2026-10-25T15:59:00.000Z" },
      { day: "2026-10-24", visits: 3, clicks: { linkedin: 1, github: 0 }, notes: 1, messages: 1, created_at: REPORT_TIME },
    ],
  });
  assert.equal((await appJson("/admin/reports?limit=1")).items.length, 1);
});

test("the Worker's entry point hands the cron its scheduled time, and requests their context", async () => {
  await worker.scheduled({ scheduledTime: Date.parse(REPORT_TIME), cron: "59 15 * * *" }, env, ctx);
  assert.deepEqual(query("SELECT day FROM daily_reports"), [{ day: "2026-10-24" }]);
  const health = await worker.fetch(new Request("https://api.example/"), env, ctx);
  assert.deepEqual(await health.json(), { ok: true, service: "aboutyowkey-api" });
});

// ---- Relations and pins ----

/** A note as the new form sends it: public, with how the writer knows the owner. */
const note = (fields, address, options = {}) => post("/feedback", { consent: true, ...fields }, { address, ...options });
const noteAct = (id, action, options) => app(`/admin/feedback/${id}`, { method: "POST", payload: { action }, ...options });
const ids = (answer) => answer.notes.map((entry) => entry.id);

test("a note may say how the writer knows the owner: any case, stored lowercase, null when not said", async () => {
  const cases = [["Colleague", "colleague"], [" FRIEND ", "friend"], ["special", "special"], ["other", "other"], ["", null], [null, null], [undefined, null]];
  for (const [i, [relation, stored]] of cases.entries()) {
    const payload = { message: `said ${JSON.stringify(relation)}`, consent: true };
    if (relation !== undefined) payload.relation = relation;
    assert.equal((await post("/feedback", payload, { address: `198.51.100.${i + 1}` })).status, 201, `${relation} → ${stored}`);
  }
  assert.deepEqual(feedbackRows().map((row) => row.relation), cases.map(([, stored]) => stored));

  const form = await call("/feedback", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "message=From+the+form&relation=Friend&consent=on", address: "198.51.100.50",
  });
  assert.equal(form.status, 201);
  assert.match(await form.text(), /Thank you/);
  assert.equal(feedbackRows().at(-1).relation, "friend", "a plain form post sends it too");
});

test("a relation the form does not offer is refused with the contract's sentence, after the rating's", async () => {
  for (const relation of ["boss", "colleagues", "special someone", 5]) {
    const response = await post("/feedback", { message: "ok", relation });
    assert.equal(response.status, 400, String(relation));
    assert.deepEqual(await response.json(), { error: "Pick one of the ways you know me.", problems: ["Pick one of the ways you know me."] });
  }
  assert.equal((await (await post("/feedback", { message: "ok", relation: ["friend"] })).json()).error, "Each field is text.");
  assert.deepEqual(
    (await (await post("/feedback", { message: "", rating: 9, relation: "boss" })).json()).problems,
    ["Write a message first.", "The rating is 1 to 5.", "Pick one of the ways you know me."],
    "the order of FeedbackDraft.problems",
  );
  const form = await call("/feedback", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "message=Hi&relation=boss" });
  assert.equal(form.status, 400);
  assert.match(await form.text(), /Pick one of the ways you know me\./);
  assert.equal(feedbackRows().length, 0, "nothing of the above was kept");
});

test("a star's sheet: its notes by relation (not said is other), the sorter's counts, and the whole site's rating", async () => {
  await note({ rating: 5, relation: "colleague", message: "1" }, "198.51.100.1");
  await note({ rating: 5, relation: "friend", message: "2" }, "198.51.100.2");
  await note({ rating: 5, message: "3: did not say" }, "198.51.100.3");
  await note({ rating: 5, relation: "other", message: "4" }, "198.51.100.4");
  await note({ rating: 5, relation: "colleague", message: "5: private", consent: false }, "198.51.100.5");
  await note({ rating: 4, relation: "colleague", message: "6" }, "198.51.100.6");
  await note({ rating: 5, relation: "special", message: "7: hidden" }, "198.51.100.7");
  await note({ rating: 3, message: "8: private", consent: false }, "198.51.100.8");
  await noteAct(7, "hide");

  const sheet = async (query) => (await call(`/notes?${query}`)).json();
  const fives = await sheet("stars=5");
  assert.deepEqual(ids(fives), [4, 3, 2, 1]);
  assert.equal(fives.total, 4);
  assert.equal(fives.more, false);
  assert.deepEqual(fives.relations, { colleague: 1, friend: 1, special: 0, other: 2 }, "the public ones only; not said counts as other");
  assert.deepEqual(fives.notes.map((entry) => entry.relation), ["other", null, "friend", "colleague"], "a note keeps what it said, null included");
  assert.deepEqual(fives.ratings, {
    count: 7, average: 4.6, stars: { 1: 0, 2: 0, 3: 1, 4: 1, 5: 5 }, private: 2, privateStars: { 1: 0, 2: 0, 3: 1, 4: 0, 5: 1 },
  }, "the rating is the whole site's, whatever the list asks for");

  const others = await sheet("relation=other");
  assert.deepEqual([ids(others), others.total], [[4, 3], 2], "other is other or not said, of any star");
  assert.deepEqual(others.relations, { colleague: 2, friend: 1, special: 0, other: 2 }, "the counts never depend on the relation asked for");
  assert.deepEqual(others.ratings, fives.ratings);
  const colleagues = await sheet("stars=5&relation=colleague");
  assert.deepEqual([ids(colleagues), colleagues.total], [[1], 1], "both filters at once");
  assert.deepEqual(colleagues.relations, fives.relations, "the star's counts, for the sorter beside the list");
  assert.deepEqual(ids(await sheet("relation=%20Colleague%20")), [6, 1], "any case, as the app's Relation.of reads it");
  const none = await sheet("stars=4&relation=friend");
  assert.deepEqual([none.notes, none.total, none.more], [[], 0, false]);
  assert.deepEqual(none.relations, { colleague: 1, friend: 0, special: 0, other: 0 });
  for (const ignored of ["relation=boss", "relation=", "relation=other'%20OR%201=1--", "stars=0&relation=special%20someone"]) {
    assert.deepEqual(ids(await sheet(ignored)), [6, 4, 3, 2, 1], `${ignored} is ignored`);
  }
});

test("the notes page by offset, with how many match and whether more follow", async () => {
  for (let i = 1; i <= 15; i++) await note({ rating: (i % 5) + 1, message: `Note ${i}` }, `198.51.100.${i}`);
  const page = async (query) => (await call(`/notes?${query}`)).json();
  const first = await page("offset=0&limit=5");
  assert.deepEqual([ids(first), first.total, first.more], [[15, 14, 13, 12, 11], 15, true]);
  const last = await page("offset=10&limit=5");
  assert.deepEqual([ids(last), last.total, last.more], [[5, 4, 3, 2, 1], 15, false]);
  assert.deepEqual(ids(await page("offset=12")), [3, 2, 1], "the default page size from there");
  const past = await page("offset=15");
  assert.deepEqual([past.notes, past.total, past.more], [[], 15, false]);
  for (const ignored of ["offset=100001", "offset=-1", "offset=two"]) {
    const answer = await page(ignored);
    assert.deepEqual([answer.notes.length, answer.notes[0].id, answer.more], [LIMITS.notesPage, 15, true], `${ignored} is the first page`);
  }
  const fives = await page("stars=5&offset=1&limit=1");
  assert.deepEqual([ids(fives), fives.total, fives.more], [[9], 3, true]);
  const lastFive = await page("stars=5&offset=2&limit=1");
  assert.deepEqual([ids(lastFive), lastFive.more], [[4], false]);
});

test("pinned notes come first, the newest pin first, then the newest notes; the 1.1 app's later pages leave them out", async () => {
  for (let i = 1; i <= 5; i++) await note({ rating: 5, message: `Note ${i}`, relation: i % 2 ? "friend" : "colleague" }, `198.51.100.${i}`);
  assert.equal((await noteAct(2, "pin", { now: DAY_ONE })).status, 200);
  assert.equal((await noteAct(4, "pin", { now: DAY_TWO })).status, 200);
  const listed = await publicNotes();
  assert.deepEqual(ids(listed), [4, 2, 5, 3, 1]);
  assert.deepEqual(listed.notes.map((entry) => entry.pinned), [true, true, false, false, false]);
  assert.ok(!("pinned_at" in listed.notes[0]), "the site learns that a note is pinned, not when");

  assert.equal((await noteAct(2, "pin", { now: "2026-10-26T09:00:00.000Z" })).status, 200);
  assert.deepEqual(ids(await publicNotes()), [4, 2, 5, 3, 1], "a second pin keeps the first time");
  const page = async (query) => (await call(`/notes?${query}`)).json();
  assert.deepEqual(ids(await page("limit=2")), [4, 2]);
  assert.deepEqual(ids(await page("offset=2&limit=2")), [5, 3]);
  assert.deepEqual(ids(await page("relation=friend")), [5, 3, 1]);
  assert.deepEqual(ids(await page("relation=colleague&stars=5")), [4, 2], "a sheet lists its pins first too");

  const legacy = await page("before=5");
  assert.deepEqual([ids(legacy), legacy.more], [[3, 1], false], "by id, without the pins its first page already showed");
  const legacyPage = await page("before=5&limit=1");
  assert.deepEqual([ids(legacyPage), legacyPage.more], [[3], true]);

  assert.equal((await noteAct(4, "unpin")).status, 200);
  assert.deepEqual(ids(await publicNotes()), [2, 5, 4, 3, 1]);
});

test("only a note on the site can be pinned; hiding unpins it; the app and the inbox list relation and pinned_at", async () => {
  await note({ name: "Ada", rating: 5, relation: "colleague", message: "Public" }, "198.51.100.1");
  await note({ message: "Private", consent: false }, "198.51.100.2");
  await note({ message: "Hidden" }, "198.51.100.3");
  await noteAct(3, "hide");
  const pinnedAt = () => feedbackRows().map((row) => row.pinned_at);

  for (const id of [2, 3]) {
    const refused = await noteAct(id, "pin");
    assert.equal(refused.status, 409, `note ${id}`);
    assert.deepEqual(await refused.json(), { error: "Only a note on the site can be pinned." });
  }
  const fromInbox = await act(2, "pin");
  assert.equal(fromInbox.status, 409);
  assert.match(await fromInbox.text(), /Only a note on the site can be pinned\./);
  assert.equal((await noteAct(99, "pin")).status, 404);
  assert.deepEqual(pinnedAt(), [null, null, null]);

  assert.deepEqual(await (await noteAct(1, "pin")).json(), { ok: true });
  const byId = (items) => Object.fromEntries(items.map(({ id, relation, pinned_at }) => [id, { relation, pinned_at }]));
  assert.deepEqual(byId((await appJson("/admin/feedback")).items), {
    1: { relation: "colleague", pinned_at: DAY_ONE },
    2: { relation: null, pinned_at: null },
    3: { relation: null, pinned_at: null },
  });
  assert.equal(byId((await (await owner("/feedback")).json()).items)[1].pinned_at, DAY_ONE, "the old owner's list too");

  assert.equal((await noteAct(1, "hide")).status, 200);
  assert.deepEqual(pinnedAt(), [null, null, null], "hiding unpins");
  assert.equal((await noteAct(1, "unhide")).status, 200);
  assert.deepEqual((await publicNotes()).notes.map((entry) => entry.pinned), [false], "and unhiding does not pin it again");

  assert.equal((await act(1, "pin")).status, 303, "the inbox's Pin");
  assert.equal(pinnedAt()[0], DAY_ONE);
  assert.equal((await act(1, "unpin")).status, 303, "and Unpin");
  assert.equal(pinnedAt()[0], null);
  assert.equal((await noteAct(1, "unpin")).status, 200, "unpinning a note that is not pinned is fine");
  await noteAct(1, "pin");
  assert.equal((await noteAct(1, "unpublish")).status, 200);
  assert.equal(pinnedAt()[0], null, "the 1.0 app's Unpublish hides, so it unpins too");
  const forged = await act(1, "pin", { headers: { "Sec-Fetch-Site": "cross-site" } });
  assert.equal(forged.status, 403, "Pin follows the inbox's same-origin rule");
});

test("the owner's lists put the pinned notes first too, so an old pin is never past the limit", async () => {
  for (let i = 1; i <= 4; i++) await note({ message: `Note ${i}` }, `198.51.100.${i}`);
  await noteAct(1, "pin", { now: DAY_ONE });
  await noteAct(3, "pin", { now: DAY_TWO });
  assert.deepEqual((await appJson("/admin/feedback")).items.map((item) => item.id), [3, 1, 4, 2], "newest pin first, then newest first");
  assert.deepEqual((await appJson("/admin/feedback?limit=2")).items.map((item) => item.id), [3, 1]);
  assert.deepEqual((await (await owner("/feedback?limit=1")).json()).items.map((item) => item.id), [3], "the old owner's list too");
  const page = await (await call("/inbox", { headers: basic(env.ADMIN_TOKEN) })).text();
  const rows = [...page.matchAll(/action="\/inbox\/notes\/(\d+)"/g)].map((match) => Number(match[1]));
  assert.deepEqual(rows, [3, 1, 4, 2], "and the inbox's table");
});

test("a note's alert says who it is from when the relation has a badge", async () => {
  await note({ rating: 5, relation: "colleague", message: "One" }, "198.51.100.1");
  await note({ relation: "Friend", message: "Two" }, "198.51.100.2");
  await note({ rating: 4, relation: "special", message: "Three" }, "198.51.100.3");
  await note({ rating: 3, relation: "other", message: "Four" }, "198.51.100.4");
  await note({ rating: 2, message: "Five" }, "198.51.100.5");
  assert.deepEqual(notificationRows().map((row) => row.title), [
    "New note (★5) · Colleague",
    "New note · Friend",
    "New note (★4) · Special someone",
    "New note (★3)",
    "New note (★2)",
  ]);
});

test("the inbox shows the relation beside the name, Pinned in the status, and Pin or Unpin on the notes on the site", async () => {
  await note({ name: "Ada", rating: 5, relation: "colleague", message: "Pinned one" }, "198.51.100.1");
  await note({ relation: "special", message: "No name" }, "198.51.100.2");
  await note({ name: "Old", message: "Private", consent: false }, "198.51.100.3");
  await note({ name: "Gone", relation: "other", message: "Hidden" }, "198.51.100.4");
  await noteAct(1, "pin", { now: DAY_TWO });
  await noteAct(4, "hide");

  const page = await (await call("/inbox", { headers: basic(env.ADMIN_TOKEN) })).text();
  assert.match(page, /<td>Ada <span class="relation">Colleague<\/span><\/td>/);
  assert.match(page, /<td> <span class="relation">Special someone<\/span><\/td>/);
  assert.match(page, /<td>Gone <span class="relation">Other<\/span><\/td>/);
  assert.match(page, /<td>Old<\/td>/, "nothing when they did not say");
  assert.match(page, /<strong>On the site<\/strong><br>Pinned<br><span class="dim">2026-10-25 09:00 UTC<\/span>/);
  assert.equal(page.match(/Pinned<br>/g).length, 1);
  assert.match(page, /action="\/inbox\/notes\/1"><button name="action" value="hide">Hide<\/button><button name="action" value="unpin">Unpin<\/button><button name="action" value="delete"/);
  assert.match(page, /action="\/inbox\/notes\/2"><button name="action" value="hide">Hide<\/button><button name="action" value="pin">Pin<\/button><button name="action" value="delete"/);
  assert.match(page, /action="\/inbox\/notes\/3"><button name="action" value="hide">Hide<\/button><button name="action" value="delete"/, "a private note cannot be pinned");
  assert.match(page, /action="\/inbox\/notes\/4"><button name="action" value="unhide">Unhide<\/button><button name="action" value="delete"/, "nor a hidden one");
});

// ---- Push ----

/** A service account like Firebase's, with an RSA key made for this test, and its public half to check signatures. */
async function testServiceAccount() {
  const keys = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = Buffer.from(await crypto.subtle.exportKey("pkcs8", keys.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${der.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`;
  const account = { type: "service_account", project_id: "demo-portfolio", client_email: "push@demo-portfolio.iam.gserviceaccount.com", private_key: pem };
  return { secret: JSON.stringify(account), publicKey: keys.publicKey };
}

const fromBase64Url = (part) => Buffer.from(part, "base64url");

/** Google, played by the test: the token endpoint and FCM, with an answer per device token. */
function fakeGoogle(publicKey, answers = {}) {
  const calls = { token: [], fcm: [] };
  network = async (url, init) => {
    if (url === "https://oauth2.googleapis.com/token") {
      const form = new URLSearchParams(init.body);
      const [header, claims, signature] = form.get("assertion").split(".");
      const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, fromBase64Url(signature), new TextEncoder().encode(`${header}.${claims}`));
      calls.token.push({ grant: form.get("grant_type"), header: JSON.parse(fromBase64Url(header)), claims: JSON.parse(fromBase64Url(claims)), valid });
      return Response.json({ access_token: `ya29.test-${calls.token.length}`, expires_in: 3599, token_type: "Bearer" });
    }
    const body = JSON.parse(init.body);
    calls.fcm.push({ url, authorization: init.headers.Authorization, body });
    const answer = answers[body.message.token] || [200, { name: "projects/demo-portfolio/messages/1" }];
    return Response.json(answer[1], { status: answer[0] });
  };
  return calls;
}

const fcmError = (status, httpStatus, errorCode, field) => [
  httpStatus,
  {
    error: {
      code: httpStatus,
      status,
      message: "test",
      details: [
        { "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode },
        ...(field ? [{ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [{ field, description: "test" }] }] : []),
      ],
    },
  },
];

test("an alert is pushed to every phone as data-only, high priority, and dead tokens are dropped", async () => {
  const { secret, publicKey } = await testServiceAccount();
  env.FCM_SERVICE_ACCOUNT = secret;
  for (const token of ["phone-ok", "phone-gone", "phone-bad", "phone-not-found", "phone-busy", "phone-payload"]) {
    await app("/admin/devices", { method: "POST", payload: { token, platform: "android" } });
  }
  const google = fakeGoogle(publicKey, {
    "phone-gone": fcmError("NOT_FOUND", 404, "UNREGISTERED"),
    "phone-bad": fcmError("INVALID_ARGUMENT", 400, "INVALID_ARGUMENT", "message.token"),
    "phone-not-found": [404, { error: { code: 404, status: "NOT_FOUND", message: "Requested entity was not found." } }],
    "phone-busy": fcmError("UNAVAILABLE", 503, "UNAVAILABLE"),
    "phone-payload": fcmError("INVALID_ARGUMENT", 400, "INVALID_ARGUMENT", "message.data"),
  });
  const quiet = mock.method(console, "error", () => {});

  const sent = await post("/feedback", { name: "Ada", rating: 4, message: "Lovely site" });
  assert.equal(sent.status, 201);
  assert.equal(pending.length, 1, "the push runs in waitUntil, after the answer");
  await settle();

  assert.equal(google.token.length, 1);
  const [{ grant, header, claims, valid }] = google.token;
  assert.equal(grant, "urn:ietf:params:oauth:grant-type:jwt-bearer");
  assert.ok(valid, "signed with the service account's key");
  assert.deepEqual(header, { alg: "RS256", typ: "JWT" });
  assert.deepEqual(claims, {
    iss: "push@demo-portfolio.iam.gserviceaccount.com",
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: Date.parse(DAY_ONE) / 1000,
    exp: Date.parse(DAY_ONE) / 1000 + 3600,
  });

  assert.equal(google.fcm.length, 6);
  const toOk = google.fcm.find((entry) => entry.body.message.token === "phone-ok");
  assert.equal(toOk.url, "https://fcm.googleapis.com/v1/projects/demo-portfolio/messages:send");
  assert.equal(toOk.authorization, "Bearer ya29.test-1");
  assert.deepEqual(toOk.body, {
    message: {
      token: "phone-ok",
      data: { id: "1", kind: "feedback", title: "New note (★4)", body: "Ada: Lovely site", ref_id: "1" },
      android: { priority: "HIGH" },
    },
  }, "data only: no notification block, every value a string");

  assert.deepEqual(query("SELECT token FROM devices ORDER BY token"), [{ token: "phone-busy" }, { token: "phone-ok" }, { token: "phone-payload" }], "UNREGISTERED, NOT_FOUND and a malformed token are dropped; a busy server or a payload FCM refuses is not");
  assert.equal(quiet.mock.callCount(), 2, "the busy one and the refused payload are logged");

  await post("/messages", { contact: "bob@example.com", topic: "job", message: "Hiring?" }, { now: "2026-10-24T10:40:00.000Z" });
  await settle();
  assert.equal(google.token.length, 1, "the access token is reused while it is fresh");
  const second = google.fcm.slice(6);
  assert.deepEqual(second.map((entry) => entry.body.message.token).sort(), ["phone-busy", "phone-ok", "phone-payload"]);
  assert.deepEqual(second[0].body.message.data, { id: "2", kind: "message", title: "New message · Job", body: "bob@example.com: Hiring?", ref_id: "1" });

  await post("/messages", { contact: "bob@example.com", message: "Later" }, { now: "2026-10-24T10:56:00.000Z" });
  await settle();
  assert.equal(google.token.length, 2, "within five minutes of expiry, a new one");

  await dailyReport(env, ctx, { now: new Date(REPORT_TIME), fetch: fetchStub });
  await settle();
  assert.equal(google.fcm.at(-1).body.message.data.ref_id, "", "the daily report refers to nothing");
  quiet.mock.restore();
});

test("FCM refusing the access token makes the next push sign in again", async () => {
  const { secret, publicKey } = await testServiceAccount();
  env.FCM_SERVICE_ACCOUNT = secret;
  await app("/admin/devices", { method: "POST", payload: { token: "phone-ok" } });
  const google = fakeGoogle(publicKey, { "phone-ok": [401, { error: { code: 401, status: "UNAUTHENTICATED" } }] });
  const quiet = mock.method(console, "error", () => {});
  await post("/feedback", { message: "One" });
  await settle();
  await post("/feedback", { message: "Two" }, { address: "198.51.100.2" });
  await settle();
  assert.equal(google.token.length, 2);
  assert.deepEqual(query("SELECT token FROM devices"), [{ token: "phone-ok" }], "a refused access token is not a dead phone");
  quiet.mock.restore();
});

test("without FCM_SERVICE_ACCOUNT, or without phones, an alert is only its record", async () => {
  await app("/admin/devices", { method: "POST", payload: { token: "phone-ok" } });
  assert.equal((await post("/feedback", { message: "No push" })).status, 201);
  assert.deepEqual(pending, [], "nothing is pushed while the secret is unset");
  assert.equal(notificationRows().length, 1, "the record is still written");

  const { secret, publicKey } = await testServiceAccount();
  env.FCM_SERVICE_ACCOUNT = secret;
  await app("/admin/devices", { method: "DELETE", payload: { token: "phone-ok" } });
  const google = fakeGoogle(publicKey);
  await post("/messages", { contact: "a@example.com", message: "Nobody signed in" });
  await settle();
  assert.deepEqual(google, { token: [], fcm: [] }, "no phones: Google is not even asked for a token");
  assert.equal(notificationRows().length, 2);
});

test("a broken push never breaks the visitor's request", async () => {
  env.FCM_SERVICE_ACCOUNT = "not json";
  await app("/admin/devices", { method: "POST", payload: { token: "phone-ok" } });
  const quiet = mock.method(console, "error", () => {});
  const sent = await post("/feedback", { message: "Still kept" });
  assert.equal(sent.status, 201);
  await settle();
  assert.equal(quiet.mock.callCount(), 1, "the reason is logged for `npm run tail`");
  assert.match(String(quiet.mock.calls[0].arguments[1]), /FCM_SERVICE_ACCOUNT is not a service-account key/);
  assert.equal(feedbackRows().length, 1);
  assert.equal(notificationRows().length, 1);

  env.FCM_SERVICE_ACCOUNT = undefined;
  env.DB.raw.exec("DROP TABLE notifications");
  const noRecord = await post("/messages", { contact: "a@example.com", message: "Kept anyway" });
  assert.equal(noRecord.status, 201, "even a failed record leaves the message kept");
  assert.equal(messageRows().length, 1);
  quiet.mock.restore();
});
