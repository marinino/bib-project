CREATE TABLE app_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nullable: bestehende Prompts aus Stufe 1-3 haben keinen Owner. Neue Prompts bekommen
-- ab jetzt immer einen (siehe PromptService), die Spalte selbst bleibt aber optional,
-- damit diese Migration nicht an vorhandenen Zeilen scheitert.
ALTER TABLE prompt ADD COLUMN owner_id UUID REFERENCES app_user (id) ON DELETE CASCADE;
ALTER TABLE prompt ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX idx_prompt_owner_id ON prompt (owner_id);
