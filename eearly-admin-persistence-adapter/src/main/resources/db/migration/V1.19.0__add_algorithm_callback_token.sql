-- Tracks the single-use callback token issued to a webhook-style algorithm provider, so that a POST to
-- /api/v1/analyses/{id}/result can be authenticated and cannot be replayed. Only the token's identifier and its
-- lifecycle are stored here, never the signed token itself. The columns stay empty unless callback tokens are
-- enabled (algorithm.callback.token-secret).
ALTER TABLE t_algorithm_execution
    ADD COLUMN callback_token_id          UUID,
    ADD COLUMN callback_token_expires_at  TIMESTAMPTZ,
    ADD COLUMN callback_token_consumed_at TIMESTAMPTZ;
