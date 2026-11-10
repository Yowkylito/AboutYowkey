/**
 * The owner's pages, in the site's own colours: the inbox, with a button for everything that can be done to a
 * message or a note, and the one-paragraph notices a plain form post gets back. Every value from the database or
 * the request goes through escape().
 */

/** The names the owner reads, on this page and in the alerts on the phone. */
const TOPIC_LABELS = { job: "Job", freelance: "Freelance", collaboration: "Collaboration", other: "Other" };
const LINK_LABELS = { linkedin: "LinkedIn", github: "GitHub" };
/** The app's `Relation` labels, so the inbox, the alert and the note card say it the same way. */
const RELATION_LABELS = { colleague: "Colleague", friend: "Friend", special: "Special someone", other: "Other" };

/**
 * The page's only script: the confirm() of the buttons that cannot be undone. Each is allowed by its hash in
 * PAGE_HEADERS (the tests check that every handler on the page is), so an injected one would not run.
 */
const CONFIRM_DELETE_NOTE = "return confirm('Delete this note for good?')";
const CONFIRM_MAKE_PUBLIC = "return confirm('Only if the writer agreed: show this note on the site?')";
const CONFIRM_DELETE_MESSAGE = "return confirm('Delete this message for good?')";

/**
 * What every page here is sent with. The browser sends the inbox's saved password with any request to this origin,
 * so no other site may frame these pages (a framed button would pass the same-origin check), they run nothing but
 * the confirm() handlers above, post only here, and send no referrer along with their links.
 */
export const PAGE_HEADERS = Object.freeze({
  "Content-Security-Policy": [
    "default-src 'none'",
    "style-src 'unsafe-inline'",
    // CONFIRM_DELETE_NOTE, CONFIRM_MAKE_PUBLIC, CONFIRM_DELETE_MESSAGE
    "script-src 'unsafe-hashes' 'sha256-zY3iYRSQDJXbUMSgNR4AsxVn2VZ8q5qlVfSOBzJkN/A=' 'sha256-YF7z7fKimeZnwJTnK6nX2xJAomwf/T7lAUFwdRgmnCg=' 'sha256-+90hzeHH+mu8ObWA6aWX+IrEsiCWO3OrV+Dj9Gdbycw='",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "base-uri 'none'",
  ].join("; "),
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
});

export function topicLabel(topic) {
  return TOPIC_LABELS[topic] || "Other";
}

export function relationLabel(relation) {
  return RELATION_LABELS[relation] || "Other";
}

export function linkLabel(link) {
  return LINK_LABELS[link] || link.charAt(0).toUpperCase() + link.slice(1);
}

/** The inbox's tabs, in order; the first is the one a bare `/inbox` opens. */
export const INBOX_TABS = Object.freeze(["messages", "notes", "reading", "visits"]);
/** Messages, or notes, on one page of their tab. */
export const INBOX_PAGE = 25;

const TAB_LABELS = { messages: "Messages", notes: "Notes", reading: "Reading", visits: "Visits" };
const SECTION_LABELS = {
  home: "First screen", experience: "Experience", projects: "Projects", skills: "Skills", services: "Focus areas",
  recognition: "Education & awards", contact: "Contact",
};
const DEVICE_LABELS = { phone: "Phone", tablet: "Tablet", desktop: "Desktop" };
const SOURCE_LABELS = { direct: "Direct or unknown", linkedin: "LinkedIn", github: "GitHub", google: "Google", facebook: "Facebook", other: "Another site" };
const VIEW_LABELS = { app: "The app (canvas)", plain: "The plain HTML page" };

/**
 * The owner's page: the numbers that matter at the top, then one tab at a time, each a page of cards that read on
 * a phone as well as on a desk. [counts] is the header's (and the tabs' badges), [data] the tab's own rows.
 */
