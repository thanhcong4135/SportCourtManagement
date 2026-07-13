ALTER TABLE app_user ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE app_user ADD COLUMN provider_id VARCHAR(128) NULL;
ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(512) NULL;
ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_app_user_provider_provider_id ON app_user (provider, provider_id);
