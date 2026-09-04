CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE prompt (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    current_version_no  INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prompt_title ON prompt (title);

CREATE TABLE prompt_version (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id   UUID NOT NULL REFERENCES prompt (id) ON DELETE CASCADE,
    version_no  INT NOT NULL,
    content     TEXT NOT NULL,
    parameters  JSONB,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prompt_version_no UNIQUE (prompt_id, version_no)
);

CREATE INDEX idx_prompt_version_prompt_id ON prompt_version (prompt_id);

CREATE TABLE tag (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE prompt_tag (
    prompt_id  UUID NOT NULL REFERENCES prompt (id) ON DELETE CASCADE,
    tag_id     UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (prompt_id, tag_id)
);
