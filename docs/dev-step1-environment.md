# Step 1 - Local Environment Stabilization

## Goal
- Bring up dependency stack consistently.
- Ensure `auth-service` no longer fails Flyway/Hikari due to DB connection.
- Provide one-command check for local environment readiness.

## One command
From repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-step1-up.ps1
```

### If local ports 3306/3307/3308 are occupied

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-step1-up.ps1 -CoreDbPort 13306 -PaymentDbPort 13307 -AuthDbPort 13308
```

Then run services with matching env vars:

```powershell
$env:CORE_DB_PORT="13306"
$env:PAYMENT_DB_PORT="13307"
$env:AUTH_DB_PORT="13308"
```

### Optional: start app containers as well

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-step1-up.ps1 -StartServiceContainers
```

## Notes
- `docker-compose.yml` now uses DB port variables:
  - `MYSQL_CORE_PORT`, `MYSQL_PAYMENT_PORT`, `MYSQL_AUTH_PORT`
- MySQL services include healthcheck.
- `core-service`, `payment-service`, `auth-service` wait for DB `service_healthy` in compose.
