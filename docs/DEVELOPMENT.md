# Development

Everything about editing, running and deploying the portfolio: the content file and its rules, the media scripts,
how the page loads, the backend and the owner's Android app. What the site is, and why it is laid out the way it is,
is in the [README](../README.md). Commands run from the repository root unless a step says otherwise.

## Editing the content

Everything on the page comes from one file:
[`composeApp/src/commonMain/composeResources/files/portfolio.json`](../composeApp/src/commonMain/composeResources/files/portfolio.json).
Change it, push to `main`, and the site redeploys. A section with no entries disappears, nav link included.

Rules the build enforces (it fails rather than ship a broken page):

- `profile.name` is not blank; `siteUrl` is `https://`
- project `id`s are unique and plain (`a-z`, `0-9`, `-`): they key the list rows and are the project's link,
  `#/projects/<id>`
- links are `https://` or `mailto:`
- `api`, if given, is `https://` with no trailing slash: the site's backend (see "Visits and feedback")
- `profile.photo`, if given, is a plain `.webp` name in `files/profile/` (see "The portrait")

### The portrait

`"photo": "portrait.webp"` in `profile` puts my photo in the hero, standing on a tinted stage with its echo trail
(see `HeroPortrait.kt`). On a phone or tablet my name and role (the headline up to its first " · ") stand on the
stage, left of the photo, when they fit there (`HeroStage.kt` decides by measuring; the static page gets the same
answer from a generous width estimate); otherwise, and on a wide window, the name is beside or under the photo as
usual. Once per page load the photo dashes in from the stage's left edge, its trail draws back, and the name, role,
headline, proof, buttons and summary read in after it (`HeroEntrance.kt`, on the timeline in `HeroMotion.kt`, which
the static page plays too). The photo must be a cut-out: transparent around the person, cropped to them, at most
0.85 times as wide as it is tall (the name's place on the stage counts on it), with
the bottom edge where the photo ends so the figure stands on the stage's floor. On a Mac, Photos or Preview can lift
the subject out of a photo (or a short Swift script with Vision's `VNGenerateForegroundInstanceMaskRequest`); save it
as a PNG, then:

```bash
cwebp -q 88 -alpha_q 100 -alpha_filter best -m 6 -sharp_yuv cut-out.png -o composeApp/src/commonMain/composeResources/files/profile/portrait.webp
```

Use the largest original there is: the wide layout draws the figure 224dp tall, so 450 px or more keeps it sharp on
a 2x screen. Tests fail if the file is missing, over 48 KB, not transparent at its top corners, or not the only file
in `files/profile/`.

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

The hero's entrance plays on the HTML first, in CSS written from the app's own timeline, and only where the visitor
allows motion. When the app takes over it reads how far that entrance has got and keeps to it frame by frame, so the
swap never hides words that are already showing and brings them in again.

Browsers without WasmGC, or a failed download, just keep the HTML version. It carries everything the detail
screens do, one `<article id="project-<id>">` per project, and a `#/projects/<id>` link scrolls to that article
there too.

With an `api` in `portfolio.json` (see below), both versions also count clicks on the profile's LinkedIn and GitHub
links, and carry a private **Message me** form. A click leaves as a beacon (`navigator.sendBeacon`), which the browser
delivers even as the link takes the tab away; the page sends it at most once per link per page load and never makes
the link wait for it. The worker counts it once per visitor per day per link, telling visitors apart by the same
salted daily hash as visits, and nothing is stored on the device. The desktop window is the owner's, so it counts
nothing. The message form sits above the notes and the public **Leave a note** form, and each is badged
Private or Public: a message asks for a way to reply (an email, or anything else) and an optional topic (a job, a
freelance project, a collaboration, something else), only the owner reads it, and it is never shown on the site.
Without the page's script, both forms post as plain HTML forms and the worker answers with a page.

## Run it

JDK 17+ (Android Studio's bundled JBR works).

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, with live reload
./gradlew :composeApp:run                           # desktop window
./gradlew :composeApp:jvmTest                       # tests
./gradlew :composeApp:wasmJsBrowserDistribution     # the bundle that ships, in composeApp/build/dist/wasmJs/productionExecutable
```

## Deploy

The site is served by Cloudflare Pages, project `aboutyowkey`, at https://aboutyowkey.pages.dev/. The repository is
private, and GitHub Pages serves private repositories only on a paid plan, so the site moved there on 2026-10-24.

`.github/workflows/pages.yml` scans for secrets, runs the tests and builds the bundle on every push and pull request.
On `main` it then deploys the bundle to Cloudflare Pages and runs Lighthouse against the live URL, once the
repository has the `CLOUDFLARE_API_TOKEN` (with the Cloudflare Pages: Edit permission) and `CLOUDFLARE_ACCOUNT_ID`
secrets; until then the deploy is skipped with a note in the run's summary, and the site is deployed from a Mac
signed in with `npx wrangler login`:

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
npx --prefix worker wrangler pages deploy composeApp/build/dist/wasmJs/productionExecutable --project-name aboutyowkey --branch main
```

The site's address is in three places that must agree: `siteUrl` in portfolio.json (canonical links, sitemap),
`ALLOWED_ORIGINS` and `SITE_URL` in `worker/wrangler.toml` (the backend answers only the site's own pages), and the
Lighthouse URL in pages.yml.

## Android app

`androidApp/` builds **Yowkey**, an APK for my own phone. It opens on the portfolio exactly as the site shows it,
running natively. Long-press its icon and pick **Owner** to sign in (the password is the worker's `ADMIN_TOKEN`, see
below): a dashboard of today's visits, LinkedIn and GitHub clicks, unread messages and who wants to hire me, the 30
days of visits and their countries; the private messages (reply by email, archive, delete), the notes to pin, hide or
delete, and the history of every alert and daily report. A new message, a new note and the 23:59 summary arrive as
notifications. Once signed in, the app opens there, with **Portfolio** to go back to the site.

The SDK needs platform 37 (Compose 1.12 compiles against it; the app targets 36 and runs on Android 8.0+).

```bash
./gradlew :androidApp:assembleDebug     # androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :androidApp:assembleRelease   # shrunk with R8; signed only with keystore.properties, below
```

Every CI run of `pages.yml` builds the debug APK and keeps it for two weeks as the `yowkey-debug-apk` artifact.

**Push, once.** Without it the app still gets every alert, by asking the worker every 15 minutes; with it they
arrive as they happen.

1. In the [Firebase console](https://console.firebase.google.com/), create a project (Analytics is not needed).
2. Add an Android app with the package name `dev.yowkey.portfolio`, download its `google-services.json` and put it
   at `androidApp/google-services.json`. It is gitignored; never commit it. The build applies the Google services
   plugin only when the file is there, so without it everything builds the same with push off.
3. Give the worker its key, `FCM_SERVICE_ACCOUNT` (step 3 and 4 under "Visits and feedback" below).
4. Rebuild, install, sign in once and allow notifications. **Settings** in the app then says this phone is
   registered and the server has push set up.

**A signed release, once.** Create a key and keep it safe (every update must be signed with the same one):

```bash
keytool -genkeypair -v -keystore ~/keys/yowkey-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias yowkey
```

then `keystore.properties` at the repo root (gitignored), and `assembleRelease` signs with it:

```properties
storeFile=/Users/you/keys/yowkey-release.jks
storePassword=…
keyAlias=yowkey
keyPassword=…
```

Without the file the release APK is unsigned and Android will not install it; use the debug one. A debug install
must be uninstalled before a release one goes on (they are signed with different keys).

**Trying it against a local worker** (debug builds only): with `npx wrangler dev` running in `worker/`,
`adb shell am start -n dev.yowkey.portfolio/.app.MainActivity --es debug_api http://10.0.2.2:8787` sends the
app's requests there instead (the emulator reaches the computer at 10.0.2.2), and `--es debug_api ""` undoes it.

## Visits and feedback

The bar's proof chip (the notes' rating and the visit count, while the hero is on screen), the "Notes from visitors" dashboard
and cards and the "Leave a note" form under Contact are real data, kept by a small backend of the site's own: a
Cloudflare Worker with a D1 (SQLite) database, in [`worker/`](../worker/). Cloudflare Pages serves files only, so
this is the one piece that runs on a server. Cloudflare's free plan covers it many times over (100,000 requests a day), and
it never sleeps.

What it keeps, and nothing more:

- **Visits**: one count per day. A visitor is counted once a day, told apart by a hash of the day, their address,
  their browser and a secret salt; the address itself is never stored, and the hashes are deleted the next day.
  Crawlers, link previews and CI's Lighthouse run read the count but are not counted.
- **Notes**: what the form says (name, email and how they know me are optional), the rating, the time, and the
  day's hash for a limit of 5 notes per visitor per day, and one every 5 minutes (`NOTE_GAP_MINUTES` in
  `worker/wrangler.toml`). A hidden field catches bots that fill every field. The form says a note is public,
  and it is: it shows on the page as soon as it is sent.
- **What the page shows**: every note, pinned ones first, then newest, a page at a time. A card has the name (or "A
  visitor") and the stars on top, the date with any badge under them (Pinned; Colleague, Friend or Special someone),
  then the words, never the email. Above them, a dashboard: the average to one decimal and how many rated, and a bar
  per number of stars with its count and share. A bar opens a sheet of that star's notes, sorted by who wrote them
  (all, colleagues, friends, a special someone, others: a note that does not say, like every older one, is an
  other). The ratings count every note you have not hidden, including the few sent before notes were public without
  the visitor's tick: their words stay private, and the dashboard and each star's sheet say how many. Hiding a note
  takes it down (and its stars out of the count, and its pin off); deleting it removes it for good.

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

   The build checks it is `https://` with no trailing slash. The next deploy of the site shows the rating and visits in
   the bar and the form under Contact; a note appears as soon as it is sent.

**Reading the notes, and taking one down**: open `https://aboutyowkey-api.<account>.workers.dev/inbox` in a browser;
any user name, the password is `ADMIN_TOKEN`. It lists the notes newest first, with the visit totals and the last two
weeks by day, and a button for each: **Hide** (off the site, but kept), **Unhide**, **Pin** (first on the site, newest
pin first; only for a note on the site) or **Unpin**, and **Delete**. A private note (sent before notes were public,
without the tick) also has **Show on the site**, for when its writer has since said yes. The app's Notes screen has
the same. The page's buttons only work from the
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

See [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`READINESS.md`](READINESS.md).
