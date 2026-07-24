# Notification V2

Notification V2 supports:

- customer `IN_APP` notifications with unread state;
- transactional `EMAIL` notifications for important booking and payment events;
- schema `1.0` consumers for backward compatibility and schema `1.1` email snapshots;
- retry/backoff and admin retry through the existing notification operations API.

## Customer API

All customer endpoints derive the customer ID from the JWT `sub` claim.

```text
GET   /api/notifications/me
GET   /api/notifications/me/unread-count
PATCH /api/notifications/me/{notificationId}/read
PATCH /api/notifications/me/read-all
```

Only successfully delivered `IN_APP` records owned by the authenticated customer
are returned.

## Email configuration

Email delivery is disabled by default outside Docker. Configure these variables
for an SMTP provider:

```text
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@sportcourt.vn
MAIL_FROM_NAME=SportCourt
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLED=true
MAIL_CONNECTION_TIMEOUT_MS=5000
MAIL_TIMEOUT_MS=10000
APP_FRONTEND_BASE_URL=https://app.example.com
```

Do not commit SMTP credentials. The notification service masks recipient addresses
in logs and does not log rendered email bodies.

## Local Mailpit

Docker Compose configures notification-service to use Mailpit on the local stack:

- SMTP: `localhost:1025`
- UI: `http://localhost:8025`

The repository-level `.env` contains the required local payment settings. Because
the Compose file lives under `infra/docker`, pass that env file explicitly:

```powershell
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --build `
  kafka mysql-notification mailpit notification-service auth-service api-gateway
```

Mailpit is a development inbox. It captures messages instead of delivering them to
real recipients.

## Transactional email allowlist

Email is created only when a valid email snapshot is present for:

- `BOOKING_CONFIRMED`

This final booking event is emitted after a successful deposit is applied and the
booking is confirmed. Intermediate payment/deposit events and all other supported
events continue to create `IN_APP` notifications only, preventing multiple emails
for one completed booking flow.
