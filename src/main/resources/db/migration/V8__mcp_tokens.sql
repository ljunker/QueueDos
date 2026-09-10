CREATE TABLE IF NOT EXISTS queuedos_mcp_tokens
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    owner_id        text NOT NULL REFERENCES queuedos_users (id) ON DELETE CASCADE,
    name            text NOT NULL,
    token_hash      text NOT NULL UNIQUE,
    token_hint      text NOT NULL,
    created_at      text NOT NULL,
    expires_at      text NOT NULL,
    last_used_at    text,
    revoked_at      text
);

CREATE INDEX IF NOT EXISTS idx_queuedos_mcp_tokens_owner
    ON queuedos_mcp_tokens (organization_id, owner_id, created_at);

CREATE INDEX IF NOT EXISTS idx_queuedos_mcp_tokens_active_hash
    ON queuedos_mcp_tokens (token_hash)
    WHERE revoked_at IS NULL;
