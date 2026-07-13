ALTER TABLE payment_transaction ADD COLUMN payment_ref VARCHAR(128) NULL;
ALTER TABLE payment_transaction ADD COLUMN provider_transaction_no VARCHAR(128) NULL;
ALTER TABLE payment_transaction ADD COLUMN bank_code VARCHAR(64) NULL;
ALTER TABLE payment_transaction ADD COLUMN response_code VARCHAR(16) NULL;
ALTER TABLE payment_transaction ADD COLUMN transaction_status VARCHAR(16) NULL;
ALTER TABLE payment_transaction ADD COLUMN pay_date VARCHAR(32) NULL;
ALTER TABLE payment_transaction ADD COLUMN raw_callback_data TEXT NULL;
ALTER TABLE payment_transaction ADD COLUMN updated_at TIMESTAMP(6) NULL;

CREATE UNIQUE INDEX uk_payment_transaction_payment_ref ON payment_transaction (payment_ref);
