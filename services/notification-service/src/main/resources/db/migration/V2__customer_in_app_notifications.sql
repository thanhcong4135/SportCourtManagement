ALTER TABLE notification_message
  ADD COLUMN title VARCHAR(180) NULL,
  ADD COLUMN deep_link VARCHAR(512) NULL,
  ADD COLUMN read_at TIMESTAMP(6) NULL;

UPDATE notification_message
SET title = COALESCE(NULLIF(template_code, ''), 'Notification')
WHERE title IS NULL;

CREATE INDEX idx_notification_customer_inbox
  ON notification_message(customer_id, channel, read_at, created_at);
