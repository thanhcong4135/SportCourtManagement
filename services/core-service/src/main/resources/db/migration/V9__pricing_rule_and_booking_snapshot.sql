ALTER TABLE booking
  ADD COLUMN customer_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN price_snapshot_json TEXT NULL;

CREATE TABLE pricing_rule (
  id BINARY(16) PRIMARY KEY,
  court_id BINARY(16) NOT NULL,
  name VARCHAR(255) NOT NULL,
  day_type VARCHAR(16) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  customer_tier VARCHAR(16) NOT NULL,
  price_per_hour DECIMAL(12,2) NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_pricing_rule_court FOREIGN KEY (court_id) REFERENCES court(id),
  CONSTRAINT chk_pricing_rule_time CHECK (end_time > start_time)
) ENGINE=InnoDB;

CREATE INDEX idx_pricing_rule_lookup
  ON pricing_rule(court_id, is_active, customer_tier, day_type, priority);
