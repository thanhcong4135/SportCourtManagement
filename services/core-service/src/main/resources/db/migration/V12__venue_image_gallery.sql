ALTER TABLE venue
  CHANGE COLUMN image_url cover_image_url VARCHAR(1024) NULL;

CREATE TABLE venue_image (
  id BINARY(16) PRIMARY KEY,
  venue_id BINARY(16) NOT NULL,
  image_url VARCHAR(1024) NOT NULL,
  alt_text VARCHAR(255) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  is_cover BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_venue_image_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
) ENGINE=InnoDB;

CREATE INDEX idx_venue_image_venue_id_sort_order ON venue_image(venue_id, sort_order);
