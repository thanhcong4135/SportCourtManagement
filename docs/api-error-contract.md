# API Error Contract

The services now use one common error shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "startTime", "message": "must not be null" }
  ],
  "traceId": "c1e4f1ab-5a6f-4d22-9d7a-5c3fc9ccf8d5",
  "timestamp": "2026-03-15T16:40:30.619+07:00",
  "status": 400,
  "path": "/api/core/bookings/draft",
  "error": "Bad Request"
}
```

## Notes

- `traceId` is propagated by header `X-Trace-Id`.
- If client does not send `X-Trace-Id`, service/gateway generates one.
- `details` is used mainly for validation errors; other errors can return `null`.
- Domain/business errors can expose dedicated `code` values for deterministic UI handling.
  - Example in `core-service`: `PRICING_RULE_MISSING` with HTTP `422` and detail:
    - `{"field":"missingSlotStart","message":"2026-04-17T18:30+07:00"}`
- `core-service` keeps response envelope:
  - success: `{"success": true, "data": ..., "error": null}`
  - error: `{"success": false, "data": null, "error": { ...contract above... }}`
- `auth-service`, `payment-service`, `api-gateway` return the error contract directly.
