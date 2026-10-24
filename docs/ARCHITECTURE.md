# Architecture

```
wasmJsMain/main.kt ─┐                     jvmMain/main.kt (desktop window)
                    ├─ startKoin(appModule) → App()
                    │
ui/      PortfolioRoute ── koinViewModel ──► PortfolioViewModel ── StateFlow<PortfolioUiState>, <VisitCount?>, <SiteNotes?>, <FeedbackUiState>, <MessageUiState>
           │            └─ koinInject ──► Navigator ── StateFlow<Route>   (HashNavigator on web, InMemoryNavigator on JVM)
           └─ PortfolioScreen (stateless) → list: TopBar, sections/*   |   detail/ProjectDetailScreen
                                             (AnimatedContent on the route; the list's LazyListState lives above it)
data/    PortfolioRepository ◄── ResourcePortfolioRepository(readContent, dispatcher)
           └─ PortfolioParser (decode + validate)
         SiteApi ◄── HttpSiteApi(HttpTransport: FetchTransport on web, JavaHttpTransport on JVM) ──► worker/ (Cloudflare Worker + D1)
model/   Portfolio, Profile, Service, Project, Engineering, Experience, SkillGroup, Link   (@Serializable, @Immutable)
         VisitCount, RatingSummary, PublicNote, SiteNotes, LiveStats, FeedbackDraft, FeedbackCopy (Site.kt: what the backend answers and takes, the hero's line, the form's words)
         MessageDraft, MessageTopic, MessageCopy, Link.trackingKey (Site.kt too: the private message form, the tracked links)
         ProjectGroup (work, then personal: a project with a company is work)
         ModuleGraph (cycle check, dependency layers for the module diagram)
site/    StaticHtml (common)  ◄── jvmMain/site/StaticSite.kt ◄── Gradle task generateStaticSite
```

Arrows point one way: UI → ViewModel → repository → parser → model. Nothing below the UI imports Compose UI.

