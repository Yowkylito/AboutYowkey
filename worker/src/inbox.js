/**
 * The owner's pages, in the site's own colours: the inbox, with a button for everything that can be done to a
 * message or a note, and the one-paragraph notices a plain form post gets back. Every value from the database or
 * the request goes through escape().
 */

/** The names the owner reads, on this page and in the alerts on the phone. */
const TOPIC_LABELS = { job: "Job", freelance: "Freelance", collaboration: "Collaboration", other: "Other" };
const LINK_LABELS = { linkedin: "LinkedIn", github: "GitHub" };

export function topicLabel(topic) {
  return TOPIC_LABELS[topic] || "Other";
}

export function linkLabel(link) {
  return LINK_LABELS[link] || link.charAt(0).toUpperCase() + link.slice(1);
}

export function renderInbox({ visits, items, days, today, timeZone, messages = [], clicks = {} }) {
  const waiting = items.filter((item) => item.consent && !item.published_at && !item.hidden_at).length;
  const unread = messages.filter((item) => !item.read_at && !item.archived_at).length;
  const rows = items
    .map(
      (item) => `<tr${item.hidden_at ? ' class="hidden"' : ""}>
  <td class="when">${escape(when(item.created_at))}</td>
  <td class="rating">${stars(item.rating)}</td>
  <td>${escape(item.name || "")}${contactLine(item.contact)}</td>
  <td class="message">${escape(item.message)}</td>
  <td class="status">${status(item)}</td>
  <td class="actions"><form method="post" action="/inbox/notes/${Number(item.id)}">${buttons(item)}</form></td>
</tr>`,
    )
    .join("\n");
  const messageRows = messages
    .map(
      (item) => `<tr${item.archived_at ? ' class="hidden"' : ""}>
  <td class="when">${escape(when(item.created_at))}</td>
  <td class="topic">${escape(topicLabel(item.topic))}</td>
  <td>${escape(item.name || "")}${contactLine(item.contact)}</td>
  <td class="message">${escape(item.message)}</td>
  <td class="status">${messageStatus(item)}</td>
  <td class="actions"><form method="post" action="/inbox/messages/${Number(item.id)}">${messageButtons(item)}</form></td>
</tr>`,
    )
    .join("\n");
  const series = days.map((entry) => `<li><span>${escape(entry.day)}</span><strong>${Number(entry.count)}</strong></li>`).join("");
  const clickSeries = Object.entries(clicks)
    .map(([link, count]) => `<li><span>${escape(linkLabel(link))}</span><strong>${Number(count)}</strong></li>`)
    .join("");
  const since = visits.since ? ` since ${escape(visits.since)}` : "";
  return page(
    "Inbox",
    `<h1>Inbox</h1>
<p class="muted">${Number(visits.total)} visits${since} · ${Number(visits.today)} today (${escape(today)}, ${escape(timeZone)}) · ${items.length} notes, ${waiting} waiting · ${messages.length} messages, ${unread} unread</p>
<p class="muted">A note with the visitor's tick can be published; it then shows on the site with the name, the stars and the words, never the contact. Hide takes it down without deleting it. Messages are private: they are never on the site.</p>
${days.length ? `<h2>Visitors by day</h2>\n<ul class="days">${series}</ul>` : ""}
${clickSeries ? `<h2>Link clicks today</h2>\n<ul class="days">${clickSeries}</ul>` : ""}
<h2>Messages</h2>
${
  messages.length
    ? `<table>
<thead><tr><th>When</th><th>Topic</th><th>From</th><th>Message</th><th>Status</th><th></th></tr></thead>
<tbody>
${messageRows}
</tbody>
</table>`
    : "<p>No messages yet.</p>"
}
<h2>Notes</h2>
${
  items.length
    ? `<table>
<thead><tr><th>When</th><th>Rating</th><th>From</th><th>Message</th><th>Status</th><th></th></tr></thead>
<tbody>
${rows}
</tbody>
</table>`
    : "<p>No notes yet.</p>"
}`,
  );
}

/** A short page with one sentence, and the way back when [backUrl] is given. */
export function renderNotice(title, sentence, backUrl) {
  const label = backUrl && backUrl.startsWith("/") ? "Back to the inbox" : "Back to the portfolio";
  const back = backUrl ? `\n<p><a href="${escape(backUrl)}">${label}</a></p>` : "";
  return page(title, `<h1>${escape(title)}</h1>\n<p>${escape(sentence)}</p>${back}`);
}

