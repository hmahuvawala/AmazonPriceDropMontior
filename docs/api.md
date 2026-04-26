# REST API

No authentication. JSON in/out. Errors via [GlobalExceptionHandler](../src/main/java/com/amazonpricemonitor/web/GlobalExceptionHandler.java).

## Endpoints

| Method | Path | Controller | Service |
|---|---|---|---|
| GET | `/api/products` | [ProductController.listProducts](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L30) | `ProductCatalogService.listProducts` |
| POST | `/api/products` | [ProductController.createProduct](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L36) | `ProductCatalogService.createProduct` |
| DELETE | `/api/products/{id}` | [ProductController.deleteProduct](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L42) | `ProductCatalogService.deleteProduct` |
| GET | `/api/products/{id}/price-history` | [ProductController.priceHistory](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L47) | `ProductCatalogService.priceHistory` |
| POST | `/api/admin/run-checks` | [AdminController.runChecksNow](../src/main/java/com/amazonpricemonitor/web/AdminController.java) | `PriceMonitoringService.runChecksForActiveProducts` |
| POST | `/api/admin/send-test-notification` | [AdminController.sendTestNotification](../src/main/java/com/amazonpricemonitor/web/AdminController.java) | Synthetic `Notifier.notifyPriceDrop` (no DB writes). **404** unless `app.admin.allow-test-notification=true` (`ADMIN_ALLOW_TEST_NOTIFICATION`). |
| GET | `/api/admin/scheduler-settings` | [AdminController.getSchedulerSettings](../src/main/java/com/amazonpricemonitor/web/AdminController.java) | `SchedulerSettingsService.getCheckIntervalMs` |
| PUT | `/api/admin/scheduler-settings` | [AdminController.putSchedulerSettings](../src/main/java/com/amazonpricemonitor/web/AdminController.java) | `SchedulerSettingsService.updateCheckIntervalMs` |

### Status codes
- `POST /api/products` → 201 (`@ResponseStatus(CREATED)`).
- `DELETE /api/products/{id}` → 204 (`@ResponseStatus(NO_CONTENT)`).
- `POST /api/admin/run-checks` → 202 (`@ResponseStatus(ACCEPTED)`) but the call is **synchronous** — the request thread blocks while every active product is scraped and persisted. Long-running for many products.
- `POST /api/admin/send-test-notification` → **404** when `ADMIN_ALLOW_TEST_NOTIFICATION` is not `true`. When enabled → **202** JSON `{ "status": "dispatched", "hint": "..." }` — fires one synthetic alert through the real notifier (log / email / SMS); does **not** change `price_check` rows. Use only on trusted networks (no auth).
- `GET /api/admin/scheduler-settings` → 200 JSON `{ "checkIntervalMs": 3600000 }` (delay after each **completed** scheduled run until the next; not the 60s boot delay).
- `PUT /api/admin/scheduler-settings` → 200 with the same shape. Body: `{ "checkIntervalMs": <long> }` validated to **60_000 ≤ x ≤ 604_800_000** (1 minute … 7 days). The background scheduler reads this from the DB when scheduling the **next** cycle (the run already sleeping is unchanged until it fires).
- `EntityNotFoundException` → 404 `{"message": "..."}`.
- `MethodArgumentNotValidException` / `ConstraintViolationException` → 400 `{"message": "..."}`.

## DTOs

### [CreateProductRequest](../src/main/java/com/amazonpricemonitor/web/dto/CreateProductRequest.java) (input)
```json
{
  "amazonUrl":         "https://www.amazon.com/dp/...", // @NotBlank, @Size max 2048
  "displayName":       "Kitchen scale",                  // @NotBlank, @Size max 512 (trimmed on save)
  "thresholdPct":      5.0,                            // optional; if set: 0.01 ≤ x ≤ 100.00
  "thresholdAmount":   10.00,                          // optional; if set: ≥ 0.01 (max per @DecimalMax)
  "active":            true                            // defaults to true
}
```
**At least one** of `thresholdPct` or `thresholdAmount` must be present (`@AssertTrue isAtLeastOneThresholdSet`). If both are set, notifier alerts when **either** condition is met (OR semantics) vs the previous successful price.

Validated via `@Valid` on the controller. `displayName` and `amazonUrl` are `.trim()`-ed before persistence.

### [ProductResponse](../src/main/java/com/amazonpricemonitor/web/dto/ProductResponse.java) (record)
```json
{ "id": 1, "amazonUrl": "...", "displayName": "...", "thresholdPct": 5.00, "thresholdAmount": 10.00,
  "active": true, "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "...",
  "lastPrice": 29.99, "lastPriceCurrency": "USD" }
```
Either threshold field may be `null` when only the other was configured. `lastPrice` / `lastPriceCurrency` are the latest **successful** check for that product (`null` until the first successful scrape). On `POST /api/products` (create) both are always `null`.

### Scheduler settings (JSON)

```json
{ "checkIntervalMs": 3600000 }
```

Used by `GET` / `PUT` `/api/admin/scheduler-settings`. Milliseconds between the end of one scheduled run and the start of the next (fixed-delay). Independent of the **60s** first-run delay after boot and the SPA’s **3s** post-add manual trigger.

### [PriceHistoryPointResponse](../src/main/java/com/amazonpricemonitor/web/dto/PriceHistoryPointResponse.java) (record)
```json
{ "checkedAt": "2026-01-01T00:00:00Z",
  "price": 19.99,                  // null on failure rows
  "method": "JSOUP",                // JSOUP | ALTERLAB | FAILED
  "success": true,
  "errorMessage": null }
```
Capped at the most recent **250** rows, ordered ascending by `createdAt` ([ProductCatalogService.HISTORY_LIMIT](../src/main/java/com/amazonpricemonitor/service/ProductCatalogService.java#L20)).

## Notes

- No update/PATCH endpoint — clients can only create or delete; mutating `active`, threshold, or label means delete + recreate (and lose history).
- No pagination on `GET /api/products`; entire table is returned.
- `GlobalExceptionHandler` does not handle generic `Exception`, so unexpected errors surface as Spring's default 500 with stack trace logging.
- The error payload is a flat `{"message": "..."}` map — not an RFC 7807 problem-details body. Validation messages are whatever the framework produces (verbose).