| Module | One job |
|---|---|
| `model` | The shape of `portfolio.json`. |
| `data/PortfolioParser` | Turns JSON into a valid `Portfolio` or fails with a reason. Shared by the app and the build. |
| `data/PortfolioRepository` | Loads the portfolio off the main thread; the source (`Res.readBytes`) is injected so tests fake it. |
| `data/SiteApi` | The site's backend as the app sees it: count this visit, read the rating and a page of the public notes (all, or one star's), send a note. `HttpSiteApi` speaks the worker's JSON over an `HttpTransport` each platform provides (`fetch` on the web; `java.net.http` on the desktop, which only reads the count). Called only when `Portfolio.api` is set. |
| `ui/PortfolioViewModel` | `Loading → Ready | Error`, with a retry that ignores double taps; once ready, counts the visit and loads the notes (each once; then the dashboard's star filter and "Show more" page through them, and a sent note reloads them past the cache), runs the note form's and the message form's `Idle → Sending → Sent | Failed`, and counts a LinkedIn or GitHub click once per link per page load. |
| `ui/PageItems` | Flattens the portfolio into keyed lazy-list rows for the current column count, the projects split into their two groups (work first, each group's first row carrying its title and note). Pure, unit-tested. |
| `ui/sections` | The page's rows. Projects use `WorkCard` (figures, no media: the code is the employer's) or `ProjectCard` (cover or clip). The hero's numbers are the proof cards; the live rating and visits are in the bar; the Contact section the private message (`MessageSection`: a title, a line and a button that opens the `MessageForm` in a `FormDialog`), then the `RatingsDashboard` (the average with a `StarMeter` lit to the fraction, a bar per star that opens that star's `StarSheet`, a Material bottom sheet of its notes with a relation sorter and its own "Show more", the bars growing in unless motion is reduced), the public `NoteCards` (name and stars, then the date and the Pinned and relation badges, then the words) with "Show more", and the public Feedback (`FeedbackSection`, whose button opens the `FeedbackForm` in a `FormDialog`; the form asks how the writer knows me). The dialogs sit over the page, outside the list, and keep their drafts while closed. |
| `ui/PortfolioScreen` | Stateless page: adaptive layout from `WindowSizeClass`, nav, reveal animation, list ↔ detail switch. |
| `ui/navigation` | `Route` (`Home` / `Project(id)`, parsed from and written to `#/projects/<id>`) and the `Navigator` interface. The web's `HashNavigator` (wasmJsMain) keeps it in browser history; the desktop uses `InMemoryNavigator`. Provided per platform by `di/platformModule` (expect/actual). |
| `ui/detail` | `ProjectDetailScreen` (two columns on expanded, one below) and `ModuleDiagram`, bands of cards from `ModuleGraph.layers` that wrap to the width instead of scrolling. |
| `ui/CoverPlayback` | Which card covers may play: on screen, most visible first, at most two. Pure, unit-tested. |
| `ui/theme` | The palette (neutral zinc, one blue accent; a light and a dark scale), the type scale, and the theme switch: light unless the visitor asks for dark, never the OS's choice. `App` keeps the choice through `savedDarkTheme`/`saveDarkTheme` (expect/actual: the browser's localStorage on the web, where the template's head script applies the same key before the static page's first paint; Java preferences on the desktop). A switch spreads the new theme from the switch: `ThemeReveal` keeps the page's last frame in a graphics layer, swaps the theme under a picture of it and cuts a growing circle out of the picture (under reduced motion the picture fades instead). |
| `model/ModuleGraph` | The module graph's rules: cycles, and the layers that put every module above what it uses. Pure, unit-tested. |
| `site/StaticHtml` | Renders the crawlable HTML twin of the page, plus `sitemap.xml` and `robots.txt`. With an `api`, that includes the message and note forms (each in a `<dialog>` its section's button opens, shown inline in the section without the page's script; posting to the worker either way), the visit count's place, which the template's script fills, and `data-track` on the LinkedIn and GitHub links for its click beacon. The page loads the app as `composeApp.js?v=<build>`, the build id `generateStaticSite` passes in (the git commit; the build time where there is no git): GitHub Pages caches every file for ten minutes, and a plain `composeApp.js` could be the previous deploy's, pointing at wasm files that are gone (those are content-hashed already). |
| `worker/` | The backend itself: one Cloudflare Worker over one D1 database (`migrations/`), plain JavaScript with no dependencies, tested in Node against SQLite. Routes, limits and rules in `worker/src/handler.js`; the owner's inbox page, with its publish/hide/delete buttons, in `inbox.js`. |
| `di/AppModule` | Koin wiring: dispatcher, repository, ViewModel. |

## The bar, scrolling and reading

