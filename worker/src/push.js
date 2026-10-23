/**
 * The owner's alerts: a new note, a new message, the daily report. Each is first a row in `notifications`, which
 * the app's backup poll reads, so nothing depends on a push arriving. Then, when the FCM_SERVICE_ACCOUNT secret
 * (a Google service-account key, as JSON) is set and a phone has signed in, each phone gets a data-only,
 * high-priority FCM message carrying the same record; the app turns it into a notification whose id is the
 * record's, so the poll showing the same record later replaces it instead of adding a second.
 *
 * The push runs in `ctx.waitUntil`, after the visitor already has their answer. It never throws: a failure is
 * logged for `npm run tail`, and the record is still there for the poll.
 *
 * `fetch` is a parameter so the tests never reach Google.
 */

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
/**
 * What FCM says about a token that will never work again: the app was removed. INVALID_ARGUMENT says that too when
 * the token is not one, but also when the payload is wrong, so it only counts when FCM names the token as the field.
 */
const DEAD_TOKEN = new Set(["UNREGISTERED", "NOT_FOUND"]);
/** An access token is used until this long before Google says it expires. */
const EARLY = 5 * 60 * 1000;

/** Google's access token, kept for the life of this isolate so a burst of alerts signs in once. */
let cached = null;

/** Drops the kept access token: FCM refused it, or a test wants a clean start. */
export function forgetAccessToken() {
  cached = null;
}

/** Keeps the record, then pushes it to the owner's phones. Returns the record as `GET /admin/notifications` lists it. */
export async function notify(env, ctx, { kind, title, body, refId = null }, { now = new Date(), fetch: send = defaultFetch } = {}) {
  const createdAt = now.toISOString();
  const { meta } = await env.DB
    .prepare("INSERT INTO notifications (created_at, kind, title, body, ref_id) VALUES (?1, ?2, ?3, ?4, ?5)")
    .bind(createdAt, kind, title, body, refId ?? null)
    .run();
  const record = { id: Number(meta.last_row_id), created_at: createdAt, kind, title, body, ref_id: refId ?? null };
  if (env.FCM_SERVICE_ACCOUNT) {
    const pushing = pushToDevices(env, record, send, now).catch((error) => console.error("Push not sent:", error));
    if (typeof ctx?.waitUntil === "function") ctx.waitUntil(pushing);
    else await pushing;
  }
  return record;
}

/** Workers' fetch, called as itself: some runtimes refuse a detached reference. */
function defaultFetch(...args) {
  return fetch(...args);
}

/** One FCM message per signed-in phone, all at once; the tokens FCM calls dead are deleted. */
async function pushToDevices(env, record, send, now) {
  const { results: devices } = await env.DB.prepare("SELECT token FROM devices").all();
  if (!devices.length) return { sent: 0, pruned: 0 };
  const account = serviceAccount(env.FCM_SERVICE_ACCOUNT);
  const access = await accessToken(account, send, now);
  const url = `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`;
  const data = dataOf(record);
  const outcomes = await Promise.all(devices.map(({ token }) => sendOne(send, url, access, token, data)));
  const dead = outcomes.filter((outcome) => outcome.dead);
  for (const { token } of dead) await env.DB.prepare("DELETE FROM devices WHERE token = ?1").bind(token).run();
  if (outcomes.some((outcome) => outcome.status === 401)) forgetAccessToken();
  return { sent: outcomes.filter((outcome) => outcome.ok).length, pruned: dead.length };
}

/** FCM's `data` carries strings only; the app reads the same fields as the poll's JSON. */
export function dataOf(record) {
  return {
    id: String(record.id),
    kind: record.kind,
    title: record.title,
    body: record.body,
    ref_id: record.ref_id == null ? "" : String(record.ref_id),
  };
}

/**
 * Data only (no `notification` block), so the app builds the notification itself, with the record's id, in its
 * own channel; high priority, so a phone in Doze wakes for it.
 */
async function sendOne(send, url, access, token, data) {
  try {
    const response = await send(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ message: { token, data, android: { priority: "HIGH" } } }),
    });
    if (response.ok) return { token, ok: true, status: response.status };
    const { reasons, badToken } = await fcmReasons(response);
    const dead = badToken || reasons.some((reason) => DEAD_TOKEN.has(reason));
    if (!dead) console.error(`FCM refused a push: ${response.status} ${reasons.join(", ")}`);
    return { token, ok: false, dead, status: response.status };
  } catch (error) {
    console.error("FCM could not be reached:", error);
    return { token, ok: false, dead: false, status: 0 };
  }
}

/**
 * The error's status and its FCM error codes, e.g. ["NOT_FOUND", "UNREGISTERED"], and whether it is an
 * INVALID_ARGUMENT whose BadRequest detail names `message.token`: the token itself is malformed.
 */
async function fcmReasons(response) {
  try {
    const { error } = await response.json();
    const details = error?.details || [];
    const codes = details.map((detail) => detail?.errorCode).filter(Boolean);
    const reasons = [error?.status, ...codes].filter(Boolean);
    const badToken =
      reasons.includes("INVALID_ARGUMENT") &&
      details.some((detail) => (detail?.fieldViolations || []).some((violation) => violation?.field === "message.token"));
    return { reasons, badToken };
  } catch {
    return { reasons: [], badToken: false };
  }
}

function serviceAccount(secret) {
  let account;
  try {
    account = JSON.parse(secret);
  } catch {
    account = null;
  }
  if (!account || !["project_id", "client_email", "private_key"].every((key) => typeof account[key] === "string")) {
    throw new Error("FCM_SERVICE_ACCOUNT is not a service-account key: it needs project_id, client_email and private_key.");
  }
  return account;
}

/** Google's access token for FCM: a JWT signed with the service account's key, exchanged at the token endpoint. */
async function accessToken(account, send, now) {
  if (cached && cached.account === account.client_email && now.getTime() < cached.expiresAt - EARLY) return cached.token;
  const response = await send(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: await signedJwt(account, now),
    }).toString(),
  });
  if (!response.ok) throw new Error(`Google refused the service account: ${response.status} ${(await response.text()).slice(0, 200)}`);
  const { access_token: token, expires_in: lifetime } = await response.json();
  cached = { account: account.client_email, token, expiresAt: now.getTime() + Number(lifetime || 3600) * 1000 };
  return token;
}

/** RS256 over `header.claims`, with the key from the service account's PKCS #8 PEM, through Web Crypto. */
export async function signedJwt(account, now) {
  const issued = Math.floor(now.getTime() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({ iss: account.client_email, scope: SCOPE, aud: TOKEN_URL, iat: issued, exp: issued + 3600 }));
  const key = await crypto.subtle.importKey("pkcs8", pemBytes(account.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  return `${header}.${claims}.${base64Url(new Uint8Array(signature))}`;
}

/** The key's bytes: the PEM without its armour lines and line breaks (escaped `\n` too, if the JSON was pasted twice). */
function pemBytes(pem) {
  const body = pem.replace(/\\n/g, "\n").replace(/-----(BEGIN|END) [^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

function base64Url(input) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
