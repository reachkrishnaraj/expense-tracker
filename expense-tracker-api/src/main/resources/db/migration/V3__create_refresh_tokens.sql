-- V3: Create refresh_tokens table for JWT refresh token rotation
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id),
    token_hash     VARCHAR(255) NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    is_revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    replaced_by_id UUID         REFERENCES refresh_tokens(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
