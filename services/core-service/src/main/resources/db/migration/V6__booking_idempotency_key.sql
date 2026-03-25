ALTER TABLE booking
  ADD COLUMN idempotency_key VARCHAR(128) NULL;

CREATE UNIQUE INDEX uk_booking_customer_idempotency
  ON booking(customer_id, idempotency_key);
