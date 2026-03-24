# Core Service Outbox Pattern

## Why
- Prevent data/event inconsistency when DB commit succeeds but Kafka publish fails.
- Ensure booking state changes and emitted events are transactionally aligned.

## Current flow
1. Business transaction updates `booking`.
2. Same transaction inserts `outbox_event` with `status=PENDING`.
3. `OutboxPublisherScheduler` polls pending rows and publishes to Kafka (with `event-id` header).
4. Publish success -> `status=SENT`, set `sent_at`.
5. Publish failure -> retry with backoff; after max retries -> `status=FAILED`.

## Key components
- `BookingOutboxService`: writes booking events to outbox.
- `OutboxEvent` + `OutboxEventRepository`: persistence model.
- `OutboxPublisherScheduler`: background publisher with retry.
- `BookingEventPublisher.publishRaw(...)`: Kafka producer used by scheduler.

## Config (application.yml)
- `outbox.publisher.fixed-delay-ms`
- `outbox.publisher.batch-size`
- `outbox.publisher.max-retries`
- `outbox.publisher.initial-retry-delay-seconds`
- `outbox.publisher.max-retry-delay-seconds`

## Table
- Flyway migration: `V4__outbox_event.sql`
- Main columns: `topic`, `event_key`, `payload`, `status`, `retry_count`, `next_attempt_at`, `sent_at`.

## Downstream idempotency note
- Booking event payload now carries `eventId`.
- Consumers should deduplicate by `eventId`.
- Reference table for dedup in core-service: Flyway `V5__consumed_event.sql`.
