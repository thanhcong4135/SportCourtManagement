# Core-Payment Cross-Service E2E

## Purpose
- Verify real end-to-end flow across services:
  - `core-service` creates booking draft and publishes `booking.events`.
  - `payment-service` consumes event and creates deposit transaction.
  - payment callback publishes `payment.events`.
  - `core-service` consumes `payment.events` and updates booking.

## Preconditions
- Kafka + MySQL containers are running:
  - `docker compose -f infra/docker/docker-compose.yml up -d mysql-core mysql-payment kafka`
- `core-service` is running on `http://localhost:8081`.
- `payment-service` is running on `http://localhost:8083`.
- `auth-service` is running on `http://localhost:8082` (JWKS endpoint for JWT validation).

## Run
From repo root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-core-payment.ps1
```

If your JWT key settings are different from default:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-core-payment.ps1 `
  -JwtIssuer "<your-issuer>" `
  -JwtKid "<your-kid>" `
  -JwtPrivateKeyPath "<path-to-rs256-private-key.pem>"
```

If callback secret is different from default:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-core-payment.ps1 -PaymentCallbackSecret "<your-callback-secret>"
```

## What script does
- Creates an OWNER JWT (RS256) using local private key.
- Uses JWT for payment query API (`GET /api/payments/booking/{bookingId}`).
- Calls:
  - `POST /api/core/venues`
  - `POST /api/core/courts`
  - `POST /api/core/bookings/draft`
- Polls `GET /api/payments/booking/{bookingId}` until payment transaction exists.
- Calls `POST /api/payments/callback` with header `X-Payment-Callback-Secret`.
- Polls `GET /api/core/bookings/{bookingId}` until:
  - `status = CONFIRMED`
  - `paymentStatus = DEPOSITED`
- Verifies `GET /api/core/availability` returns `available=false` for booked slot.

## Expected result
- Console prints `E2E cross-service SUCCESS`.
- Shows `bookingId`, `paymentId`, and final booking statuses.
