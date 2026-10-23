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
- project `id`s are unique (they key the list rows)
- links are `https://` or `mailto:`

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
```

- **Cover**: the splash screen shown large at the top of the card (full card width when landscape, a 320dp phone
  frame when portrait). Same files as a screenshot, with a bigger `.thumb.webp` for the card.
- **Clip**: a short animated WebP (at most 6 s, 12 fps, 720 px on the long edge, 700 KB) plus a `.poster.webp`
  still (its first frame) for the card. The source is an mp4 (ffmpeg must be on PATH; pick the stretch with
  `--start`/`--duration`, default the first 5 s) or a folder of PNG frames. If the clip is over budget the script
  makes it smaller rather than blurrier, so pick a calm stretch, or a shorter one, to keep it sharp. The card shows
  the poster with a play mark; the viewer plays it in a loop (a still under reduced motion); the static page
  animates it natively.
- **Visibility**: `"public"` or `"private"`. The card shows a "Public repo" / "Private repo" badge, and if any
  project is private a note under the Projects header says why its source is not linked. Leave it out for no badge.

The viewer pages through cover → clips → screenshots, in that order.

```json
{
  "id": "vistago",
  "visibility": "private",
  "cover": { "file": "vistago-cover.webp", "caption": "…", "width": 739, "height": 1600 },
  "clips": [{ "file": "vistago-trip.webp", "caption": "…", "width": 720, "height": 405 }],
  "screenshots": [{ "file": "vistago-ask.webp", "caption": "…", "width": 739, "height": 1600 }]
}
```

## How the page loads

A Compose app on the web draws into a `<canvas>`, which search engines, link previews and screen readers can't read,
and its runtime is several megabytes. So the build also renders `portfolio.json` into plain HTML inside
`index.html` (see `composeApp/web/index.template.html`):

1. The browser shows that HTML straight away. It is a complete, styled version of the page.
2. If the browser supports WasmGC, the Compose app loads behind it, invisible.
3. Once Compose has drawn real content, the page swaps to it.

Browsers without WasmGC, or a failed download, just keep the HTML version.

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

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/READINESS.md`](docs/READINESS.md).
