-- Add payment fields to booking (MySQL)
ALTER TABLE booking
  ADD COLUMN payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
  ADD COLUMN deposit_required DECIMAL(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN deposit_paid DECIMAL(12,2) NOT NULL DEFAULT 0;
