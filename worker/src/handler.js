/**
 * The portfolio's backend: a visit counter and a feedback inbox, on one Cloudflare Worker and one D1 database
 * (schema.sql). Everything the site calls is here; index.js only hands requests over, so the tests run this file in
 * Node against SQLite without a Workers runtime.
 *
 * Routes:
 *   GET  /            health
 *   GET  /visits      { total, today, since }    reads the counts without adding to them
 *   POST /visits      { total, today, since }    counts one visit per visitor per day, then answers as GET
 *   POST /feedback    { ok }                     JSON from the page's script, or a plain HTML form post
 *   GET  /feedback    { visits, items }          owner only: `Authorization: Bearer <ADMIN_TOKEN>`
 *   GET  /inbox       HTML                       owner only: HTTP Basic auth, any user name, password ADMIN_TOKEN
 *
 * Bindings and settings (wrangler.toml): DB (D1), ALLOWED_ORIGINS, SITE_URL. Secrets: ADMIN_TOKEN, VISITOR_SALT.
 */
import { renderInbox, renderNotice } from "./inbox.js";

export const LIMITS = Object.freeze({
  name: 80,
  contact: 160,
  message: 2000,
  /** Notes one visitor may leave in a day; the next gets a 429. */
  notesPerDay: 5,
  /** Characters of request body read; more is a 413. */
  body: 16 * 1024,
});

/**
 * Crawlers, link previews, monitors and CI's Lighthouse run the page's script too. They may read the count, but
 * they are not visits. Real browsers never put a URL in their user agent; most bots do.
 */
const BOT = /bot|crawl|spider|slurp|lighthouse|headless|preview|facebookexternalhit|curl\/|wget\/|python|go-http-client|java\/|https?:\/\//i;

export async function handleRequest(request, env, now = new Date()) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const cors = corsHeaders(request, env);
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  // A page on another site may neither count visits nor leave notes here. A request with no Origin at all (curl,
  // the owner's tools) is fine: the owner's routes have their own lock.
  const origin = request.headers.get("Origin");
  if (origin && !cors["Access-Control-Allow-Origin"] && request.method !== "GET") {
    return json({ error: "This page may not call the API." }, 403, cors);
  }
  try {
    if (path === "/" && request.method === "GET") return json({ ok: true, service: "aboutyowkey-api" }, 200, cors);
    if (path === "/visits" && request.method === "GET") return json(await visitCounts(env.DB, day(now)), 200, cors);
    if (path === "/visits" && request.method === "POST") return json(await recordVisit(request, env, now), 200, cors);
    if (path === "/feedback" && request.method === "POST") return await submitFeedback(request, env, now, cors);
    if (path === "/feedback" && request.method === "GET") return await listFeedback(request, env, url, now);
    if (path === "/inbox" && request.method === "GET") return await inbox(request, env, now);
    return json({ error: "Not found." }, 404, cors);
  } catch (error) {
    console.error(error); // `npm run tail` shows it; the visitor gets no detail
    return json({ error: "Something broke on the server." }, 500, cors);
  }
}

// ---- Visits ----

/** The UTC date of [now], the unit visits are counted in. */
export function day(now) {
  return now.toISOString().slice(0, 10);
}

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
  const today = day(now);
  const agent = request.headers.get("User-Agent") || "";
  if (!agent || BOT.test(agent)) return visitCounts(env.DB, today);
  const visitor = await visitorId(request, env, today, agent);
  const inserted = await env.DB.prepare("INSERT OR IGNORE INTO visitors (day, visitor) VALUES (?1, ?2)").bind(today, visitor).run();
  if (inserted.meta.changes === 1) {
    await env.DB
      .prepare("INSERT INTO visits (day, count) VALUES (?1, 1) ON CONFLICT (day) DO UPDATE SET count = count + 1")
      .bind(today)
      .run();
    // An id carries its day, so an earlier day's rows can never match again: nothing about a visitor outlives the day.
    await env.DB.prepare("DELETE FROM visitors WHERE day <> ?1").bind(today).run();
  }
  return visitCounts(env.DB, today);
}

/**
 * Who is visiting, for today only: a hash of the day, the address, the browser and a secret salt. The address is
 * never stored, the hash cannot be turned back into it without the salt, and tomorrow the same person gets a new one.
 */
async function visitorId(request, env, today, agent) {
  const address = request.headers.get("CF-Connecting-IP") || "unknown";
  return sha256Hex(`${today}|${address}|${agent}|${env.VISITOR_SALT || ""}`);
}

