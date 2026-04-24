# Frontend

Three static files under [src/main/resources/static/](../src/main/resources/static/), served by Spring Boot's default static handler at `/` (index.html), `/css/app.css`, `/js/app.js`. No build step, no framework, no bundler.

## [index.html](../src/main/resources/static/index.html)

- Loads Chart.js 4.4.6 from `cdn.jsdelivr.net` (CDN dependency — offline use breaks chart rendering).
- Three `<section class="panel">` regions: tracked products list, add-product form, price-history chart with a "Run checks now" button.
- The chart canvas is `<canvas id="price-chart" height="120">` inside a `.chart-wrap` (`height: 320px` from CSS, responsive width).

## [js/app.js](../src/main/resources/static/js/app.js)

Single-file vanilla JS, no modules. Holds a tiny `state = { products, selectedId, chart }` object.

Functions:
- `fetchJson(url, options)` — wrapper around `fetch` with JSON content-type, throws `Error(detail||statusText)` on non-OK, returns `null` on 204, parses JSON otherwise.
- `setMessage(text, isError)` — writes into `#form-message`, toggles `.form-message--error` class.
- `renderProductList()` — rebuilds `#product-list`; per-item buttons "History" (calls `selectProduct`) and "Remove" (calls `deleteProduct` after `window.confirm`).
- `loadProducts()` — `GET /api/products` then re-render.
- `selectProduct(id)` — `GET /api/products/{id}/price-history`, filters to `success && price != null`, builds Chart.js line dataset. Each point colored by method: `ALTERLAB` purple-ish, anything else green-ish (no special color for `JSOUP` vs others; only `ALTERLAB` is checked explicitly). Destroys prior chart before creating a new one.
- `deleteProduct(id)` — `DELETE /api/products/{id}`, clears selection/chart if it was the active one, reloads list.
- Form submit (`#add-form`) — builds payload (`amazonUrl`, `displayName||null`, `Number(thresholdPct)`, `active: true`), `POST /api/products`, resets form (and resets threshold to `5`), reloads.
- "Run checks now" (`#run-checks`) — `POST /api/admin/run-checks`, then re-selects current product to refresh chart.

Initial bootstrap: `loadProducts()` at the bottom; errors surface via `setMessage(..., true)`.

## [css/app.css](../src/main/resources/static/css/app.css)

Dark-themed via CSS variables under `:root`:
- `--bg`, `--panel`, `--text`, `--muted`, `--accent`, `--danger`.
- `color-scheme: light dark` (form controls render with native dark variant where supported).
- Layout switches to two-column `grid-template-columns: 1fr 1fr` at `min-width: 900px`; the chart panel uses `panel--wide` to span both columns.
- Buttons: default purple gradient, `.secondary` neutral, `.danger` red-tinted.
- No print styles.

## Conventions / things to know before changing UI

- The frontend has zero auth and assumes the JSON API is same-origin.
- There is no debounce on form submit / "Run checks now". The button can be hammered; the backend sync admin endpoint will queue.
- Chart only plots successful checks. `FAILED` rows are returned by the API but filtered out client-side.
- No date/timezone handling beyond `new Date(row.checkedAt).toLocaleString()` (timestamps from API are ISO-8601 UTC).
- Chart.js is loaded via `<script defer>`, and `app.js` references the global `Chart` directly — both scripts are `defer`, so order is preserved and `Chart` is defined by the time `selectProduct` runs.
