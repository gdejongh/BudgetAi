CREATE TABLE ai_advice_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    advice_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ai_advice_cache_user UNIQUE (app_user_id)
);
