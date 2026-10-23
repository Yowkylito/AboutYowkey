# Production readiness

A static site with no backend, no accounts and no stored user data. Most of the server-side checklist does not
apply; what does is below. `[x]` done, `[ ]` open, `N/A` with the reason.

## 1. Architecture

- [x] Layers: UI → ViewModel → repository → parser → model, one-way (see `ARCHITECTURE.md`)
- [x] DI with Koin; the content source is injected and faked in tests
- [x] Screen state is a sealed interface (`Loading | Ready | Error`); the route is one too (`Home | Project(id)`)
- [x] Navigation without a library: a `Navigator` interface, browser history on the web, a back stack on the desktop
- Check: `PortfolioViewModelTest` runs the ViewModel against a fake repository

## 2. Security

- [x] The one server-side piece is the site's own worker (`worker/`): the visit count and the note inbox. Its owner
  routes (`GET /feedback`, `/inbox`) take `ADMIN_TOKEN` only, compared in constant time, and stay closed while the
  token is unset; a visitor may leave 5 notes a day (by a daily hash; the address is never stored); every field has
  a type and length limit and the body a size limit; a browser's origin is checked against an allow-list; a hidden
  form field catches bots; the inbox escapes everything it shows; the inbox's buttons (hide, unhide, delete) act
  only on a post from the worker's own origin, since the browser would send the saved Basic credentials with a
  form from any site. All of it tested in Node against SQLite
- [x] A note is public as it arrives, as the form says, with the name, the stars and the words; the contact never
  leaves the inbox. Notes sent before that without the visitor's tick keep their words private (only their stars
  count, and the dashboard says how many). The owner takes a note down by hiding it, from the inbox or the app, and
  hears of every new note at once (or within 15 minutes before push is set up). `GET /notes` is the only public
  read, a page at a time, cached for 15 seconds
