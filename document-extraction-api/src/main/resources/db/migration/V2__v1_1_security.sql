-- ---------------------------------------------------------------------------
-- V1.1 security hardening: revocable sessions and login throttling.
-- ---------------------------------------------------------------------------

ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE login_attempts (
    attempt_key VARCHAR(128) PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_login_attempts_updated ON login_attempts (updated_at);
