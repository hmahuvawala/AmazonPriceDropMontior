# Frontend

Three static files under [src/main/resources/static/](../src/main/resources/static/), served by Spring Boot's default static handler at `/` (index.html), `/css/app.css`, `/js/app.js`. No build step, no framework, no bundler.

## [index.html](../src/main/resources/static/index.html)

- Loads **Inter** from Google Fonts and Chart.js 4.4.6 from `cdn.jsdelivr.net` (CDN — offline breaks the chart).
- Shell: `.app` → `.header` + `.main` grid. Three `<section class="panel">` blocks: products list (`.product-list-scroll` fixed **300px** height, scrollable list), add form, full-width price history.
- Section labels use `.panel__heading` (small caps, muted). Add form: URL, required **Label** (`displayName`), side-by-side **%** / **$** thresholds (at least one required — server-side), single-line hint, **Add** button.
- Chart area: toolbar with **Checks every** (minutes, 1–10080), **Save interval** (`GET`/`PUT` `/api/admin/scheduler-settings`), **Run checks**; placeholder “Click a listing to view the chart.” until a product is chosen.

## [js/app.js](../src/main/resources/static/js/app.js)

Single-file vanilla JS. `state = { products, selectedId, chart }`.

- `fetchJson` — same-origin `fetch` + JSON; errors throw with response body text.
- `formatThresholdSummary` — compact `5% · $10.00` (or `—` if missing).
- `formatLastPrice` — latest successful price from API (`lastPrice` + `lastPriceCurrency`), currency-formatted; `—` if none yet.
- `renderProductList` — each card is clickable (`<li role="button" tabindex="0">`): opens the chart; **Remove** stops propagation so delete does not select. Right column: last price + **Remove** only.
- `selectProduct` — history API, Chart.js line (legend off, short date ticks). Point colors: AlterLab vs Jsoup.
- Form submit — optional `thresholdPct` / `thresholdAmount` in body; success message **Added.**, **`loadProducts()`**, then **`setTimeout` 3s** → same flow as **Run checks** (`runChecksAndRefresh`: `POST /api/admin/run-checks`, `loadProducts`, **Checks complete.**, `selectProduct` if selected).
- **Run checks** — calls **`runChecksAndRefresh()`** (same as the delayed post-add run).
- **Save interval** — `PUT /api/admin/scheduler-settings` with `checkIntervalMs` from the minutes field; **Saved.** / errors on `#scheduler-settings-message`. Page load runs **`loadSchedulerSettings()`** beside `loadProducts()`.

## [css/app.css](../src/main/resources/static/css/app.css)

Light-first palette with `prefers-color-scheme: dark` variables: white/dark surfaces, subtle borders, terracotta accent. **Inter** only for UI.

- `.main` is CSS grid; two columns from ~880px; `.panel--wide` spans full width.
- Buttons: `.btn--primary`, `.btn--secondary`, `.btn--danger`.
- `.product-list-scroll`: fixed height, `overflow-y: auto` for many listings.
- Chart container height ~280px.

## Conventions / things to know before changing UI

- No auth; same-origin API.
- No debounce on submit or run checks.
- Chart plots successful checks only; `FAILED` rows filtered client-side.
- `checkedAt` rendered with `toLocaleString` short date + time.
- Chart.js + `app.js` both `defer`; global `Chart` is available when user opens a chart.
