# Architecture

```
wasmJsMain/main.kt ─┐                     jvmMain/main.kt (desktop window)
                    ├─ startKoin(appModule) → App()
                    │
ui/      PortfolioRoute ── koinViewModel ──► PortfolioViewModel ── StateFlow<PortfolioUiState>
           │            └─ koinInject ──► Navigator ── StateFlow<Route>   (HashNavigator on web, InMemoryNavigator on JVM)
           └─ PortfolioScreen (stateless) → list: TopBar, sections/*   |   detail/ProjectDetailScreen
                                             (AnimatedContent on the route; the list's LazyListState lives above it)
data/    PortfolioRepository ◄── ResourcePortfolioRepository(readContent, dispatcher)
           └─ PortfolioParser (decode + validate)
model/   Portfolio, Profile, Service, Project, Engineering, Experience, SkillGroup, Link   (@Serializable, @Immutable)
         ModuleGraph (cycle check, dependency layers for the module diagram)
site/    StaticHtml (common)  ◄── jvmMain/site/StaticSite.kt ◄── Gradle task generateStaticSite
```

Arrows point one way: UI → ViewModel → repository → parser → model. Nothing below the UI imports Compose UI.

| Module | One job |
|---|---|
| `model` | The shape of `portfolio.json`. |
| `data/PortfolioParser` | Turns JSON into a valid `Portfolio` or fails with a reason. Shared by the app and the build. |
| `data/PortfolioRepository` | Loads the portfolio off the main thread; the source (`Res.readBytes`) is injected so tests fake it. |
| `ui/PortfolioViewModel` | `Loading → Ready | Error`, with a retry that ignores double taps. |
| `ui/PageItems` | Flattens the portfolio into keyed lazy-list rows for the current column count. Pure, unit-tested. |
| `ui/PortfolioScreen` | Stateless page: adaptive layout from `WindowSizeClass`, nav, reveal animation, list ↔ detail switch. |
| `ui/navigation` | `Route` (`Home` / `Project(id)`, parsed from and written to `#/projects/<id>`) and the `Navigator` interface. The web's `HashNavigator` (wasmJsMain) keeps it in browser history; the desktop uses `InMemoryNavigator`. Provided per platform by `di/platformModule` (expect/actual). |
| `ui/detail` | `ProjectDetailScreen` (two columns on expanded, one below) and `ModuleDiagram`, bands of cards from `ModuleGraph.layers` that wrap to the width instead of scrolling. |
| `ui/CoverPlayback` | Which card covers may play: on screen, most visible first, at most two. Pure, unit-tested. |
| `model/ModuleGraph` | The module graph's rules: cycles, and the layers that put every module above what it uses. Pure, unit-tested. |
| `site/StaticHtml` | Renders the crawlable HTML twin of the page, plus `sitemap.xml` and `robots.txt`. |
| `di/AppModule` | Koin wiring: dispatcher, repository, ViewModel. |

## Performance notes

- Grids are chunked into rows before they reach `LazyColumn`, so each row is one keyed item with a stable
  `contentType`; nothing measures off-screen cards.
- Scroll-driven visuals read state in the draw/layer phase: the top bar's divider (`drawBehind { scrolled() }`) and
  the reveal (`graphicsLayer { alpha = progress.value }`). Scrolling recomposes nothing; only a change of active
  section does, through `derivedStateOf`.
- The model classes are `@Immutable`, so rows skip recomposition when their data is unchanged.
- JSON is decoded on `Dispatchers.Default` (injected); Kotlin/Wasm has no IO dispatcher.
- Card covers that are clips decode one frame at a time into one bitmap, only while on screen (`CoverPlayback`
  over the list's layout info, through `derivedStateOf`, so a card recomposes only when its own answer changes),
  and a shared `ClipDecodeGate` lets one clip decode per display frame however many play.
- On the web the viewer plays a clip's 60 fps `.mp4` twin, when it has one, as an HTML `<video>` (`ClipVideo`,
  expect/actual; the desktop actual is never called). The browser decodes it, in hardware where it can, so it costs
  the UI thread nothing per frame where a WebP frame costs 7–8 ms. Compose places the element over the canvas and
  cannot draw above it, so it is used only in the viewer, where nothing overlaps the media; the wrapper and the
  video pass pointer events through to the canvas, so the pager still swipes over it. A video that fails to load
  falls back to the WebP.
- The detail screen's pinned media column and its top bar divider read the scroll offset in the layer and draw
  phases. The module diagram levels each line of cards with `IntrinsicSize.Min` too, so a card's "uses" names are a
  `WrapRow` for the same reason.
- The card grid levels rows with `IntrinsicSize.Min`, so the card's chip groups use `WrapRow`, whose intrinsic
  height matches what it draws (FlowRow's did not, see `WrapRowLayoutTest`).
