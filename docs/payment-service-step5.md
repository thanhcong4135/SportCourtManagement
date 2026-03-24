# Payment Service - Step 5

## Scope
- Build `payment-service` as a dedicated microservice for deposit processing.
- Consume booking draft events from Kafka (`booking.events`) and create deposit transactions.
- Expose payment APIs for initiate/callback/query.
- Publish deposit result events to Kafka (`payment.events`).

## Main APIs
- `POST /api/payments/deposits/initiate`
  - Create deposit payment transaction (idempotent by `idempotencyKey`).
- `POST /api/payments/callback`
  - Mark payment `SUCCESS`/`FAILED` and publish event.
  - Requires header `X-Payment-Callback-Secret` if `payment.callback.shared-secret` is configured.
- `GET /api/payments/{paymentId}`
  - Get payment by id.
- `GET /api/payments/booking/{bookingId}`
  - List payments by booking.

## Kafka contracts
- Consume topic: `booking.events`
  - Interested type: `BOOKING_DRAFT_CREATED`
  - Creates deposit amount = `priceTotal * payment.deposit.ratio`.
- Publish topic: `payment.events`
  - Types: `DEPOSIT_SUCCEEDED`, `DEPOSIT_FAILED`
  - Uses `bookingId` as Kafka key.

## Run locally
1. Start infra:
   - `docker compose -f infra/docker/docker-compose.yml up -d mysql-payment kafka`
2. Run service:
   - `cd services/payment-service`
   - `mvn spring-boot:run`
3. Swagger:
   - `http://localhost:8083/swagger-ui.html`

## Notes
- DB migration: `services/payment-service/src/main/resources/db/migration/V1__init.sql`.
- DB migration provider fields: `services/payment-service/src/main/resources/db/migration/V2__add_provider_and_checkout_url.sql`.
- DB migration outbox: `services/payment-service/src/main/resources/db/migration/V3__outbox_event.sql`.
- Payment provider da duoc tach qua abstraction `PaymentProviderClient` (hien tai co `MOCK`, co the bo sung VNPay/Stripe sau).
- API response payment tra them:
  - `provider`
  - `checkoutUrl`
- Payment service da bat JWT auth cho API nghiep vu (`initiate`, `get`, `list`) va giu callback endpoint theo shared secret de tiep nhan webhook provider.
- Event `payment.events` da publish qua outbox scheduler (khong push truc tiep trong callback transaction).
