CREATE TABLE app_user (
  id BINARY(16) NOT NULL,
  email VARCHAR(128) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_email (email)
);

CREATE TABLE app_role (
  id BINARY(16) NOT NULL,
  name VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_role_name (name)
);

CREATE TABLE app_user_role (
  user_id BINARY(16) NOT NULL,
  role_id BINARY(16) NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);

CREATE TABLE refresh_token (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_token_user_id (user_id),
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_CUSTOMER');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_OWNER');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_STAFF');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_ADMIN');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_SUPPORT');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_SERVICE_PAYMENT');
INSERT INTO app_role(id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_SERVICE_NOTIFICATION');