// ---- Feedback ----

async function submitFeedback(request, env, now, cors) {
  const body = await readBody(request);
  if (body.error) return reply(body.form, { error: body.error }, body.status, cors, env);
  const fields = body.fields;
  // The trap: a field people never see, which bots that fill every field fill. Say yes and keep nothing.
  if (fields.website) return reply(body.form, { ok: true }, 201, cors, env);
  const problems = validateFeedback(fields);
  if (problems.length) return reply(body.form, { error: problems[0], problems }, 400, cors, env);

  const today = day(now);
  const visitor = await visitorId(request, env, today, request.headers.get("User-Agent") || "");
  const { n } = await env.DB.prepare("SELECT COUNT(*) AS n FROM feedback WHERE visitor = ?1").bind(visitor).first();
  if (n >= LIMITS.notesPerDay) {
    return reply(body.form, { error: "That is plenty for one day, thank you. Try again tomorrow." }, 429, cors, env);
  }
  const note = normalizeFeedback(fields);
  await env.DB
    .prepare("INSERT INTO feedback (created_at, name, contact, rating, message, visitor) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
    .bind(now.toISOString(), note.name, note.contact, note.rating, note.message, visitor)
    .run();
  return reply(body.form, { ok: true }, 201, cors, env);
}

/** JSON from the page's script, or the plain form a browser without it posts. Anything else is turned away. */
async function readBody(request) {
  const type = (request.headers.get("Content-Type") || "").split(";")[0].trim().toLowerCase();
  const form = type === "application/x-www-form-urlencoded";
  if (!form && type !== "application/json") return { form: false, error: "Send JSON or a URL-encoded form.", status: 415 };
  const text = await request.text();
  if (text.length > LIMITS.body) return { form, error: `Keep the note under ${LIMITS.message} characters.`, status: 413 };
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
  if ([message, name, contact, rating].includes(null)) return ["Each field is text."];
  if (!message) problems.push("Write a message first.");
  else if (message.length > LIMITS.message) problems.push(`Keep the message under ${LIMITS.message} characters.`);
  if (name.length > LIMITS.name) problems.push(`Keep the name under ${LIMITS.name} characters.`);
  if (contact.length > LIMITS.contact) problems.push(`Keep the email under ${LIMITS.contact} characters.`);
  if (rating !== "" && !/^[1-5]$/.test(rating)) problems.push("The rating is 1 to 5.");
  return problems;
}

/** The note as stored: trimmed, the optional fields null when empty, the rating a number. */
export function normalizeFeedback(fields) {
  const rating = text(fields.rating);
  return {
    name: text(fields.name) || null,
    contact: text(fields.contact) || null,
    rating: rating ? Number(rating) : null,
    message: text(fields.message),
  };
}

// ---- The owner ----

async function listFeedback(request, env, url, now) {
  const entry = await authorize(bearerToken(request), env);
  if (entry !== true) return entry;
  const limit = Math.min(Math.max(Number(url.searchParams.get("limit")) || 100, 1), 500);
  const [visits, items] = await Promise.all([visitCounts(env.DB, day(now)), feedbackItems(env.DB, limit)]);
  return json({ visits, items }, 200, { "Cache-Control": "no-store" });
}

async function inbox(request, env, now) {
  const entry = await authorize(basicPassword(request), env, { basic: true });
  if (entry !== true) return entry;
  const today = day(now);
  const [visits, items, days] = await Promise.all([
    visitCounts(env.DB, today),
    feedbackItems(env.DB, 200),
    env.DB.prepare("SELECT day, count FROM visits ORDER BY day DESC LIMIT 14").all().then((result) => result.results),
  ]);
  return html(renderInbox({ visits, items, days, today }), 200, { "Cache-Control": "no-store", "X-Robots-Tag": "noindex" });
}

async function feedbackItems(db, limit) {
  const { results } = await db
    .prepare("SELECT id, created_at, name, contact, rating, message FROM feedback ORDER BY id DESC LIMIT ?1")
    .bind(limit)
    .all();
  return results;
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
function reply(form, payload, status, cors, env) {
  if (!form) return json(payload, status, cors);
  const ok = status < 400;
  return html(
    renderNotice(ok ? "Thank you" : "Not sent", ok ? "Your note is in; I read every one." : payload.error, env.SITE_URL),
    status,
  );
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