export function renderInbox({ tab, page = 1, counts, data, today, timeZone }) {
  const query = (name, extra = "") => (name === INBOX_TABS[0] && !extra ? "/inbox" : `/inbox?tab=${name}${extra}`);
  const badge = { messages: counts.unread, notes: 0, reading: 0, visits: 0 };
  const tabs = INBOX_TABS.map((name) => {
    const count = badge[name] ? ` <span class="count">${Number(badge[name])}</span>` : "";
    return `<a href="${query(name)}"${name === tab ? ' aria-current="page"' : ""}>${TAB_LABELS[name]}${count}</a>`;
  }).join("");
  const rating = counts.rating == null ? "—" : `${Number(counts.rating).toFixed(1)} ★`;
  const tiles = [
    ["Visits today", counts.today, escape(today)],
    ["Visits in all", counts.visits, counts.since ? `since ${escape(counts.since)}` : "none yet"],
    ["Unread messages", counts.unread, `${Number(counts.waiting)} in the inbox`],
    ["Notes on the site", counts.shown, `of ${Number(counts.notes)} · ${rating}`],
  ]
    .map(([label, value, detail]) => `<li><span>${label}</span><strong>${Number(value)}</strong><small>${detail}</small></li>`)
    .join("");
  const body = { messages: messagesTab, notes: notesTab, reading: readingTab, visits: visitsTab }[tab](data, { page, timeZone, query, counts });
  return pageOf(
    `${TAB_LABELS[tab]} · Inbox`,
    `<header class="top"><h1>Inbox</h1><p class="muted">Times are ${escape(timeZone)}'s.</p></header>
<ul class="tiles">${tiles}</ul>
<nav class="tabs" aria-label="Inbox">${tabs}</nav>
${body}`,
  );
}

/** The link to the tab's next and previous pages, when there are any. */
function pager(page, more, href) {
  if (page <= 1 && !more) return "";
  const newer = page > 1 ? `<a href="${escape(href(page - 1))}">Newer</a>` : "<span></span>";
  const older = more ? `<a href="${escape(href(page + 1))}">Older</a>` : "<span></span>";
  return `<nav class="pager" aria-label="Pages">${newer}<span class="muted">Page ${Number(page)}</span>${older}</nav>`;
}

function messagesTab({ rows, more, archived }, { page, timeZone, query, counts }) {
  const show = archived ? "&show=archived" : "";
  const here = (n) => query("messages", `${show}${n > 1 ? `&page=${n}` : ""}`);
  const filter = `<p class="filter"><a href="/inbox"${archived ? "" : ' aria-current="true"'}>Inbox <span class="count">${Number(counts.waiting)}</span></a><a href="/inbox?tab=messages&amp;show=archived"${archived ? ' aria-current="true"' : ""}>Archived <span class="count">${Number(counts.archived)}</span></a></p>`;
  const cards = rows
    .map(
      (item) => `<article id="m${Number(item.id)}" class="card${item.archived_at ? " hidden" : ""}${!item.read_at && !item.archived_at ? " unread" : ""}">
  <header><span class="who">${escape(item.name || "No name")}</span><span class="topic">${escape(topicLabel(item.topic))}</span><time>${escape(when(item.created_at, timeZone))}</time></header>
  <p class="contact">${contactLine(item.contact)}</p>
  <p class="message">${escape(item.message)}</p>
  <footer><span class="status">${messageStatus(item, timeZone)}</span><span class="acts">${replyLink(item.contact)}<form method="post" action="/inbox/messages/${Number(item.id)}">${messageButtons(item)}${back(here(page))}</form></span></footer>
</article>`,
    )
    .join("\n");
  const none = archived ? "Nothing archived." : "No messages waiting.";
  return `<p class="muted note">Messages are private: they are never on the site.</p>
${filter}
${rows.length ? `<div class="cards">\n${cards}\n</div>` : `<p class="empty">${none}</p>`}
${pager(page, more, here)}`;
}

function notesTab({ rows, more }, { page, timeZone, query }) {
  const here = (n) => query("notes", n > 1 ? `&page=${n}` : "");
  const cards = rows
    .map(
      (item) => `<article id="n${Number(item.id)}" class="card${item.hidden_at ? " hidden" : ""}">
  <header><span class="who">${escape(item.name || "A visitor")}${relationTag(item.relation)}</span><span class="rating">${stars(item.rating)}</span><time>${escape(when(item.created_at, timeZone))}</time></header>
  <p class="contact">${contactLine(item.contact)}</p>
  <p class="message">${escape(item.message)}</p>
  <footer><span class="status">${status(item, timeZone)}</span><form method="post" action="/inbox/notes/${Number(item.id)}">${buttons(item)}${back(here(page))}</form></footer>
</article>`,
    )
    .join("\n");
  return `<details class="note"><summary>How notes work</summary><p class="muted">A note is on the site as soon as it arrives, with the name, the stars and the words, never the contact. Hide takes it down without deleting it (its stars stop counting too). Pin puts a note on the site before the others, the latest pin first; hiding a note unpins it. Notes marked Private came before notes were public, without the visitor's tick: only their stars count, until you press Show on the site because the writer has said yes.</p></details>
${rows.length ? `<div class="cards">\n${cards}\n</div>` : '<p class="empty">No notes yet.</p>'}
${pager(page, more, here)}`;
}