The hero already shows the name in large type, so the bar does not repeat it while the hero is on screen. It
carries the monogram (`ui/components/Brand.kt`, the Home button) and the proof chip (`ui/components/ProofChip.kt`:
the rating drawn to the fraction and the visit total, from `LiveStats`, counting up once on first appearance, a tap
away from the notes dashboard; the profile's location and availability are not shown on the page), and swaps the
chip for the name over the role once half the hero has scrolled under it (`rememberHeroPassed`, a `derivedStateOf`, so the bar
recomposes only when that flips). The 2dp reading-progress line and the hairline are drawn in the draw phase from
`listState.layoutInfo`, so scrolling never recomposes the bar, and the count-up is drawn too. On a narrow bar the chip drops its words (`ProofFit`) and the role shortens at
whole words (`shorterForms`), and the static page uses the same helpers for its phone bar.

Scrolling lives in `ui/navigation/PageScroll.kt` and `ScrollRules.kt`: Back to top (after 1.5 screens, hidden again
under 1), keyboard reading (Space, Page Up/Down, Home/End, the arrows; a focused field keeps its keys, and on wasm the
page frame takes focus back when a field lets go), and section jumps whose glide grows with the square root of the
distance. Reduced motion makes every one of them instant.

Long text uses `Modifier.readingMeasure(style)` (`ui/sections/Reading.kt`): 40 times the text size, never over
680dp, so about 75–85 characters a line; paragraphs are `ParagraphGap` apart. The project page is a 720dp article
column, two columns only on wide windows with media.

## Link clicks and private messages

A click on the profile's LinkedIn or GitHub link opens the link first; only then does `LinkButton` tell the
ViewModel, whose `trackClick` sends `POST /clicks {"link":"linkedin"}` on its own coroutine at most once per link per
page load and drops any failure, so the link never waits on it or fails with it. In the browser `HttpSiteApi` hands
the click to a `Beacon` (`navigator.sendBeacon`, `text/plain`, no CORS preflight), which outlives the tab; elsewhere
it goes through the `HttpTransport`, and the desktop (`countsVisits = false`) sends nothing. The static page's
script does the same for its `data-track` links. The worker counts a click once per visitor per day per link, by the
salted daily hash it uses for visits; nothing is kept on the device. The private message form (`MessageDraft`,
`POST /messages`) mirrors the note form, its rules the worker's own sentences in the worker's order, and is badged
Private against the note form's Public; only the owner reads a message, and the site never shows one.

## Android

```
androidApp/  com.android.application, applicationId dev.yowkey.portfolio ("Yowkey")
  YowkeyApplication ── startKoin(appModule, platformModule, ownerModule) · AndroidThemeStore.init · channels · PollWorker
  MainActivity ── YowkeyRoot ─┬─ guest view: App() (the shared page, unchanged) + BackHandler → Navigator.back()
       ▲                      ├─ SignInScreen ─┐
       │ OwnerLink            └─ AdminScreen ──┴─ OwnerViewModel ── OwnerSession ── AdminApi ──► worker /admin/*
       │ (shortcut "Owner",       Dashboard · Messages · Notes · Records · Settings        │
       │  notification taps)                                                               └─ OwnerStore + TokenCipher
  push/  PushService (FCM data message) ─┐                                                   (prefs; token sealed with an
         PollWorker (WorkManager, 15 min)┴─► AlertNotifier: notification id = record id,      Android Keystore AES-GCM key)
         RegisterWorker (POST /admin/devices)   channels messages / notes / daily, tap → OwnerLink
composeApp/  com.android.kotlin.multiplatform.library (namespace dev.yowkey.portfolio.shared), next to jvm and wasmJs
  androidMain/  UrlConnectionTransport (HttpTransport + AdminTransport) · platformModule (InMemoryNavigator,
                HttpSiteApi(countsVisits from AndroidHost)) · ClipVideo (ExoPlayer → TextureView) · AnimatedClip
                (first frame) · ThemeStore (SharedPreferences)
  commonMain/admin/  AdminApi, AdminTransport, the owner routes' DTOs, and the pure rules (filters, the 30-day
                series, top countries, the backup check's cursor), tested in commonTest/admin
  skikoMain/  what the desktop and the web share and Android has not: AnimatedClip over Skia's Codec
```

| Piece | One job |
|---|---|
| Guest view | The portfolio exactly as on the web: the shared `App`, kept clear of the system bars and the keyboard (edge to edge, `safeDrawing` insets), Back popping the in-memory `Navigator`. It counts its visit and clicks like a browser (`countsVisits = true`) only when the owner was signed out when the app started. Card covers, the viewer and the detail hero play the clips' 60 fps `.mp4` twins with Media3 ExoPlayer, muted and looping, paused while `playing` is false or the window is hidden; a failed video falls back to the WebP, which Android, with no Skia, shows as its first frame. |
| Way in | The guest UI is untouched: a static launcher shortcut, "Owner" (long-press the icon), and every notification open MainActivity with an `OwnerLink` (`yowkey-owner://messages/34`, no intent filter). Signed in, the app opens on the admin view, which has "Portfolio" in its top bar; from the guest view the owner gets back with Back or the corner button, which guests never see. |
| `OwnerSession` | Signs in by checking the password (the worker's `ADMIN_TOKEN`) with `GET /admin/summary`, then seals it in the Keystore, notes where the alert records stand, schedules the backup check and registers for push. A 401 anywhere signs out to the sign-in screen with the reason; sign-out sends `DELETE /admin/devices`, cancels the work, unregisters from FCM and clears the notifications. The server is `Portfolio.api` from the bundled `portfolio.json`. |
| `AdminApi` | The owner routes over an `AdminTransport` (an `HttpTransport` that also takes headers, for the bearer token): JSON with `ignoreUnknownKeys`, the worker's `{error}` sentence as the error, 401 as `AdminSignedOutException`, nothing reached as an offline error. |
| Push | Firebase Cloud Messaging, data-only. The config file (`androidApp/google-services.json`) is never committed; the build applies the Google services plugin only when it is there. Without it the app builds and runs the same with push off. `PushService` turns each message into the notification the backup check would make; `onRegistered`/`onNewToken` re-register while signed in. |
| Backup check | `PollWorker`, periodic every 15 minutes with a network constraint, only while signed in: `GET /admin/notifications?after=<cursor>` (oldest first), each record through the same `AlertNotifier`, the cursor and the ids already shown kept in `OwnerStore` (`planPoll`). Its first run only notes the newest id, so old alerts never flood in. Settings has "Check now". |
| Theme | The page's own: light unless the visitor (or the owner, in Settings) switches, kept in `AndroidThemeStore`; the admin view and the system bars' icons follow the same choice. |
| Debug only | `DebugApi` (src/debug; src/release has a no-op twin) redirects every request to a local worker given with `--es debug_api http://10.0.2.2:8787`, and the debug network config allows plain HTTP to 10.0.2.2 and localhost only. |

## Performance notes

- Grids are chunked into rows before they reach `LazyColumn`, so each row is one keyed item with a stable
  `contentType`; nothing measures off-screen cards.
- Scroll-driven visuals read state in the draw/layer phase: the top bar's divider (`drawBehind { scrolled() }`) and
  the reveal (`graphicsLayer { alpha = progress.value }`). Scrolling recomposes nothing; only a change of active
  section does, through `derivedStateOf`.
- The hero's entrance (`ui/sections/HeroEntrance`: the portrait's dash, then the intro reading in) runs on one clock
  kept above the list, read in the portrait's draw phase and the intro's layers, so it plays once per page load and
  recomposes nothing (`HeroEntranceTest` counts). Its timeline (`HeroMotion`) is pure, and `StaticHtml.heroMotion`
  writes the static page's CSS from it; on the web the app keeps to the static page's clock (`staticIntroMillis`,
  expect/actual) until the entrance ends.
- The model classes are `@Immutable`, so rows skip recomposition when their data is unchanged.
- JSON is decoded on `Dispatchers.Default` (injected); Kotlin/Wasm has no IO dispatcher.
- Card covers that are clips decode one frame at a time into one bitmap, only while on screen (`CoverPlayback`
  over the list's layout info, through `derivedStateOf`, so a card recomposes only when its own answer changes),
  and a shared `ClipDecodeGate` lets one clip decode per display frame however many play.
- On the web the viewer, the card covers and the detail screen's hero play a clip's 60 fps `.mp4` twin, when it has
  one, as an HTML `<video>` (`ClipVideo`, expect/actual; the desktop actual is never called). The browser decodes it,
  in hardware where it can, so it costs the UI thread nothing per frame where a WebP frame costs 7–8 ms. Compose
  places the element over the canvas and cannot draw above it, so it is used only where nothing is drawn over the
  media, inside the scrolling areas below the top bars (Compose clips the element to them), and a cover's video is
  only up while it plays, so it never shows through the viewer. Its corners follow `mediaFrameCorner`. The wrapper
  and the video pass pointer events through to the canvas, so swipes and taps still work. A video that fails to
  load falls back to the WebP.
- The detail screen's pinned media column and its top bar divider read the scroll offset in the layer and draw
  phases. The module diagram levels each line of cards with `IntrinsicSize.Min` too, so a card's "uses" names are a
  `WrapRow` for the same reason.
- The card grid levels rows with `IntrinsicSize.Min`, so the card's chip groups use `WrapRow`, whose intrinsic
  height matches what it draws (FlowRow's did not, see `WrapRowLayoutTest`).
