#!/usr/bin/env python3
"""
The résumé recruiters download: a PDF made from portfolio.json, so it says what the site says and nothing else.

    python3 scripts/make_resume.py

Writes composeApp/src/wasmJsMain/resources/<Name>-Resume.pdf, which ships at the site's root; portfolio.json's
"links" has the "Résumé" entry that points at it. Run it again after editing portfolio.json. To ship a résumé
written by hand instead, save it over that file: nothing else needs to change.

Every word comes from portfolio.json: the profile, the experience, each personal project's title and tagline (work
is told under Experience, as its own bullets), the skills and the education and awards. It carries no phone number,
because the site has none. Set in Arial, or the system's stand-in for it, so the PDF embeds an ordinary TrueType
font that résumé parsers read without trouble.

It needs Chrome or Chromium to print the page (set CHROME to its path if it is not found), and nothing else.
"""
import html
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "composeApp/src/commonMain/composeResources/files/portfolio.json"
SITE_FILES = ROOT / "composeApp/src/wasmJsMain/resources"

BROWSERS = [
    "google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/opt/pw-browsers/chromium",
]

STYLE = """
@page { size: A4; margin: 11mm 15mm; }
* { box-sizing: border-box; }
html { font: 9.2pt/1.42 Arial, "Liberation Sans", Helvetica, sans-serif; color: #09090B; }
body { margin: 0; }
a { color: inherit; text-decoration: none; }
h1 { font-size: 21pt; line-height: 1.1; letter-spacing: -.02em; margin: 0; }
.role { font-size: 10.5pt; font-weight: 500; margin: 3pt 0 0; }
.reach { color: #475569; margin: 4pt 0 0; display: flex; flex-wrap: wrap; gap: 0 16pt; }
.reach span { white-space: nowrap; }
.reach a { color: #1D4ED8; }
.summary { margin: 8pt 0 0; }
h2 {
  font-size: 8pt; letter-spacing: .09em; text-transform: uppercase; color: #1D4ED8; font-weight: 700;
  margin: 10pt 0 4pt; padding-bottom: 2.5pt; border-bottom: .6pt solid #D4D4D8; break-after: avoid;
}
.job, .entry { break-inside: avoid; }
.job + .job { margin-top: 6pt; }
.line { display: flex; justify-content: space-between; gap: 12pt; align-items: baseline; }
.line strong, .entry strong, .skills dt { font-weight: 700; }
.where { color: #475569; }
.when { color: #475569; white-space: nowrap; }
ul { margin: 2pt 0 0; padding-left: 11pt; }
li { margin: 0 0 1.2pt; }
.entry { margin: 0 0 2.4pt; }
.entry .more { color: #475569; }
.skills { display: grid; grid-template-columns: max-content 1fr; gap: 1.6pt 10pt; margin: 0; }
.skills dd { margin: 0; }
"""


def esc(text: str) -> str:
    return html.escape(text, quote=True)


def shown(url: str) -> str:
    """A link as it reads on paper: no scheme, no trailing slash."""
    return url.removeprefix("https://").removeprefix("www.").rstrip("/")


def page(content: dict) -> str:
    profile = content["profile"]
    site = content["siteUrl"]
    own = site.rstrip("/") + "/"
    where = " · ".join(part for part in (profile.get("location"), profile.get("availability")) if part)
    reach = [esc(where)] if where else []
    if profile.get("email"):
        reach.append(f'<a href="mailto:{esc(profile["email"])}">{esc(profile["email"])}</a>')
    # The profiles, not the site's own files (this résumé is one of them).
    reach += [f'<a href="{esc(link["url"])}">{esc(shown(link["url"]))}</a>' for link in content.get("links", []) if not link["url"].startswith(own)]
    reach.append(f'<a href="{esc(site)}">{esc(shown(site))}</a>')

    out = [f"<h1>{esc(profile['name'])}</h1>", f'<p class="role">{esc(profile["headline"])}</p>']
    out.append('<p class="reach">' + "".join(f"<span>{item}</span>" for item in reach) + "</p>")
    out.append(f'<p class="summary">{esc(profile["summary"])}</p>')

    if content.get("experience"):
        out.append("<h2>Experience</h2>")
        for job in content["experience"]:
            place = f' <span class="where">· {esc(job["location"])}</span>' if job.get("location") else ""
            out.append('<div class="job">')
            out.append(f'<div class="line"><span><strong>{esc(job["role"])}</strong> · {esc(job["company"])}{place}</span>'
                       f'<span class="when">{esc(job["period"])}</span></div>')
            out.append("<ul>" + "".join(f"<li>{esc(line)}</li>" for line in job.get("highlights", [])) + "</ul></div>")

    # A project with a company is work, and work is already told above, in the job's own bullets.
    personal = [project for project in content.get("projects", []) if not project.get("company")]
    if personal:
        out.append("<h2>Personal projects</h2>")
        for project in personal:
            line = project.get("tagline") or project["summary"]
            links = [f'<a href="{esc(link["url"])}">{esc(shown(link["url"]))}</a>' for link in project.get("links", [])]
            more = ", ".join(([esc(project["period"])] if project.get("period") else []) + links)
            more = f' <span class="more">({more})</span>' if more else ""
            out.append(f'<p class="entry"><strong>{esc(project["title"])}</strong>: {esc(line)}{more}</p>')

    if content.get("skills"):
        out.append('<h2>Skills</h2><dl class="skills">')
        for group in content["skills"]:
            out.append(f"<dt>{esc(group['name'])}</dt><dd>{esc(', '.join(group['items']))}</dd>")
        out.append("</dl>")

    if content.get("recognition"):
        out.append("<h2>Education &amp; awards</h2>")
        for award in content["recognition"]:
            date = f' <span class="more">({esc(award["date"])})</span>' if award.get("date") else ""
            detail = f': {esc(award["detail"])}' if award.get("detail") else ""
            out.append(f'<p class="entry"><strong>{esc(award["title"])}</strong>{date}{detail}</p>')

    title = f"{profile['name']} — Résumé"
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><title>{esc(title)}</title>'
            f"<style>{STYLE}</style></head><body>{''.join(out)}</body></html>")


def browser() -> str:
    for name in filter(None, [os.environ.get("CHROME"), *BROWSERS]):
        found = shutil.which(name) or (name if Path(name).is_file() else None)
        if found:
            return found
    raise SystemExit("No Chrome or Chromium found: set CHROME to its path.")


def target(content: dict) -> Path:
    """Where the content's own "links" say the résumé is; so the link and the file cannot drift apart."""
    own = content["siteUrl"].rstrip("/") + "/"
    files = [link["url"][len(own):] for link in content.get("links", []) if link["url"].startswith(own) and link["url"].lower().endswith(".pdf")]
    if len(files) != 1 or "/" in files[0]:
        raise SystemExit(f'portfolio.json\'s "links" must have exactly one PDF at the site\'s root ({own}<name>.pdf), found {files}')
    return SITE_FILES / files[0]


def main() -> None:
    content = json.loads(CONTENT.read_text(encoding="utf-8"))
    pdf = target(content)
    with tempfile.TemporaryDirectory() as scratch:
        source = Path(scratch) / "resume.html"
        source.write_text(page(content), encoding="utf-8")
        subprocess.run(
            [browser(), "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer", f"--print-to-pdf={pdf}", source.as_uri()],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    print(f"{pdf.relative_to(ROOT)}: {pdf.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