/** A row of a bar chart: the label, a bar [share] of the widest wide, the count, and what else there is to say. */
function bar(label, count, share, detail = "") {
  const width = Math.max(0, Math.min(100, Math.round(share * 100)));
  return `<li><span class="label">${label}</span><span class="track"><i style="width:${width}%"></i></span><strong>${Number(count)}</strong><small>${detail}</small></li>`;
}

function percent(part, whole) {
  return whole > 0 ? `${Math.round((part / whole) * 100)}%` : "—";
}

/**
 * How the page was read: how far down visitors got, section by section, with how many were lost before each; how
 * long they stayed; the projects they opened; what they read on and where they came from.
 */
function readingTab(reading) {
  const readers = reading.sections[0]?.count ?? 0;
  if (!readers) {
    return `<p class="empty">Nothing counted yet for ${escape(reading.from)} to ${escape(reading.to)}. The page reports how it is read once the site and the worker with migration 0006 are both deployed (<code>npm run db:init</code>, then <code>npm run deploy</code>).</p>`;
  }
  const funnel = reading.sections
    .map((row, index) => {
      const before = index ? reading.sections[index - 1].count : row.count;
      const lost = Math.max(before - row.count, 0);
      const detail = index ? `${percent(row.count, readers)} of readers · ${lost ? `${lost} left before it (${percent(lost, before)})` : "nobody left before it"}` : "everyone who opened the page";
      return bar(escape(SECTION_LABELS[row.name] || row.name), row.count, row.count / readers, detail);
    })
    .join("");
  // Where the most readers stopped, between two sections that follow each other: the first place to look.
  const drops = reading.sections.slice(1).map((row, index) => ({ after: reading.sections[index], lost: reading.sections[index].count - row.count }));
  const worst = drops.reduce((a, b) => (b.lost > a.lost ? b : a), { lost: 0 });
  const lead = worst.lost > 0
    ? `Most readers who stopped did so in <strong>${escape(SECTION_LABELS[worst.after.name] || worst.after.name)}</strong>: ${worst.lost} of the ${worst.after.count} who got there went no further (${percent(worst.lost, worst.after.count)}).`
    : "Nobody has stopped short of the end yet.";
  const group = (title, rows, labels, base) =>
    rows.some((row) => row.count)
      ? `<section><h2>${title}</h2><ul class="bars">${rows.map((row) => bar(escape(labels?.[row.name] || row.name), row.count, row.count / Math.max(base, 1), percent(row.count, base))).join("")}</ul></section>`
      : "";
  const times = reading.times.map((row) => ({ ...row, name: Number(row.name) >= 60 ? `${Number(row.name) / 60} min or more` : `${row.name} s or more` }));
  const most = Math.max(...reading.projects.map((row) => row.count), 1);
  const projects = reading.projects.length
    ? `<section><h2>Projects opened</h2><ul class="bars">${reading.projects.slice(0, 20).map((row) => bar(escape(row.name), row.count, row.count / most, `${percent(row.count, readers)} of readers`)).join("")}</ul></section>`
    : "";
  return `<p class="muted note">${escape(reading.from)} to ${escape(reading.to)} (${Number(reading.days)} days): ${Number(readers)} readers, ${Number(reading.visits)} visits counted. A reader is counted once a day for each thing; only counts are kept, never who.</p>
<p class="lead">${lead}</p>
<section><h2>How far they read</h2><ul class="bars funnel">${funnel}</ul></section>
<div class="split">
${group("How long they stayed", times, null, readers)}
${projects}
${group("Read on", reading.devices, DEVICE_LABELS, readers)}
${group("Came from", reading.sources, SOURCE_LABELS, readers)}
${group("Read as", reading.views, VIEW_LABELS, readers)}
</div>`;
}

