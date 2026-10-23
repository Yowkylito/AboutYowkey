# AboutYowkey

My portfolio, built as a **Compose Multiplatform** app and served from GitHub Pages as Kotlin/WebAssembly.

**Live:** https://yowkylito.github.io/AboutYowkey/

The same Kotlin UI also runs as a desktop app on the JVM. Compose Multiplatform's web target is **Beta**.

## Editing the content

Everything on the page comes from one file:
[`composeApp/src/commonMain/composeResources/files/portfolio.json`](composeApp/src/commonMain/composeResources/files/portfolio.json).
Change it, push to `main`, and the site redeploys. A section with no entries disappears, nav link included.

Rules the build enforces (it fails rather than ship a broken page):

- `profile.name` is not blank; `siteUrl` is `https://`
- project `id`s are unique and plain (`a-z`, `0-9`, `-`): they key the list rows and are the project's link,
  `#/projects/<id>`
- links are `https://` or `mailto:`
- `api`, if given, is `https://` with no trailing slash: the site's backend (see "Visits and feedback")

### Adding screenshots to a project

```bash
python scripts/prepare_screenshots.py path/to/raw.png vistago-planner path/to/other.png vistago-translate
```

This writes `<name>.webp` (full size, for the viewer) and `<name>.thumb.webp` (for the card) into
`composeResources/files/screens/` within size budgets, and prints the `file`, `width` and `height` to put in the
project's `screenshots` list with a `caption` (the caption is also the image's alt text). Tests fail if a listed
image is missing or over budget, or if a file in `screens/` is not listed.

### A cover, a highlight clip, and the repo badge

```bash
python scripts/prepare_screenshots.py --cover path/to/splash.png vistago-cover
python scripts/prepare_screenshots.py --clip path/to/demo.mp4 vistago-trip --start 12 --duration 4.5
python scripts/prepare_screenshots.py --clip path/to/png-frames/ vistago-trip --fps 30
python scripts/prepare_screenshots.py --clip path/to/60fps-demo.mp4 vistago-trip --video --start 12 --duration 4.5
```

- **Cover**: the splash screen shown large at the top of the card (full card width when landscape, a 320dp phone
  frame when portrait). Same files as a screenshot, with a bigger `.thumb.webp` for the card.
- **Clip**: a short animated WebP (at most 6 s, 12 fps, 720 px on the long edge, 700 KB) plus a `.poster.webp`
  still (its first frame) for the card. The source is an mp4 (ffmpeg must be on PATH; pick the stretch with
  `--start`/`--duration`, default the first 5 s) or a folder of PNG frames. If the clip is over budget the script
  makes it smaller rather than blurrier, so pick a calm stretch, or a shorter one, to keep it sharp. The card shows
  the poster with a play mark; the viewer plays it in a loop (a still under reduced motion); the static page
  animates it natively.
