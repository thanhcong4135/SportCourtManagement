CREATE TABLE product (
  id BINARY(16) PRIMARY KEY,
  venue_id BINARY(16) NOT NULL,
  name VARCHAR(255) NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_product_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
) ENGINE=InnoDB;

CREATE INDEX idx_product_venue_active ON product(venue_id, is_active);

CREATE TABLE sales_order (
  id BINARY(16) PRIMARY KEY,
  booking_id BINARY(16) NULL,
  venue_id BINARY(16) NOT NULL,
  customer_id BINARY(16) NULL,
  status VARCHAR(32) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_sales_order_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
  CONSTRAINT fk_sales_order_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
) ENGINE=InnoDB;

CREATE INDEX idx_sales_order_booking ON sales_order(booking_id);
CREATE INDEX idx_sales_order_venue_created ON sales_order(venue_id, created_at);
CREATE INDEX idx_sales_order_customer_created ON sales_order(customer_id, created_at);

CREATE TABLE sales_order_item (
  id BINARY(16) PRIMARY KEY,
  sales_order_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_sales_order_item_order FOREIGN KEY (sales_order_id) REFERENCES sales_order(id),
  CONSTRAINT fk_sales_order_item_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT chk_sales_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_sales_order_item_order ON sales_order_item(sales_order_id);
