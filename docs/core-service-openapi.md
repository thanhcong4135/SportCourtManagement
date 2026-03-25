# Core Service OpenAPI

## Endpoints
- OpenAPI JSON: `http://localhost:8081/api-docs`
- Swagger UI: `http://localhost:8081/swagger-ui.html`

## Purpose
- OpenAPI is the API contract source of truth.
- Swagger UI is used for interactive API browsing/testing.

## Recommended workflow
1. Update endpoint/DTO code.
2. Verify docs at Swagger UI and `/api-docs`.
3. Keep frontend/gateway integration aligned with OpenAPI contract.

## Auth model (JWT)
- Core service is configured as OAuth2 Resource Server (Bearer JWT).
- Token is validated via JWKS (`app.security.jwt.jwk-set-uri`) and issuer check (`app.security.jwt.issuer-uri`).
- Role claim: `roles` (example: `["CUSTOMER"]`, `["ADMIN"]`, `["OWNER"]`).
- Subject (`sub`) must be a UUID.

### Role policy
- Public:
  - `GET /api/core/venues`
  - `GET /api/core/courts`
  - `GET /api/core/availability`
  - OpenAPI/Swagger and actuator health/info
- `ADMIN` or `OWNER`:
  - `POST /api/core/venues`
  - `POST /api/core/courts`
- `CUSTOMER`, `OWNER`, `ADMIN`:
  - all `/api/core/bookings/**`

### Booking ownership rule
- For role `CUSTOMER`, booking actions are constrained to that user's own bookings (`sub` == booking.customerId).
- For role `CUSTOMER`, create draft APIs ignore request `customerId` and use token `sub`.

### Example JWT claims
```json
{
  "sub": "11111111-1111-1111-1111-111111111111",
  "roles": ["CUSTOMER"],
  "iat": 1760000000,
  "exp": 1760003600
}
```