- **Clip video** (`"video": true` on a clip or the cover clip, optional): add `--video` and the script also writes
  `<name>.mp4`, H.264 at 60 fps (long edge 1440 px, silent, at most 1536 KB). It needs a 60 fps recording (the
  Mac's and most phones' screen recorders make one); slower sources are refused. On the web the viewer, the card
  covers and the detail page's hero play the video instead of the WebP, through the browser's video decoder; the
  desktop app and the static page keep the WebP. Under reduced motion the cards and the hero stay on the WebP (the
  hero's Play button sits over the clip), and the viewer shows the poster until Play, where a tap on the playing
  video pauses it (the video is drawn above the Play button).
- **Visibility**: `"public"` or `"private"`. The card shows a "Public repo" / "Private repo" badge, and if any
  project is private a note under the Projects header says why its source is not linked. Leave it out for no badge.
- **Disclaimer** (`disclaimer`, optional): for an app built around a real business that is not theirs, one
  sentence of at most 40 words saying so. The card and the detail screen get an "Unofficial concept" badge, and the
  detail screen and the static page show the sentence under the summary.

- **Cover clip** (`coverClip`, optional): an animated WebP that plays as the card's cover while the card is on
  screen. Same files and budgets as a clip (make it with `--clip`); `cover` stays the still (the static page's
  fallback and the viewer's first page). Pick a calm stretch: a clip whose frames repaint the whole screen decodes
  too slowly for a card (see READINESS.md, "Cover clips").

The viewer pages through cover → cover clip → clips → screenshots, in that order.

```json
{
  "id": "vistago",
  "visibility": "private",
  "cover": { "file": "vistago-cover.webp", "caption": "…", "width": 739, "height": 1600 },
  "clips": [{ "file": "vistago-trip.webp", "caption": "…", "width": 720, "height": 405 }],
  "screenshots": [{ "file": "vistago-ask.webp", "caption": "…", "width": 739, "height": 1600 }]
}
```

### The app icon

```bash
cwebp -q 88 -resize 192 192 path/to/icon-512.png -o composeApp/src/commonMain/composeResources/files/icons/my-app.webp
```

`"icon": "my-app.webp"` puts the app's launcher icon before its title on the card, the detail screen and the static
page, rounded, with a hairline so a white or near-black icon keeps its edge on either theme. Use the icon the app
really ships, square and full bleed (the page rounds the corners); for an Android adaptive icon, that is its
background and foreground layers cropped to the inner 72 of 108 dp. Leave it out when the app has no icon of its own
(Android Studio's default robot doesn't count): the title then stands alone. Tests fail if an icon is missing, not
square, under 120 px, over 16 KB, or in `files/icons/` but not listed.

### Work and personal projects

The Projects section has two groups: **Professional work** first, then **Personal projects**. A project is work
when it has a `company`. Work code belongs to the employer, so a work entry shows no repo badge or media and is told
through what I did and what it changed instead:

```json
{
  "id": "arenaplus",
  "company": "DigiPlus",
  "role": "Android developer",
  "period": "Sep 2024 – Present",
  "figures": [{ "value": "53%", "label": "of V2's code, written by me" }],
  "impact": ["What it changed for the company, one line"],
  "highlights": ["My contributions, one line each"],
  "optimizations": ["Performance, security and release audits and optimizations, one line each"]
}
```

The work card leads with the figures where a personal card has its cover; the detail screen reads figures, Impact,
My contributions, Optimizations and audits, then How it's built. Name products and public libraries, never internal
tools or systems: describe what a tool does instead.

| Field | Rule |
|---|---|
| `company` | not blank; only work takes `figures`, `impact` and `optimizations` |
| `figures` | at most 3; `value` at most 7 characters, `label` at most 60 |
| `impact` | at most 4 lines, each at most 40 words |
| `optimizations` | at most 6 lines, each at most 40 words |

### The card and the detail screen

The card answers three questions at a glance: the cover, the title with its period and badge, one line
(`tagline`, or the summary cut to 90 characters), and chips for what it is built with and where it runs. A tap
opens the project's own screen (`#/projects/<id>`, linkable, Back returns to the list where it was) with the
highlights, how it is built, the gallery, the full stack and the links.

```json
{
  "id": "my-app",
  "tagline": "What it is, in one line a recruiter reads in two seconds",
  "coverClip": { "file": "my-app-tour.webp", "caption": "…", "width": 332, "height": 720 },
  "engineering": {
    "builtWith": ["Kotlin Multiplatform", "Compose Multiplatform"],
    "platforms": ["Android", "iOS", "Web"],
    "architecture": "One paragraph.",
    "decisions": [{ "title": "Offline first", "detail": "Why, in a sentence or two." }],
    "modules": [{ "name": ":core:data", "role": "Repositories and sync", "dependsOn": [":core:model"] }],
    "quality": ["412 unit tests", "CI on GitHub Actions"]
  }
}
```

Every field is optional. The build holds them to what the card and the detail screen have room for:

| Field | Rule |
|---|---|
| `tagline` | at most 90 characters |
| `builtWith` | at most 4; shown as chips on the card. Without it the card shows the first 4 of `stack` |
| `platforms` | at most 5, targets that actually ship; each gets a drawn glyph (Android, iOS, Web, Desktop/macOS/Windows/Linux, Server, or a dot) |
| `architecture` | one paragraph, at most 70 words |
| `decisions` | 2 to 4, each `detail` at most 40 words |
| `modules` | at most 12; `role` at most 10 words; `dependsOn` names listed modules, with no cycles. Drawn as a layered diagram |
| `quality` | at most 4 verifiable facts |

## How the page loads

A Compose app on the web draws into a `<canvas>`, which search engines, link previews and screen readers can't read,
and its runtime is several megabytes. So the build also renders `portfolio.json` into plain HTML inside
`index.html` (see `composeApp/web/index.template.html`):

1. The browser shows that HTML straight away. It is a complete, styled version of the page.
2. If the browser supports WasmGC, the Compose app loads behind it, invisible.
3. Once Compose has drawn real content, the page swaps to it.

Browsers without WasmGC, or a failed download, just keep the HTML version. It carries everything the detail
screens do, one `<article id="project-<id>">` per project, and a `#/projects/<id>` link scrolls to that article
there too.

## Run it

JDK 17+ (Android Studio's bundled JBR works).

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, with live reload
./gradlew :composeApp:run                           # desktop window
./gradlew :composeApp:jvmTest                       # tests
./gradlew :composeApp:wasmJsBrowserDistribution     # the bundle that ships, in composeApp/build/dist/wasmJs/productionExecutable
```

## Deploy

`.github/workflows/pages.yml` scans for secrets, runs the tests, builds the bundle and deploys it on every push to
`main`, then runs Lighthouse against the live URL. Pull requests get the same build and tests without the deploy.
One-time setup: **Settings → Pages → Source: GitHub Actions**.

## Visits and feedback

The live line under the hero's headline (the published notes' rating, the visit count), the "Notes from visitors"
cards and the "Leave a note" form under Contact are real data, kept by a small backend of the site's own: a
Cloudflare Worker with a D1 (SQLite) database, in [`worker/`](worker/). GitHub Pages serves files only, so this is
the one piece that runs on a server. Cloudflare's free plan covers it many times over (100,000 requests a day), and
it never sleeps.

What it keeps, and nothing more:

- **Visits**: one count per day. A visitor is counted once a day, told apart by a hash of the day, their address,
  their browser and a secret salt; the address itself is never stored, and the hashes are deleted the next day.
  Crawlers, link previews and CI's Lighthouse run read the count but are not counted.
- **Notes**: what the form says (name and email are optional), the rating, the time, whether the visitor ticked
  "You may show this note on the site", and the day's hash for a limit of 5 notes per visitor per day. A hidden
  field catches bots that fill every field.
- **What the page shows**: only notes the visitor agreed to show *and* you published from the inbox, with the
  name (or "A visitor"), the stars and the words, never the email. The hero's rating is the mean of those notes'
  stars. Hiding a note takes it down again; deleting it removes it for good.

Until `portfolio.json` names the backend, neither the count nor the form is on the page. Setting it up takes about
ten minutes, once:

1. A free [Cloudflare account](https://dash.cloudflare.com/sign-up), and Node 22 or newer. Then:

   ```bash
   cd worker && npm install && npx wrangler login
   ```

2. Create the database, paste the `database_id` it prints into `worker/wrangler.toml`, and create the tables
   (`db:init` applies `worker/migrations/` in order and remembers which ones have run, so it is also the command
   after a new migration):

   ```bash
   npx wrangler d1 create aboutyowkey
   ```

   ```bash
   npm run db:init
   ```

3. Two secrets: the inbox password, and the salt for the visitor hashes. Long and random, both (`openssl rand
   -base64 32` makes one). Wrangler asks for the value, so neither is ever in a file:

   ```bash
   npx wrangler secret put ADMIN_TOKEN
   ```

   ```bash
   npx wrangler secret put VISITOR_SALT
   ```

4. Deploy, and note the URL it prints (`https://aboutyowkey-api.<account>.workers.dev`):

   ```bash
   npm run deploy
   ```

5. Put that URL in `portfolio.json`, next to `siteUrl`, and push:

   ```json
   "api": "https://aboutyowkey-api.<account>.workers.dev"
   ```

   The build checks it is `https://` with no trailing slash. The next deploy of the site shows the live line under
   the headline and the form under Contact; the notes appear as you publish them.

**Reading and publishing the notes**: open `https://aboutyowkey-api.<account>.workers.dev/inbox` in a browser; any
user name, the password is `ADMIN_TOKEN`. It lists the notes newest first, with the visit totals and the last two
weeks by day, and a button for each: **Publish** (only for a note whose visitor ticked the box; it then shows on the
site), **Unpublish**, **Hide** (down, but kept), **Unhide** and **Delete**. The page's buttons only work from the
page itself, so a form on some other site cannot act with your browser's saved password. From the terminal,
`npm run inbox` prints the same table.

**Changes to the worker** deploy from CI (`.github/workflows/worker.yml`) once two repository secrets exist:
`CLOUDFLARE_ACCOUNT_ID` (on the dashboard's Workers page) and `CLOUDFLARE_API_TOKEN` (a token made from the "Edit
Cloudflare Workers" template, with D1 edit added). Without them the workflow only runs the worker's tests, and
`npm run deploy` by hand does the rest.

**Locally**: `npm run dev` in `worker/` serves it at `http://localhost:8787` against a SQLite file under
`.wrangler/` (`npm run db:init:local` first, which applies the migrations locally; copy `.dev.vars.example` to `.dev.vars` for the two secrets). Set
`"api": "http://localhost:8787"` in `portfolio.json` while testing: the parser allows `http://` for localhost only.

**Messages, link clicks and the owner's app**: the worker also keeps

- **Messages**: what the contact form sends privately (the contact is required, so there is someone to write back
  to; the topic is job, freelance, collaboration or other). A message is never on the site. A visitor may send 3
  a day, and the same hidden field catches bots.
- **Link clicks**: how many visitors clicked the links named in `TRACKED_LINKS` (`worker/wrangler.toml`: LinkedIn
  and GitHub), once per visitor per day per link, told apart by the same daily hash as visits and deleted the
  same way. Bots are not counted.
- **Countries**: Cloudflare's guess at a counted visit's country, kept only as a count per country per day, never
  next to a visitor.

Everything is counted by your day, Asia/Manila (`TIME_ZONE`), and at 23:59 there a cron writes the day's report.
Each new note, new message and daily report is kept as an alert that the Android app reads from the worker's
`/admin/` routes (the same `ADMIN_TOKEN`, sent as a Bearer token), and, once Firebase is set up, pushed to your
phone as it happens. The inbox page shows the messages too, with **Mark read**, **Archive** and **Delete**, and
today's link clicks.

To turn it on, once, in `worker/`:

1. Create the new tables (`worker/migrations/0003`):

   ```bash
   npm run db:init
   ```

2. A free [Firebase project](https://console.firebase.google.com/), and in it an Android app with the package name
   `dev.yowkey.portfolio`. The `google-services.json` it offers is for the app's build, not the worker.

3. The key the worker sends pushes with: in the Firebase console, **Project settings → Service accounts →
   Generate new private key**. It downloads a JSON file; keep it out of the repo.

4. Hand it to the worker as a secret (Wrangler reads the file from the pipe, so the key is never in a command or a
   config file), then delete the file:

   ```bash
   npx wrangler secret put FCM_SERVICE_ACCOUNT < key.json
   ```

5. Deploy, which also sets up the 23:59 cron:

   ```bash
   npm run deploy
   ```

Without step 4 the rest still works: the alerts are kept, and the app finds them when it checks every 15 minutes;
they just do not arrive the moment they happen. Locally, `npx wrangler dev --test-scheduled` runs the daily report
when you open `/__scheduled`.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/READINESS.md`](docs/READINESS.md).
