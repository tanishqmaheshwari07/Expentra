-- =============================================================================
-- Migration V5: Add OAuth Exchange Codes Table for Single-Use Exchange Codes
-- =============================================================================

CREATE TABLE IF NOT EXISTS oauth_exchange_codes (
    code_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    binder_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_oauth_exchange_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_oauth_exchange_codes_expires_at ON oauth_exchange_codes(expires_at);