/** Published, hidden, waiting for the owner, or private (the visitor did not tick the box). */
function status(item) {
  if (item.hidden_at) return `Hidden<br><span class="dim">${escape(when(item.hidden_at))}</span>`;
  if (item.published_at) return `Published<br><span class="dim">${escape(when(item.published_at))}</span>`;
  return item.consent ? "<strong>Waiting</strong>" : '<span class="dim">Private</span>';
}

function buttons(item) {
  const button = (action, label, extra = "") => `<button name="action" value="${action}"${extra}>${label}</button>`;
  const remove = button("delete", "Delete", ' class="danger" onclick="return confirm(\'Delete this note for good?\')"');
  if (item.hidden_at) return button("unhide", "Unhide") + remove;
  if (item.published_at) return button("unpublish", "Unpublish") + button("hide", "Hide") + remove;
  const publish = item.consent ? button("publish", "Publish", ' class="primary"') : button("publish", "Publish", ' disabled title="No consent from the visitor"');
  return publish + button("hide", "Hide") + remove;
}

/** Archived, read, or unread (waiting for the owner). */
function messageStatus(item) {
  if (item.archived_at) return `Archived<br><span class="dim">${escape(when(item.archived_at))}</span>`;
  if (item.read_at) return `Read<br><span class="dim">${escape(when(item.read_at))}</span>`;
  return "<strong>Unread</strong>";
}

function messageButtons(item) {
  const button = (action, label, extra = "") => `<button name="action" value="${action}"${extra}>${label}</button>`;
  const read = item.read_at ? button("unread", "Mark unread") : button("read", "Mark read", ' class="primary"');
  const archive = item.archived_at ? button("unarchive", "Unarchive") : button("archive", "Archive");
  return read + archive + button("delete", "Delete", ' class="danger" onclick="return confirm(\'Delete this message for good?\')"');
}

function stars(rating) {
  if (!rating) return '<span class="dim">—</span>';
  const n = Math.min(Math.max(Number(rating), 0), 5);
  return `<span title="${n} of 5">${"★".repeat(n)}<span class="dim">${"★".repeat(5 - n)}</span></span>`;
}

/** The contact as a mail link when it reads as an address, else as text. */
function contactLine(contact) {
  if (!contact) return "";
  const safe = escape(contact);
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact) ? `<br><a href="mailto:${safe}">${safe}</a>` : `<br>${safe}`;
}

/** "2026-10-24T10:05:33.000Z" → "2026-10-24 10:05 UTC". */
function when(iso) {
  return `${String(iso).slice(0, 16).replace("T", " ")} UTC`;
}

function page(title, body) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>${escape(title)} · AboutYowkey</title>
<style>
  :root { --bg: #F7F5F0; --surface: #FFFFFF; --ink: #16181D; --muted: #545A66; --line: #E4E0D7; --accent: #C2410C; --on-accent: #FFFFFF; --danger: #B3261E; }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #0E1014; --surface: #161A20; --ink: #ECEDEF; --muted: #9AA1AD; --line: #262B33; --accent: #FB923C; --on-accent: #1A0E05; --danger: #F2B8B5; }
  }
  body { margin: 0; padding: 32px 20px 80px; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 1200px; margin: 0 auto; }
  h1 { font-size: 32px; letter-spacing: -0.02em; margin: 0 0 8px; }
  h2 { font-size: 14px; letter-spacing: .1em; text-transform: uppercase; color: var(--accent); margin: 32px 0 12px; }
  a { color: var(--accent); font-weight: 600; }
  .muted, .dim { color: var(--muted); }
  .dim { opacity: .6; }
  .days { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
  .days li { background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 8px 12px; font-size: 14px; display: grid; }
  .days strong { font-size: 20px; color: var(--accent); }
  table { width: 100%; border-collapse: collapse; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
  th, td { text-align: left; vertical-align: top; padding: 12px 14px; border-top: 1px solid var(--line); font-size: 15px; }
  th { font-size: 12px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); border-top: 0; }
  tr.hidden td { opacity: .55; }
  td.when, td.status { white-space: nowrap; color: var(--muted); font-size: 14px; }
  td.status strong { color: var(--accent); }
  td.rating { white-space: nowrap; color: var(--accent); letter-spacing: .05em; }
  td.topic { white-space: nowrap; font-weight: 600; }
  td.message { white-space: pre-wrap; overflow-wrap: anywhere; width: 45%; }
  td.actions form { display: flex; flex-direction: column; gap: 6px; }
  button { font: inherit; font-size: 13px; font-weight: 600; color: var(--ink); background: var(--bg); border: 1px solid var(--line); border-radius: 8px; padding: 6px 10px; cursor: pointer; }
  button.primary { color: var(--on-accent); background: var(--accent); border-color: var(--accent); }
  button.danger { color: var(--danger); }
  button:disabled { opacity: .5; cursor: default; }
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
