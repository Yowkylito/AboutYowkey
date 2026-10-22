# Architecture

```
wasmJsMain/main.kt ─┐                     jvmMain/main.kt (desktop window)
                    ├─ startKoin(appModule) → App()
                    │
ui/      PortfolioRoute ── koinViewModel ──► PortfolioViewModel ── StateFlow<PortfolioUiState>
           └─ PortfolioScreen (stateless) → TopBar, sections/*, components/*
data/    PortfolioRepository ◄── ResourcePortfolioRepository(readContent, dispatcher)
           └─ PortfolioParser (decode + validate)
model/   Portfolio, Profile, Service, Project, Experience, SkillGroup, Link   (@Serializable, @Immutable)
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
| `ui/PortfolioScreen` | Stateless page: adaptive layout from `WindowSizeClass`, nav, reveal animation. |
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
