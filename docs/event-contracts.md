# Event Contracts (Kafka)

## Scope

- `booking.events` (producer: `core-service`, consumer: `payment-service`)
- `payment.events` (producer: `payment-service`, consumer: `core-service`)

## Contract versioning

- Event payload co field `schemaVersion`.
- Version hien tai: `1.0`.
- Rule compatibility:
  - Consumer cho phep `schemaVersion` rong/null (backward compatibility cho event cu).
  - Consumer chi chap nhan explicit version `1.0`.
  - Event version moi (`2.0`, ...) phai tang theo chinh sach rollout:
    1. producer publish dual-version (neu can),
    2. consumer ho tro version moi,
    3. moi remove support version cu.

## booking.events (v1.0)

Required fields:
- `schemaVersion`
- `eventId`
- `type`
- `bookingId`
- `customerId`
- `priceTotal`

Common optional fields:
- `courtId`
- `status`
- `paymentStatus`
- `startTime`
- `endTime`
- `occurredAt`

## payment.events (v1.0)

Required fields:
- `schemaVersion`
- `eventId`
- `type`
- `paymentId`
- `bookingId`

Common optional fields:
- `customerId`
- `amount`
- `providerReference`
- `occurredAt`

## Validation in code

- `payment-service` reject unsupported `booking.events.schemaVersion`.
- `core-service` reject unsupported `payment.events.schemaVersion`.
