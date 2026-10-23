/**
 * The owner's pages, in the site's own colours: the inbox, and the one-paragraph notices a plain form post gets
 * back. Every value from the database or the request goes through escape().
 */

export function renderInbox({ visits, items, days, today }) {
  const rows = items
    .map(
      (item) => `<tr>
  <td class="when">${escape(when(item.created_at))}</td>
  <td class="rating">${stars(item.rating)}</td>
  <td>${escape(item.name || "")}${contactLine(item.contact)}</td>
  <td class="message">${escape(item.message)}</td>
</tr>`,
    )
    .join("\n");
  const series = days.map((entry) => `<li><span>${escape(entry.day)}</span><strong>${Number(entry.count)}</strong></li>`).join("");
  const since = visits.since ? ` since ${escape(visits.since)}` : "";
  return page(
    "Inbox",
    `<h1>Inbox</h1>
<p class="muted">${Number(visits.total)} visits${since} · ${Number(visits.today)} today (${escape(today)}, UTC) · ${items.length} notes</p>
${days.length ? `<h2>Visitors by day</h2>\n<ul class="days">${series}</ul>` : ""}
<h2>Notes</h2>
${
  items.length
    ? `<table>
<thead><tr><th>When</th><th>Rating</th><th>From</th><th>Message</th></tr></thead>
<tbody>
${rows}
</tbody>
</table>`
    : "<p>No notes yet.</p>"
}`,
  );
}

/** A short page with one sentence, and the way back to the portfolio when [backUrl] is given. */
export function renderNotice(title, sentence, backUrl) {
  const back = backUrl ? `\n<p><a href="${escape(backUrl)}">Back to the portfolio</a></p>` : "";
  return page(title, `<h1>${escape(title)}</h1>\n<p>${escape(sentence)}</p>${back}`);
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
  :root { --bg: #F7F5F0; --surface: #FFFFFF; --ink: #16181D; --muted: #545A66; --line: #E4E0D7; --accent: #C2410C; }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #0E1014; --surface: #161A20; --ink: #ECEDEF; --muted: #9AA1AD; --line: #262B33; --accent: #FB923C; }
  }
  body { margin: 0; padding: 32px 20px 80px; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 1120px; margin: 0 auto; }
  h1 { font-size: 32px; letter-spacing: -0.02em; margin: 0 0 8px; }
  h2 { font-size: 14px; letter-spacing: .1em; text-transform: uppercase; color: var(--accent); margin: 32px 0 12px; }
  a { color: var(--accent); font-weight: 600; }
  .muted, .dim { color: var(--muted); }
  .dim { opacity: .45; }
  .days { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
  .days li { background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 8px 12px; font-size: 14px; display: grid; }
  .days strong { font-size: 20px; color: var(--accent); }
  table { width: 100%; border-collapse: collapse; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
  th, td { text-align: left; vertical-align: top; padding: 12px 14px; border-top: 1px solid var(--line); font-size: 15px; }
  th { font-size: 12px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); border-top: 0; }
  td.when { white-space: nowrap; color: var(--muted); }
  td.rating { white-space: nowrap; color: var(--accent); letter-spacing: .05em; }
  td.message { white-space: pre-wrap; overflow-wrap: anywhere; width: 55%; }
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
