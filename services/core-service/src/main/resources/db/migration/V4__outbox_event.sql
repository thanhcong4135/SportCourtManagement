CREATE TABLE outbox_event (
  id BINARY(16) PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  topic VARCHAR(255) NOT NULL,
  event_key VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  sent_at DATETIME(6) NULL,
  last_error VARCHAR(1024) NULL
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_status_next_attempt ON outbox_event(status, next_attempt_at, created_at);
