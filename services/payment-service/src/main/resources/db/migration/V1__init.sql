CREATE TABLE payment_transaction (
  id BINARY(16) NOT NULL,
  booking_id BINARY(16) NOT NULL,
  customer_id BINARY(16) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  provider_reference VARCHAR(128) NULL,
  requested_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  failure_reason VARCHAR(512) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_transaction_idempotency (idempotency_key),
  KEY idx_payment_transaction_booking_id (booking_id)
);
