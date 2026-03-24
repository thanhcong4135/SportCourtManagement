# Core Service Reliability (Step 4)

## Scope delivered
- Build/test gate for `core-service`.
- Outbox end-to-end verification test (create draft -> publish -> `SENT`).
- Kafka reliability baseline:
  - Producer idempotence + `acks=all`.
  - Consumer retry policy + DLQ policy (`*.dlq`) via `DefaultErrorHandler`.
- Observability:
  - Metrics: `outbox.events.pending`, `outbox.events.failed`, `outbox.publish.success`, `outbox.publish.failure`.
  - Health indicator: `outbox` with pending/failed counters and oldest pending age.
- Idempotency utility for consumers:
  - `consumed_event` table and `IdempotentEventService`.
  - Optional audit consumer (`kafka.consumer.audit.enabled=false` by default).

## New config keys
- `spring.kafka.producer.acks`
- `spring.kafka.producer.retries`
- `spring.kafka.producer.properties.enable.idempotence`
- `spring.kafka.producer.properties.max.in.flight.requests.per.connection`
- `kafka.consumer.retry.max-attempts`
- `kafka.consumer.retry.backoff-ms`
- `kafka.consumer.dlq.suffix`
- `kafka.consumer.audit.enabled`
- `kafka.consumer.audit.group-id`
- `outbox.monitoring.failed-threshold`

## Quick verify
1. Run tests:
   - `mvn "-Dmaven.repo.local=.m2repo" test`
2. Check actuator:
   - `GET /actuator/metrics/outbox.events.pending`
   - `GET /actuator/metrics/outbox.events.failed`
   - `GET /actuator/health/outbox`
3. Check DB:
   - `select status, count(*) from outbox_event group by status;`
   - `select count(*) from consumed_event;`
