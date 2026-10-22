# Production readiness

A static site with no backend, no accounts and no stored user data. Most of the server-side checklist does not
apply; what does is below. `[x]` done, `[ ]` open, `N/A` with the reason.

## 1. Architecture

- [x] Layers: UI → ViewModel → repository → parser → model, one-way (see `ARCHITECTURE.md`)
- [x] DI with Koin; the content source is injected and faked in tests
- [x] Screen state is a sealed interface (`Loading | Ready | Error`)
- Check: `PortfolioViewModelTest` runs the ViewModel against a fake repository

## 2. Security

- N/A authentication, authorisation, rate limiting: no server, no accounts, no API
- [x] No secrets in the repo or the bundle; there are none to have. CI runs gitleaks over the full history
- [x] Links limited to `https://` and `mailto:` by the parser (blocks `javascript:` URLs in content)
- [x] All content HTML-escaped in the static page; JSON-LD cannot close its `<script>` (tested)
- [ ] Contact email: published as plain text when `profile.email` is set. Trigger to revisit: spam volume

## 3. Edge cases

| Step | Failure | Behaviour |
|---|---|---|
| Open the site | No WasmGC (old browser) | Static HTML page stays; it is complete |
| Open the site | Wasm download slow or fails | Static HTML shows from the first paint; the app replaces it only when ready |
| Load content | `portfolio.json` malformed | Build fails (CI); at runtime the Error state offers retry |
| Render | Empty section | Section and its nav link are not shown |
| Render | Long titles, long text, many tags | Titles ellipsize at 2 lines; tags wrap (`FlowRow`); see `LongProjectCardPreview` |
| Navigate | Double-tap Retry | Second tap ignored while a load is in flight (tested) |
| Screenshots | Image slow or missing | Slot is sized from the JSON's width/height before loading, so nothing jumps; a failed image keeps its placeholder. Tests fail the build if a listed file is missing |
| Screenshots | Page weight | Cards load thumbnails only (budget 62 KB each); full images (254 KB) load when the viewer opens. Decoded bitmaps are cached LRU: 40 thumbnails, 3 full-size |
| Resize | Window or rotation changes size class | Layout re-derives columns from `WindowSizeClass` live |

## 4. Database

- N/A: content is one JSON file in the repo, versioned by git

## 5. Real-world testing

| Check | Status |
|---|---|
| 375 px phone, light | [x] checked in a browser: compact nav menu, single column |
| 768 px tablet | [x] two-column cards, inline nav, stacked headers |
| 1024 px+ desktop, dark | [x] header rail beside two-column cards |
| Live resize between size classes | [x] |
| Reduced motion | [x] reveals and skeleton pulse disabled |
| Safari / Firefox | [ ] not yet run. Trigger: before sharing the link |
| Throttled 3G | [ ] static HTML covers first paint; measure time-to-app. Trigger: before sharing the link |
| Screen reader on the Compose canvas | [ ] Compose web accessibility is limited; the static HTML is the accessible version. Trigger: before sharing the link |

Automated: Lighthouse CI on every deploy (SEO and best practices ≥ 0.9 fail the job; accessibility and
performance warn).

## 6. After launch

- [x] Deploy on every push to `main`: a content fix is live in minutes
- [x] Lighthouse report kept as a workflow artifact on each deploy
- [ ] Privacy-friendly visit counts (for example GoatCounter). Trigger: after the link is first shared
- [ ] Custom domain (`CNAME`). Trigger: when a domain is bought

## Not done

- Android / iOS targets. Trigger: when an app-store demo is wanted
- Custom font. The default font is used; bundling Inter (as in The Eagle's Watch) is a small follow-up
- `og:image` for link previews. Trigger: when there is artwork for it
