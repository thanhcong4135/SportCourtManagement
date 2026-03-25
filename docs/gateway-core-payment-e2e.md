# Gateway-Core-Payment E2E

## Purpose

Smoke test full booking + payment callback flow through `api-gateway`:

- create venue/court
- create booking draft
- wait payment transaction from `booking.events`
- callback success
- wait core update from `payment.events`
- verify availability becomes `false`

## Prerequisites

- Docker engine running
- Compose stack up:

```powershell
docker compose -f infra/docker/docker-compose.yml up -d --build
```

- `auth-service` must be reachable so gateway/core/payment can fetch JWKS.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-gateway-core-payment.ps1
```

Custom JWT/callback settings (if changed):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-gateway-core-payment.ps1 `
  -JwtIssuer "<jwt-issuer>" `
  -JwtKid "<jwt-kid>" `
  -JwtPrivateKeyPath "<path-to-rs256-private-key.pem>" `
  -PaymentCallbackSecret "<callback-secret>"
```
