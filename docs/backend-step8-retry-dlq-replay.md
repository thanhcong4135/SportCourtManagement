# Retry / DLQ Replay policy (core-service + payment-service)

## Scope
- Consumer `payment.events` trong `core-service`.
- Consumer `booking.events` trong `payment-service`.
- Retry + DLQ da co, buoc nay bo sung parking-lot va replay API de van hanh end-to-end.

## Policy
1. Consumer retry theo `kafka.consumer.retry.*`.
2. Qua so lan retry -> message duoc day sang topic `*.dlq`.
3. DLQ consumer rieng se ghi message vao bang `dead_letter_event` (trang thai `RECEIVED`).
4. Ops co the replay message qua API:
   - replay thanh cong -> `REPLAYED`
   - replay that bai -> `FAILED`
   - gioi han so lan replay theo `kafka.dlq.replay.max-attempts`.

## New migrations
- `core-service`: `V8__dead_letter_event.sql`
- `payment-service`: `V4__dead_letter_event.sql`

## New endpoints
### Core
- `GET /api/core/ops/dlq?status=RECEIVED&page=0&size=20`
- `POST /api/core/ops/dlq/{id}/replay`

### Payment
- `GET /api/payments/ops/dlq?status=RECEIVED&page=0&size=20`
- `POST /api/payments/ops/dlq/{id}/replay`

## Security
- Core: `/api/core/ops/**` yeu cau role `ADMIN`.
- Payment: `/api/payments/ops/**` yeu cau role `ADMIN`.

## Config keys
- `kafka.consumer.payment-dlq.enabled`
- `kafka.consumer.payment-dlq.group-id`
- `kafka.consumer.booking-dlq.enabled`
- `kafka.consumer.booking-dlq.group-id`
- `kafka.topics.payment-events-dlq`
- `kafka.topics.booking-events-dlq`
- `kafka.dlq.replay.max-attempts`

## Quick verify
1. Tao 1 message loi co chu dich vao `payment.events` hoac `booking.events` de no roi vao DLQ.
2. Kiem tra bang:
   - `select status, count(*) from dead_letter_event group by status;`
3. Goi API list DLQ de lay `id`.
4. Goi API replay theo `id`.
5. Kiem tra lai `dead_letter_event.status` da doi sang `REPLAYED` hoac `FAILED`.
