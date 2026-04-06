# Notification Service - Sprint 1

## Scope implemented
- Chuyen notification-service tu in-memory mock sang DB + Flyway.
- Them event consumers:
  - `booking.events`
  - `payment.events`
- Queue idempotent theo `sourceEventId + channel + recipient + templateCode`.
- Dispatcher scheduler voi retry + exponential backoff.
- Dead-letter bucket noi bo: `status=FAILED` sau khi vuot max attempts.
- Bo sung API lich su theo booking/customer va retry thu cong.

## Database
- Migration: `services/notification-service/src/main/resources/db/migration/V1__init.sql`
- Table: `notification_message`

## APIs
- `POST /api/notifications/send`
- `GET /api/notifications/{notificationId}`
- `GET /api/notifications?bookingId=&customerId=&status=&page=&size=`
- `POST /api/notifications/{notificationId}/retry`

## Config keys
- `notification.dispatcher.fixed-delay-ms`
- `notification.dispatcher.batch-size`
- `notification.dispatcher.max-attempts`
- `notification.dispatcher.initial-backoff-ms`
- `notification.dispatcher.max-backoff-ms`
- `notification.delivery.mock.fail-recipient-pattern`
- `kafka.consumer.booking.*`
- `kafka.consumer.payment.*`
- `kafka.consumer.retry.*`
- `kafka.consumer.dlq.suffix`

## Verify
1. Run notification-service.
2. Publish booking/payment event co `eventId` va `customerId`.
3. Kiem tra `notification_message` co ban ghi `QUEUED` -> `SENT`.
4. Set `notification.delivery.mock.fail-recipient-pattern` de buoc send loi.
5. Kiem tra record sang `FAILED` sau max retry va goi API retry.