function visitsTab({ days, clicks, countries }) {
  const most = Math.max(...days.map((entry) => entry.count), 1);
  // Oldest first, as a chart reads; each column's height is its share of the busiest day.
  const columns = [...days]
    .reverse()
    .map((entry) => `<li title="${escape(entry.day)}: ${Number(entry.count)}"><span>${escape(entry.day)}</span><strong>${Number(entry.count)}</strong><i style="height:${Math.round((entry.count / most) * 100)}%"></i><small>${escape(String(entry.day).slice(5))}</small></li>`)
    .join("");
  const clickRows = clicks.map((row) => `<li><span>${escape(linkLabel(row.link))}</span><strong>${Number(row.today)}</strong><small>today · ${Number(row.total)} in all</small></li>`).join("");
  const top = Math.max(...countries.map((row) => row.count), 1);
  const places = countries.map((row) => bar(escape(row.country === "XX" ? "Unknown" : row.country), row.count, row.count / top)).join("");
  return `${days.length ? `<section><h2>Visitors by day</h2><ul class="columns">${columns}</ul></section>` : '<p class="empty">No visits counted yet.</p>'}
${clickRows ? `<section><h2>Link clicks</h2><ul class="tiles small">${clickRows}</ul></section>` : ""}
${places ? `<section><h2>Countries, all time</h2><ul class="bars">${places}</ul></section>` : ""}`;
}

/** Where a button was pressed, so the answer comes back to the same tab and page. */
function back(address) {
  return `<input type="hidden" name="back" value="${escape(address)}">`;
}

/** A short page with one sentence, and the way back when [backUrl] is given. */
export function renderNotice(title, sentence, backUrl) {
  const label = backUrl && backUrl.startsWith("/") ? "Back to the inbox" : "Back to the portfolio";
  const back = backUrl ? `\n<p><a href="${escape(backUrl)}">${label}</a></p>` : "";
  return pageOf(title, `<h1>${escape(title)}</h1>\n<p>${escape(sentence)}</p>${back}`);
}

/** Sent as public and not hidden: the notes the site shows, and the only ones that can be pinned. */
function onTheSite(item) {
  return Boolean(item.consent) && !item.hidden_at;
}

/**
 * Hidden by the owner, on the site (and pinned, since when), or private (sent before notes were public, without
 * the visitor's tick).
 */
function status(item, timeZone) {
  if (item.hidden_at) return `Hidden <span class="dim">${escape(when(item.hidden_at, timeZone))}</span>`;
  if (!item.consent) return '<span class="dim">Private</span>';
  const pinned = item.pinned_at ? ` · Pinned <span class="dim">${escape(when(item.pinned_at, timeZone))}</span>` : "";
  return `<strong>On the site</strong>${pinned}`;
}

/** Hide or Unhide, then Pin for a note on the site or Unpin for a pinned one, then Delete. */
function buttons(item) {
  const button = (action, label, extra = "") => `<button name="action" value="${action}"${extra}>${label}</button>`;
  const remove = button("delete", "Delete", ` class="danger" onclick="${CONFIRM_DELETE_NOTE}"`);
  const pin = item.pinned_at ? button("unpin", "Unpin") : onTheSite(item) ? button("pin", "Pin") : "";
  // A private note goes on the site only on the owner's word that its writer agreed, so the button asks for it.
  const show = item.consent
    ? ""
    : button("make-public", "Show on the site", ` onclick="${CONFIRM_MAKE_PUBLIC}"`);
  return show + (item.hidden_at ? button("unhide", "Unhide") : button("hide", "Hide")) + pin + remove;
}

/** How the writer knows the owner, beside their name; nothing when they did not say. */
function relationTag(relation) {
  return relation ? ` <span class="relation">${escape(relationLabel(relation))}</span>` : "";
}

/** Archived, read, or unread (waiting for the owner). */
function messageStatus(item, timeZone) {
  if (item.archived_at) return `Archived <span class="dim">${escape(when(item.archived_at, timeZone))}</span>`;
  if (item.read_at) return `Read <span class="dim">${escape(when(item.read_at, timeZone))}</span>`;
  return "<strong>Unread</strong>";
}

