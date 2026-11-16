-- One-time codes for "forgot password". Only a BCrypt hash of the 6-digit code is stored.
CREATE TABLE password_reset_codes (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  varchar(100) NOT NULL,
    attempts   integer NOT NULL DEFAULT 0,
    expires_at timestamp(6) with time zone NOT NULL,
    used_at    timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_codes_user ON password_reset_codes (user_id, created_at DESC);
CREATE INDEX idx_password_reset_codes_expires ON password_reset_codes (expires_at);
