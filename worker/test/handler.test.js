import { beforeEach, mock, test } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { handleRequest, LIMITS } from "../src/handler.js";
import { openDatabase } from "./d1.js";

const MIGRATIONS = fileURLToPath(new URL("../migrations", import.meta.url));
const SITE = "https://yowkylito.github.io";
const CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const DAY_ONE = "2026-10-24T10:00:00.000Z";
const DAY_TWO = "2026-10-25T09:00:00.000Z";

let env;
beforeEach(() => {
  env = {
    DB: openDatabase(MIGRATIONS),
    ALLOWED_ORIGINS: `${SITE}, http://localhost:8080`,
    SITE_URL: `${SITE}/AboutYowkey/`,
    ADMIN_TOKEN: "owner-secret",
    VISITOR_SALT: "salt",
  };
});

/** One request as the page would make it: from the site, a real browser, one address, at a given moment. */
function call(path, { method = "GET", headers = {}, body, address = "203.0.113.7", agent = CHROME, origin = SITE, now = DAY_ONE } = {}) {
  const h = new Headers(headers);
  if (agent !== null) h.set("User-Agent", agent);
  if (address) h.set("CF-Connecting-IP", address);
  if (origin) h.set("Origin", origin);
  return handleRequest(new Request(`https://api.example${path}`, { method, headers: h, body }), env, new Date(now));
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
  assert.deepEqual(Object.keys(listed.items[0]).sort(), ["consent", "contact", "created_at", "hidden_at", "id", "message", "name", "published_at", "rating"]);
  assert.deepEqual(listed.items[1], {
    id: 1, created_at: DAY_ONE, name: "Ada", contact: null, rating: 5, message: "Nice site.\nHire me?", consent: 0, published_at: null, hidden_at: null,
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

test("a note is public only with the visitor's tick and the owner's click, and the rating is the published notes'", async () => {
  const empty = await call("/notes");
  assert.equal(empty.headers.get("Cache-Control"), "public, max-age=60");
  assert.equal(empty.headers.get("Access-Control-Allow-Origin"), SITE);
  assert.deepEqual(await empty.json(), { visits: { total: 0, today: 0, since: null }, ratings: { count: 0, average: null }, notes: [] });

  await post("/feedback", { name: "Ada", contact: "ada@example.com", rating: 5, message: "Great", consent: true });
  await post("/feedback", { rating: 4, message: "Good", consent: "1" }, { address: "198.51.100.2" });
  await post("/feedback", { name: "Troll", rating: 1, message: "Private rant", consent: false }, { address: "198.51.100.3" });
  await post("/feedback", { message: "No stars", consent: true }, { address: "198.51.100.4" });
  assert.deepEqual((await publicNotes()).notes, [], "nothing shows before the owner says so");

  assert.equal((await act(1, "publish")).status, 303);
  assert.equal((await act(2, "publish", { now: DAY_TWO })).status, 303);
  assert.equal((await act(4, "publish", { now: DAY_TWO })).status, 303);
  const shown = await publicNotes({ now: DAY_TWO });
  assert.deepEqual(shown.ratings, { count: 2, average: 4.5 }, "a note without stars is shown but does not rate");
  assert.deepEqual(shown.notes, [
    { id: 4, name: null, rating: null, message: "No stars", date: "2026-10-25" },
    { id: 2, name: null, rating: 4, message: "Good", date: "2026-10-25" },
    { id: 1, name: "Ada", rating: 5, message: "Great", date: "2026-10-24" },
  ]);
  assert.ok(!JSON.stringify(shown).includes("ada@example.com"), "the contact is never public");

  const refused = await act(3, "publish");
  assert.equal(refused.status, 409, "no tick, no publishing");
  assert.match(await refused.text(), /did not agree/);

  assert.equal((await act(1, "hide")).status, 303);
  const afterHide = await publicNotes();
  assert.deepEqual(afterHide.ratings, { count: 1, average: 4 });
  assert.deepEqual(afterHide.notes.map((note) => note.id), [4, 2]);
  assert.equal((await act(1, "unhide")).status, 303);
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [4, 2, 1]);
  assert.equal((await act(2, "unpublish")).status, 303);
  assert.deepEqual((await publicNotes()).notes.map((note) => note.id), [4, 1]);

  assert.equal((await act(3, "delete")).status, 303);
  assert.deepEqual(feedbackRows().map((row) => row.id), [1, 2, 4]);
  assert.equal((await act(1, "explode")).status, 400);
  assert.equal((await act(99, "hide")).status, 404);
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
  await post("/feedback", { message: "Already up", consent: true }, { address: "198.51.100.5" });
  await act(3, "publish");

  const open = await call("/inbox", { headers: basic(env.ADMIN_TOKEN, "whoever"), now: DAY_TWO });
  assert.equal(open.status, 200);
  assert.equal(open.headers.get("X-Robots-Tag"), "noindex");
  assert.equal(open.headers.get("Cache-Control"), "no-store");
  const page = await open.text();
  assert.match(page, /2 visits since 2026-10-24 · 1 today \(2026-10-25, UTC\) · 3 notes, 1 waiting/);
  assert.match(page, /<strong>Waiting<\/strong>/, "the consented, unpublished note waits");
  assert.match(page, /<span class="dim">Private<\/span>/, "the note without a tick is private");
  assert.match(page, /Published<br>/);
  assert.match(page, /<form method="post" action="\/inbox\/notes\/1">.*value="publish" class="primary"/s);
  assert.match(page, /<form method="post" action="\/inbox\/notes\/2">.*value="publish" disabled/s);
  assert.match(page, /<form method="post" action="\/inbox\/notes\/3">.*value="unpublish"/s);
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