function messageButtons(item) {
  const button = (action, label, extra = "") => `<button name="action" value="${action}"${extra}>${label}</button>`;
  const read = item.read_at ? button("unread", "Mark unread") : button("read", "Mark read", ' class="primary"');
  const archive = item.archived_at ? button("unarchive", "Unarchive") : button("archive", "Archive");
  return read + archive + button("delete", "Delete", ` class="danger" onclick="${CONFIRM_DELETE_MESSAGE}"`);
}

function stars(rating) {
  if (!rating) return '<span class="dim">—</span>';
  const n = Math.min(Math.max(Number(rating), 0), 5);
  return `<span title="${n} of 5">${"★".repeat(n)}<span class="dim">${"★".repeat(5 - n)}</span></span>`;
}

/**
 * The contact as a mail link when it reads as an address, else as text. The link's address is percent-encoded (all
 * but its "@"), so a contact written as `a@b.c?bcc=…&body=…` opens a draft to that odd address, never one with the
 * visitor's choice of extra recipients or words.
 */
function contactLine(contact) {
  if (!contact) return '<span class="dim">No contact left</span>';
  const safe = escape(contact);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact)) return safe;
  const address = escape(encodeURIComponent(contact).replace(/%40/g, "@"));
  return `<a href="mailto:${address}">${safe}</a>`;
}

/** "Reply", a mail draft to the sender with a subject already on it, when the contact reads as an address. */
function replyLink(contact) {
  if (!contact || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact)) return "";
  const address = escape(encodeURIComponent(contact).replace(/%40/g, "@"));
  return `<a class="button" href="mailto:${address}?subject=${encodeURIComponent("Re: your message on my portfolio")}">Reply</a>`;
}

const clocks = new Map();

/** "2026-10-24T10:05:33.000Z" → "2026-10-24 18:05" in [timeZone], the owner's: the day the counts use, and their clock. */
function when(iso, timeZone = "UTC") {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return String(iso);
  let clock = clocks.get(timeZone);
  if (!clock) {
    clock = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    clocks.set(timeZone, clock);
  }
  const part = Object.fromEntries(clock.formatToParts(date).map(({ type, value }) => [type, value]));
  return `${part.year}-${part.month}-${part.day} ${part.hour}:${part.minute}`;
}

