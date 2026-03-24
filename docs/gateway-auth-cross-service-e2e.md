# Gateway + Auth E2E

Script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-gateway-auth.ps1
```

## What it verifies

1. Register and login through `api-gateway` -> `auth-service`.
2. Call `/api/auth/me` with JWT through gateway.
3. Security contract on gateway:
   - anonymous call to `/api/core/bookings` -> `401`
   - `CUSTOMER` call to `/api/core/bookings` -> `200`
   - `CUSTOMER` call to `POST /api/core/venues` -> `403`
4. (Default) Promote the test user to `OWNER` via auth admin API and verify:
   - `OWNER` can create venue/court through gateway.
5. Refresh token rotation + logout revoke flow:
   - refresh works and rotates token
   - revoked refresh token is rejected (`401`)

## Required services

- `api-gateway` on `http://localhost:8080`
- `auth-service` on `http://localhost:8082`
- `core-service` on `http://localhost:8081`

## Optional flags

Skip owner role scenario:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-gateway-auth.ps1 -SkipOwnerScenario
```

Use custom base URLs / JWT secret:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-gateway-auth.ps1 `
  -GatewayBaseUrl "http://localhost:8080" `
  -AuthBaseUrl "http://localhost:8082" `
  -CoreBaseUrl "http://localhost:8081" `
  -JwtSecret "<your-dev-secret>"
```
