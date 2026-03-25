CREATE TABLE booking_action_idempotency (
  id BINARY(16) PRIMARY KEY,
  booking_id BINARY(16) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint VARCHAR(128) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_booking_action_idempotency_booking
    FOREIGN KEY (booking_id) REFERENCES booking(id)
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uk_booking_action_idempotency
  ON booking_action_idempotency(booking_id, action_type, idempotency_key);
