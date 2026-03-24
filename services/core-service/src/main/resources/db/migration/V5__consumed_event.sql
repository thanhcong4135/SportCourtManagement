CREATE TABLE consumed_event (
  id BINARY(16) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  topic VARCHAR(255) NOT NULL,
  consumed_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_consumed_event_event_id (event_id)
);
