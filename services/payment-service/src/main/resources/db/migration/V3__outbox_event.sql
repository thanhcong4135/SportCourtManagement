CREATE TABLE outbox_event (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  topic VARCHAR(255) NOT NULL,
  event_key VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL,
  next_attempt_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  sent_at TIMESTAMP(6) NULL,
  last_error VARCHAR(1024) NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_event_status_next_attempt (status, next_attempt_at, created_at),
  KEY idx_outbox_event_aggregate (aggregate_type, aggregate_id)
);
