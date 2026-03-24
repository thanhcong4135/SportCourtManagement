-- Core schema init for MySQL 8+
CREATE TABLE venue (
  id BINARY(16) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  address VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE court (
  id BINARY(16) PRIMARY KEY,
  venue_id BINARY(16) NOT NULL,
  name VARCHAR(255) NOT NULL,
  sport_type VARCHAR(32) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_court_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
) ENGINE=InnoDB;

CREATE TABLE booking (
  id BINARY(16) PRIMARY KEY,
  court_id BINARY(16) NOT NULL,
  customer_id BINARY(16) NULL,
  status VARCHAR(32) NOT NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  price_total DECIMAL(12,2) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_booking_court FOREIGN KEY (court_id) REFERENCES court(id),
  CONSTRAINT chk_booking_time CHECK (end_time > start_time)
) ENGINE=InnoDB;

-- Useful indexes for overlap checks and listing
CREATE INDEX idx_booking_court_time ON booking(court_id, start_time, end_time);
CREATE INDEX idx_booking_status_time ON booking(status, start_time);
