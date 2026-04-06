CREATE TABLE dead_letter_event (
  id BINARY(16) NOT NULL,
  source_topic VARCHAR(255) NOT NULL,
  dead_letter_topic VARCHAR(255) NOT NULL,
  kafka_partition INT NOT NULL,
  kafka_offset BIGINT NOT NULL,
  event_key VARCHAR(255) NULL,
  event_id VARCHAR(128) NULL,
  payload TEXT NOT NULL,
  failure_reason VARCHAR(1024) NULL,
  status VARCHAR(32) NOT NULL,
  replay_count INT NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  last_replayed_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dead_letter_topic_partition_offset (dead_letter_topic, kafka_partition, kafka_offset),
  KEY idx_dead_letter_status_received (status, received_at)
);