- [x] Link clicks and countries keep nothing about a person: a click is counted once per visitor per day per link
  by the same daily hash as a visit (the hashes deleted with the next day's first click), only for the links in
  `TRACKED_LINKS`, never for a bot; a country is Cloudflare's guess from the address, stored only as a count per
  country per day. Neither is ever next to a visitor id
- [x] Messages are private: never on the site, 3 per visitor per day, the same limits and trap field as notes. The
  owner's app reads them, the notes and the counts over `/admin/` with the same `ADMIN_TOKEN` as a Bearer token,
  answered `no-store`; the phones' push tokens are dropped when FCM says they are dead. A push carries a visitor's
  contact only for a message (who to write back to), and only to the owner's phone
- [x] No secrets in the repo or the bundle: the worker's (the Firebase service-account key included) are set with
  `wrangler secret put`, and its local `.dev.vars` is gitignored. CI runs gitleaks over the full history
- [x] Links limited to `https://` and `mailto:` by the parser (blocks `javascript:` URLs in content)
- [x] Project ids limited to `a-z`, `0-9` and `-` by the parser: they are written into the URL fragment and into HTML
  `id`s, and the router only accepts that shape back
- [x] All content HTML-escaped in the static page; JSON-LD cannot close its `<script>` (tested)
- [ ] Contact email: published as plain text when `profile.email` is set. Trigger to revisit: spam volume

## 3. Edge cases

| Step | Failure | Behaviour |
|---|---|---|
| Open the site | No WasmGC (old browser) | Static HTML page stays; it is complete |
| Open the site | Wasm download slow or fails | Static HTML shows from the first paint; the app replaces it only when ready |
| Load content | `portfolio.json` malformed | Build fails (CI); at runtime the Error state offers retry |
| Render | Empty section | Section and its nav link are not shown |
| Render | Long titles, long text, many tags | Card titles ellipsize at 2 lines, the card line at 3; chips wrap (`WrapRow`) and an overlong chip ends in an ellipsis; see `LongProjectCardPreview` |
| Render | Cards of different lengths in one row | Equal height: the stage is a fixed 280 dp, the button sits at the bottom. The row is levelled with intrinsic sizes, which `FlowRow` under-reported (the button was squeezed to half height on a phone); `WrapRow` reports what it draws (`WrapRowLayoutTest`) |
| Render | Project without media, or without `engineering` | The card's stage shows the title's initials; the chips fall back to the first 4 of `stack`; the detail screen drops the sections it has nothing for |
| Navigate | Deep link `#/projects/<id>` on first load | The app opens on the detail screen; the static page under it scrolls to `#project-<id>` (so browsers without WasmGC land there too). The on-screen back goes home in place, it does not leave the site. The hand-off gives the canvas the keyboard focus when nothing holds it, so Escape works before any click |
| Navigate | Unknown or malformed id in the URL | Malformed: treated as the list. Well-formed but unknown: the list shows and the URL is replaced with the bare page |
| Navigate | Back from a project (browser Back, Escape, the bar's arrow, the button at the end) | Returns to the list at the same scroll position: its `LazyListState` lives above the list/detail switch. Tapping a card that is partly off screen first brings it fully into view (the card takes focus), so that is the position kept |
| Navigate | Double-tap Retry | Second tap ignored while a load is in flight (tested) |
| Screenshots | Image slow or missing | Slot is sized from the JSON's width/height before loading, so nothing jumps; a failed image keeps its placeholder. Tests fail the build if a listed file is missing |
| Screenshots | Page weight | Cards load thumbnails, cover heroes and clip posters only (budget 62 KB each); full images (254 KB) and clips (700 KB) load when the viewer opens them |
| Screenshots | Memory | Three LRU caches weighed in decoded bytes (4 B/pixel), not counts. Card images: 24 MB, unchanged; cards now hold less (a cover or poster each, no thumbnail strip, which moved to the detail screen). Viewer: 18 MB, three 1600×900 stills; the detail screen's large still goes here too, so the viewer opens on it without a second decode. Cover clips: 8 MB, below. A clip counts as its encoded file plus **one** decoded frame, because it is decoded a frame at a time into one reusable bitmap: Pawffice's ride (480×271, 54 frames) is 0.69 MB + 0.52 MB = 1.2 MB, where decoding every frame would be 28 MB |
| Cover clips | Memory | Their own cache (`COVER_CLIP_BUDGET`, 8 MB), so scrolling past animated cards never evicts the viewer's open page, and their own instances, so the viewer never steps the same bitmap as a card. Sized for four clips at the script's limits (700 KB + one 720×405 frame = 1.9 MB): the two that may play and the two last scrolled past. Today's clips weigh 0.94–1.17 MB each (measured: Vistago splash 0.94, BubuProtect 1.04, Calm Escape 1.07, Seventh J 1.16, Pawffice 1.17), so four take 4.4 MB; `SiteContentTest` fails if four of the largest stop fitting |
| Cover clips | When they play | Only while their card is on screen (the list's layout info), the most visible first, at most two (`CoverPlayback`). Not downloaded until first asked to play. Paused, a cover keeps its frame; under reduced motion it is the poster and the clip is never fetched (checked: no clip request in Chrome). Off screen: checked, no decodes in 10 s with the covers scrolled away. Background tab: the frame clock stops, as for the viewer (not observable in headless Chrome, which keeps tabs visible) |
| Clips | Decode cost | Decoded on the UI thread inside the frame clock (Wasm has one thread): measured 7.0–8.0 ms a frame on average, 12.6 ms worst, in Chrome 156. Inside 16 ms at 60 Hz; at 120 Hz a decode frame can overrun 8 ms, 12 times a second. Paused while the pager is swiped, off the settled page, in a background tab, and under reduced motion (a still of frame 0) |
| Cover clips | Decode cost, two at once | A shared `ClipDecodeGate` lets one clip decode per display frame; the other waits one frame (16 ms late on an 83 ms frame). So two covers never add up inside a frame, and the worst frame is set by the most expensive single clip. Measured in headless Chrome 156 (SwiftShader, DPR 1), 12 s each, per-frame time = all work inside the animation-frame callbacks: **BubuProtect + Vistago** (both 332×720, small changes per frame): decode 1.0–1.9 ms avg, 11 ms worst; frames 3.2 ms avg, p95 8.8, max 11.0, none over 16 ms. **Pawffice alone** (480×271, whole-frame changes): decode 5.9–6.6 ms avg, 10.7 worst; frames 7.2 avg, p95 9.5, max 11.9, none over 16. **Seventh J + Pawffice**: Seventh J decodes at 10 ms avg, 21.5 worst; frames 9.1 avg, p95 16.5, 15 of 241 over 16 ms. **Seventh J alone**: frames 11.7 avg, p95 17.0, max 18.4, 24 of 107 over 16 ms. Two covers are therefore no worse than one, so up to two play; a clip that repaints a full 324×720 screen every frame (Seventh J's stroke splash) overruns 16 ms on its own and should not be a cover clip as encoded: pick a calm stretch, or encode it smaller |
| Clips | Over budget | `prepare_screenshots.py --clip` keeps quality at 50 or more and shrinks the frame instead; a busy 4.5 s stretch of the Pawffice trailer came out at 480 px. Calmer or shorter stretches keep more pixels |
| Resize | Window or rotation changes size class | Layout re-derives columns from `WindowSizeClass` live |

## 4. Database

- Content is one JSON file in the repo, versioned by git
- Visits, clicks, notes and messages live in a D1 (SQLite) database on Cloudflare: `worker/migrations/`, applied in
  order by `npm run db:init` (wrangler remembers which have run). Counts by the owner's local day (Asia/Manila):
  `visits`, `visit_countries` and `clicks`; today's hashes, deleted the next day: `visitors` and `clickers`; what
  visitors write: `feedback` (with `consent`, `published_at`, `hidden_at`, `day`) and `messages` (with `read_at`,
  `archived_at`); the owner's: `notifications` (every alert), `devices` (the phones' FCM tokens) and
  `daily_reports`. Nothing identifying is kept beyond what a visitor types into a form

## 5. Real-world testing

| Check | Status |
|---|---|
| 375 px phone, light | [x] checked in a browser: compact nav menu, single column |
| 768 px tablet | [x] two-column cards, inline nav, stacked headers |
| 1024 px+ desktop, dark | [x] header rail beside two-column cards |
| Live resize between size classes | [x] |
| Reduced motion | [x] reveals and skeleton pulse disabled; card covers show posters, clips not fetched; the list ↔ detail switch is instant. The detail hero and the viewer show a Play/Pause button on clips instead, so animations stay one tap away (reader-started motion); checked in Chrome with Windows animations off: plays on Play, and the a11y tree exposes "Play the animation" |
| Project card and detail, 412 px phone and 1280 px desktop, light and dark | [x] checked in Chrome 156 (screenshots in the PR hand-off): equal-height cards, chips, glyphs, the module diagram on a phone |
| Detail routing | [x] open, browser Back (scroll kept), Escape, the bar's arrow after a deep link, `#/projects/<id>` on first load, an unknown id |
| Safari / Firefox | [ ] not yet run. Trigger: before sharing the link |
| Throttled 3G | [ ] static HTML covers first paint; measure time-to-app. Trigger: before sharing the link |
| Screen reader on the Compose canvas | [ ] Compose web accessibility is limited; the static HTML is the accessible version. Trigger: before sharing the link |

Automated: Lighthouse CI on every deploy (SEO and best practices ≥ 0.9 fail the job; accessibility and
performance warn).

## 6. After launch

- [x] Deploy on every push to `main`: a content fix is live in minutes
- [x] Lighthouse report kept as a workflow artifact on each deploy
- [x] Visit counts (once per visitor per day, no addresses kept) and a feedback inbox, on the site's own worker. The
  page shows them once `portfolio.json` names the worker (README, "Visits and feedback"); the notes, their rating
  dashboard and a star filter show on the page too
- [x] The owner hears about a new note or message as it arrives, and gets the day's numbers at 23:59 Manila time:
  each is kept as a record the Android app polls every 15 minutes, and pushed through FCM once the
  `FCM_SERVICE_ACCOUNT` secret is set (README, "Visits and feedback"). A failed push never fails the visitor's
  request
- [ ] Custom domain (`CNAME`). Trigger: when a domain is bought

## Not done

- Swipe-back gesture on touch screens: the detail screen leaves by Back, Escape or its buttons. Trigger: phone analytics show it is missed
- A per-clip decode budget enforced by the build: cover clips are held to the clip limits, not to a measured decode time (which needs a browser). Trigger: a cover clip that janks

- Android / iOS targets. Trigger: when an app-store demo is wanted
- Custom font. The default font is used; bundling Inter (as in The Eagle's Watch) is a small follow-up
- `og:image` for link previews. Trigger: when there is artwork for it
- Editing a note's words (a typo the visitor made, say): today it is shown as is, or hidden. Trigger: the first such note
- Holding back notes with links or abuse until the owner has looked: today every note is up at once and the owner
  hides what should not be. Trigger: the first spam note that gets through the daily limit and the bot trap
