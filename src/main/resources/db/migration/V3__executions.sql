CREATE TABLE execution (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_version_id  UUID NOT NULL REFERENCES prompt_version (id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL,
    model               VARCHAR(100),
    input_params        JSONB,
    output              TEXT,
    latency_ms          INT,
    tokens_in           INT,
    tokens_out          INT,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ
);

CREATE INDEX idx_execution_prompt_version_id ON execution (prompt_version_id);
CREATE INDEX idx_execution_status ON execution (status);
