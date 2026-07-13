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
  - Optional HMAC headers when enabled:
    - `X-Payment-Signature`
    - `X-Payment-Timestamp` (unix epoch seconds)
- `GET /api/payments/{paymentId}`
  - Get payment by id.
- `GET /api/payments/booking/{bookingId}`
  - List payments by booking.
- `POST /api/payments/vnpay/create-payment`
  - Create idempotent VNPAY sandbox deposit transaction and return signed checkout URL.
- `GET /api/payments/vnpay/ipn`
  - Public server-to-server callback from VNPAY.
  - Verify merchant code, HMAC SHA512 signature and amount before updating payment and enqueueing outbox event.
- `GET /api/payments/vnpay/return`
  - Public browser redirect callback.
  - Verify return signature and redirect user to frontend payment result page without updating DB.
- `GET /api/payments/by-ref/{paymentRef}`
  - Get VNPAY transaction status for frontend polling.

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
- DB migration VNPay fields: `services/payment-service/src/main/resources/db/migration/V5__add_vnpay_fields.sql`.
- DB migration expanded checkout URL: `services/payment-service/src/main/resources/db/migration/V6__expand_checkout_url.sql`.
- Payment provider da duoc tach qua abstraction `PaymentProviderClient`; flow hien tai ho tro `MOCK` va VNPAY sandbox.
- API response payment tra them:
  - `provider`
  - `checkoutUrl`
- Payment service da bat JWT auth cho API nghiep vu (`initiate`, `get`, `list`) va giu callback endpoint theo shared secret de tiep nhan webhook provider.
- Callback co che signature da san sang cho sandbox provider qua config:
  - `payment.callback.signature.enabled`
  - `payment.callback.signature.secret`
  - `payment.callback.signature.max-skew-seconds`
- Event `payment.events` da publish qua outbox scheduler (khong push truc tiep trong callback transaction).
- VNPAY IPN va return URL co vai tro khac nhau:
  - IPN la callback server-to-server de cap nhat payment chinh thuc.
  - Return URL chi redirect user ve frontend, khong cap nhat DB.
- Huong dan cau hinh va test sandbox: `docs/vnpay-sandbox-dev.md`.
- Outbox observability:
  - metrics: `outbox.events.pending`, `outbox.events.failed`
  - health indicator: `outbox` (`/actuator/health/outbox`)
