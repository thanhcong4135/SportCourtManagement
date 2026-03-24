ALTER TABLE payment_transaction
  ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'MOCK' AFTER idempotency_key,
  ADD COLUMN checkout_url VARCHAR(512) NULL AFTER provider_reference;