function pageOf(title, body) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>${escape(title)} · AboutYowkey</title>
<style>
  :root { color-scheme: light dark; --bg: #FAFAFA; --surface: #FFFFFF; --ink: #09090B; --muted: #475569; --line: #E4E4E7; --accent: #2563EB; --on-accent: #FFFFFF; --soft: #EFF6FF; --danger: #B3261E; }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #101216; --surface: #181B21; --ink: #E8E8EA; --muted: #A1A1AA; --line: #2C3038; --accent: #60A5FA; --on-accent: #0B1D3F; --soft: #172554; --danger: #F2B8B5; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px 16px 80px; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 960px; margin: 0 auto; }
  h1 { font-size: 28px; letter-spacing: -0.02em; margin: 0; }
  h2 { font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); margin: 28px 0 10px; }
  a { color: var(--accent); font-weight: 600; }
  a:focus-visible, button:focus-visible, summary:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
  code { font: 600 14px ui-monospace, SFMono-Regular, Consolas, monospace; }
  .muted, .dim { color: var(--muted); }
  .dim { font-weight: 400; }
  .top { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 4px 16px; }
  .top p { margin: 0; font-size: 14px; }
  .tiles { list-style: none; margin: 16px 0 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
  .tiles li { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 12px 14px; display: grid; gap: 2px; }
  .tiles span { font-size: 13px; color: var(--muted); }
  .tiles strong { font-size: 26px; line-height: 1.1; font-variant-numeric: tabular-nums; }
  .tiles small { font-size: 13px; color: var(--muted); }
  .tiles.small { margin-top: 0; }
  .tabs { position: sticky; top: 0; z-index: 1; display: flex; gap: 4px; margin: 20px -16px 0; padding: 8px 16px; background: var(--bg); border-bottom: 1px solid var(--line); overflow-x: auto; }
  .tabs a, .filter a { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 14px; border-radius: 999px; color: var(--muted); text-decoration: none; white-space: nowrap; }
  .tabs a[aria-current], .filter a[aria-current] { color: var(--accent); background: var(--soft); }
  .count { font-size: 12px; font-weight: 700; padding: 1px 7px; border-radius: 999px; background: var(--accent); color: var(--on-accent); }
  .filter .count { background: transparent; color: inherit; border: 1px solid var(--line); }
  .filter { display: flex; gap: 4px; margin: 12px 0; }
  .note { font-size: 14px; margin: 16px 0 0; }
  details.note summary { cursor: pointer; color: var(--accent); font-weight: 600; width: fit-content; min-height: 32px; }
  details.note p { margin: 4px 0 0; max-width: 70ch; }
  .lead { margin: 12px 0 0; padding: 12px 14px; border-radius: 12px; background: var(--soft); max-width: 70ch; }
  .empty { margin: 24px 0; padding: 24px 16px; text-align: center; color: var(--muted); border: 1px dashed var(--line); border-radius: 12px; }
  .cards { display: grid; gap: 10px; margin-top: 12px; }
  .card { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; scroll-margin-top: 72px; }
  .card.unread { border-left: 4px solid var(--accent); padding-left: 13px; }
  .card.hidden { opacity: .7; }
  .card:target { outline: 2px solid var(--accent); outline-offset: 2px; }
  .card header { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 10px; }
  .card .who { font-weight: 700; overflow-wrap: anywhere; }
  .card time { margin-left: auto; font-size: 13px; color: var(--muted); white-space: nowrap; font-variant-numeric: tabular-nums; }
  .card p { margin: 0; }
  .card .contact { font-size: 14px; overflow-wrap: anywhere; }
  .card .message { margin-top: 8px; white-space: pre-wrap; overflow-wrap: anywhere; max-width: 75ch; }
  .card footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px 12px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--line); }
  .card .status { font-size: 13px; color: var(--muted); }
  .card .status strong { color: var(--accent); }
  .card form, .card .acts { display: flex; flex-wrap: wrap; gap: 6px; }
  a.button { display: inline-flex; align-items: center; min-height: 36px; padding: 0 12px; border: 1px solid var(--accent); border-radius: 8px; font-size: 14px; text-decoration: none; }
  .topic, .relation { font-size: 12px; font-weight: 600; color: var(--accent); border: 1px solid var(--line); border-radius: 999px; padding: 1px 8px; white-space: nowrap; }
  .rating { color: var(--accent); letter-spacing: .05em; white-space: nowrap; }
  .rating .dim { opacity: .35; }
  button { font: inherit; font-size: 14px; font-weight: 600; min-height: 36px; color: var(--ink); background: var(--bg); border: 1px solid var(--line); border-radius: 8px; padding: 0 12px; cursor: pointer; }
  button.primary { color: var(--on-accent); background: var(--accent); border-color: var(--accent); }
  button.danger { color: var(--danger); }
  .pager { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; margin-top: 16px; font-size: 14px; }
  .pager a:last-child, .pager span:last-child { justify-self: end; }
  .bars { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
  .bars li { display: grid; grid-template-columns: minmax(96px, 150px) 1fr 44px; align-items: center; gap: 2px 10px; font-size: 14px; }
  .bars .label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .bars .track { height: 12px; border-radius: 999px; background: var(--line); overflow: hidden; }
  .bars i { display: block; height: 100%; border-radius: 999px; background: var(--accent); }
  .bars strong { text-align: right; font-variant-numeric: tabular-nums; }
  .bars small { grid-column: 2 / -1; color: var(--muted); font-size: 12px; }
  .bars small:empty { display: none; }
  .split { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 0 32px; }
  .columns { list-style: none; margin: 0; padding: 12px 12px 8px; display: flex; align-items: flex-end; gap: 3px; height: 180px; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; overflow-x: auto; }
  .columns li { position: relative; flex: 1 1 0; min-width: 0; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 2px; }
  .columns li > span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  .columns strong { font-size: 11px; font-variant-numeric: tabular-nums; }
  .columns i { display: block; width: 100%; min-height: 2px; border-radius: 4px 4px 0 0; background: var(--accent); }
  .columns small { font-size: 10px; color: var(--muted); white-space: nowrap; writing-mode: vertical-rl; }
  @media (max-width: 600px) {
    .tabs { gap: 0; }
    .tabs a { padding: 0 11px; }
    /* A phone has room for two weeks of columns: the latest fourteen. */
    .columns li:nth-last-child(n+15) { display: none; }
  }
</style>
</head>
<body>
<main>
${body}
</main>
</body>
</html>
`;
}

export function escape(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
