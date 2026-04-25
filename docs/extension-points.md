# Extension points

Common changes and where they land. Cross-references the per-area docs so you don't have to re-derive structure each time.

## Add a new product field (e.g. `category`, `notes`)

1. New Flyway migration `V2__add_<field>.sql` (`ALTER TABLE monitored_product ...`). See [domain-and-db.md](domain-and-db.md).
2. Add field + getter/setter to [MonitoredProduct](../src/main/java/com/amazonpricemonitor/domain/MonitoredProduct.java).
3. Add to [CreateProductRequest](../src/main/java/com/amazonpricemonitor/web/dto/CreateProductRequest.java) (with validation) and [ProductResponse](../src/main/java/com/amazonpricemonitor/web/dto/ProductResponse.java) record.
4. Wire it through [ProductCatalogService.createProduct](../src/main/java/com/amazonpricemonitor/service/ProductCatalogService.java#L39).
5. Update SPA form / list rendering in [js/app.js](../src/main/resources/static/js/app.js).

## Add a PATCH/PUT endpoint to update products

There is no update path today — clients delete + recreate. To add one:
- New `UpdateProductRequest` DTO (probably partial; remember `@Valid`).
- New `@PutMapping("/{id}")` on `ProductController`.
- Service method that fetches, mutates, returns; `MonitoredProduct.@PreUpdate` will set `updatedAt`.
- SPA: replace "Remove" with an "Edit" affordance in `renderProductList`.

## Add a new fetcher / change priority

[CompositePriceFetcher](../src/main/java/com/amazonpricemonitor/service/CompositePriceFetcher.java) hardcodes Jsoup → AlterLab. To plug a third source:
- Define a new `@Component` exposing `Optional<PriceQuote> fetch(String)`.
- Add a new constant to [FetchMethod](../src/main/java/com/amazonpricemonitor/domain/FetchMethod.java) — column is `VARCHAR(32)`, no migration needed unless you exceed length.
- Inject it into `CompositePriceFetcher` and chain it.
- Update SPA color cue in `selectProduct` (currently only branches on `ALTERLAB`).

## Multi-currency support

Currently single-currency in practice. To do this right:
- Stop hard-coding `"USD"` in [JsoupAmazonPriceFetcher](../src/main/java/com/amazonpricemonitor/service/JsoupAmazonPriceFetcher.java) (parse currency symbol).
- Drop drop-detection comparison if currencies differ across checks (or convert).
- Stop hard-coding `"USD"` in the [Slack message](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java) — use `quote.currency()` (thread through `Notifier.notifyPriceDrop` if needed).
- `MoneyParsing.fromPlainText` mishandles EU `1.299,95` formats — see [price-fetching.md](price-fetching.md).

## Async admin trigger

`POST /api/admin/run-checks` is annotated `202 Accepted` but is synchronous. To make it actually async:
- Make `runChecksForActiveProducts` `@Async` (and `@EnableAsync` on the application). This conflicts with the current `@Transactional` on the same method — wrap with a separate `@Async` boundary that delegates.
- Or post a marker/queue and let the scheduler pick it up.

## Move Slack out of the DB transaction

Today `Notifier.notifyPriceDrop` (Slack implementation) is called from inside the `@Transactional` `runChecksForActiveProducts`. A slow webhook holds the transaction. Options:
- Persist a "pending alert" row, send from a separate scheduled task.
- Spring `ApplicationEvent` published after commit (`@TransactionalEventListener(AFTER_COMMIT)`).

## Change the alert threshold semantics

Comparison and threshold lives in [PriceMonitoringService.checkSingleProduct](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java#L54). Things you may want:
- Compare against a rolling window (e.g. lowest in last N days) instead of last successful.
- Add hysteresis so you don't re-alert on every check while price stays low.
- Cap alert frequency per product per day.

## Add tests

There are none beyond `contextLoads`. Reasonable starter set:
- `MoneyParsingTest` — pure unit, lots of edge cases.
- `PriceMonitoringServiceTest` — `@DataJpaTest` or service-level test with mocked `CompositePriceFetcher` and `Notifier`.
- `JsoupAmazonPriceFetcherTest` — feed canned HTML through `Jsoup.parse(...)` against a fake `Connection`; or extract a seam.
- Web-layer slice tests with `@WebMvcTest(ProductController.class)`.

## Auth / hardening

The README explicitly calls this a prototype. Before exposing publicly:
- Add Spring Security; protect `/api/**` (and at minimum `/api/admin/**`).
- Validate the Amazon URL host on create.
- Move the AlterLab key + Slack URL out of env vars at runtime if you're not running this on a single trusted box.
