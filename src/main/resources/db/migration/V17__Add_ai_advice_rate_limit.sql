ALTER TABLE ai_advice_cache ADD COLUMN generation_count INT NOT NULL DEFAULT 0;
ALTER TABLE ai_advice_cache ADD COLUMN generation_reset_at TIMESTAMPTZ NOT NULL DEFAULT now();
