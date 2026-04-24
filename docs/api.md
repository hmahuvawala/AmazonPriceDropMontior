# REST API

No authentication. JSON in/out. Errors via [GlobalExceptionHandler](../src/main/java/com/amazonpricemonitor/web/GlobalExceptionHandler.java).

## Endpoints

| Method | Path | Controller | Service |
|---|---|---|---|
| GET | `/api/products` | [ProductController.listProducts](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L30) | `ProductCatalogService.listProducts` |
| POST | `/api/products` | [ProductController.createProduct](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L36) | `ProductCatalogService.createProduct` |
| DELETE | `/api/products/{id}` | [ProductController.deleteProduct](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L42) | `ProductCatalogService.deleteProduct` |
| GET | `/api/products/{id}/price-history` | [ProductController.priceHistory](../src/main/java/com/amazonpricemonitor/web/ProductController.java#L47) | `ProductCatalogService.priceHistory` |
| POST | `/api/admin/run-checks` | [AdminController.runChecksNow](../src/main/java/com/amazonpricemonitor/web/AdminController.java#L22) | `PriceMonitoringService.runChecksForActiveProducts` |

### Status codes
- `POST /api/products` → 201 (`@ResponseStatus(CREATED)`).
- `DELETE /api/products/{id}` → 204 (`@ResponseStatus(NO_CONTENT)`).
- `POST /api/admin/run-checks` → 202 (`@ResponseStatus(ACCEPTED)`) but the call is **synchronous** — the request thread blocks while every active product is scraped and persisted. Long-running for many products.
- `EntityNotFoundException` → 404 `{"message": "..."}`.
- `MethodArgumentNotValidException` / `ConstraintViolationException` → 400 `{"message": "..."}`.

## DTOs

### [CreateProductRequest](../src/main/java/com/amazonpricemonitor/web/dto/CreateProductRequest.java) (input)
```json
{
  "amazonUrl":    "https://www.amazon.com/dp/...", // @NotBlank, @Size max 2048
  "displayName":  "Optional label",                 // @Size max 512, nullable
  "thresholdPct": 5.0,                              // @NotNull, 0.01 ≤ x ≤ 100.00
  "active":       true                              // defaults to true
}
```
Validated via `@Valid` on the controller. `displayName` is `trimToNull`-ed in the service before persistence; `amazonUrl` is `.trim()`-ed.

### [ProductResponse](../src/main/java/com/amazonpricemonitor/web/dto/ProductResponse.java) (record)
```json
{ "id": 1, "amazonUrl": "...", "displayName": "...", "thresholdPct": 5.00,
  "active": true, "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "..." }
```

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
