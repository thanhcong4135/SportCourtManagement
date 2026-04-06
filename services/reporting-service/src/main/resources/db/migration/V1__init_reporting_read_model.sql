CREATE TABLE projected_event (
  id BINARY(16) PRIMARY KEY,
  event_id VARCHAR(128) NOT NULL,
  source_topic VARCHAR(255) NOT NULL,
  consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_projected_event UNIQUE (event_id, source_topic)
) ENGINE=InnoDB;

CREATE TABLE booking_read_model (
  booking_id BINARY(16) PRIMARY KEY,
  venue_id BINARY(16) NULL,
  court_id BINARY(16) NULL,
  customer_id BINARY(16) NULL,
  status VARCHAR(32) NOT NULL,
  payment_status VARCHAR(32) NULL,
  start_time DATETIME(6) NULL,
  end_time DATETIME(6) NULL,
  price_total DECIMAL(12,2) NOT NULL DEFAULT 0,
  deposit_paid DECIMAL(12,2) NOT NULL DEFAULT 0,
  last_event_type VARCHAR(64) NULL,
  last_occurred_at DATETIME(6) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_booking_rm_date_venue ON booking_read_model(start_time, venue_id);
CREATE INDEX idx_booking_rm_status ON booking_read_model(status);

CREATE TABLE sales_order_read_model (
  order_id BINARY(16) PRIMARY KEY,
  booking_id BINARY(16) NULL,
  venue_id BINARY(16) NULL,
  customer_id BINARY(16) NULL,
  status VARCHAR(32) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  last_event_type VARCHAR(64) NULL,
  last_occurred_at DATETIME(6) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_sales_rm_updated_venue ON sales_order_read_model(updated_at, venue_id);
