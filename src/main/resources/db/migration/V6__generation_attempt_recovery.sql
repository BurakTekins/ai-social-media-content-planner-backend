ALTER TABLE content
    ADD COLUMN generation_index INT;

WITH ranked_content AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY batch_id ORDER BY created_at, id) AS generation_index
    FROM content
    WHERE batch_id IS NOT NULL
      AND text_provider IS NOT NULL
)
UPDATE content
SET generation_index = ranked_content.generation_index
FROM ranked_content
WHERE content.id = ranked_content.id;

ALTER TABLE content
    ADD CONSTRAINT content_generation_index_check CHECK (generation_index IS NULL OR generation_index > 0),
    ADD CONSTRAINT content_generation_batch_check CHECK (generation_index IS NULL OR batch_id IS NOT NULL);

CREATE UNIQUE INDEX content_batch_generation_index_unique_idx
    ON content(batch_id, generation_index)
    WHERE batch_id IS NOT NULL AND generation_index IS NOT NULL;

CREATE TABLE generation_attempt (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES generation_batch(id) ON DELETE CASCADE,
    generation_index INT NOT NULL,
    retry_number INT NOT NULL,
    capability VARCHAR NOT NULL,
    provider VARCHAR NOT NULL,
    model VARCHAR NOT NULL,
    prompt_hash VARCHAR(64) NOT NULL,
    status VARCHAR NOT NULL,
    provider_response_id TEXT,
    provider_request_id TEXT,
    output TEXT,
    storage_key TEXT,
    media_content_type VARCHAR,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT generation_attempt_index_check CHECK (generation_index > 0),
    CONSTRAINT generation_attempt_retry_number_check CHECK (retry_number >= 0),
    CONSTRAINT generation_attempt_capability_check CHECK (capability IN ('TEXT', 'IMAGE', 'VIDEO')),
    CONSTRAINT generation_attempt_status_check CHECK (
        status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'INVALID')
    ),
    CONSTRAINT generation_attempt_prompt_hash_check CHECK (prompt_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT generation_attempt_success_output_check CHECK (
        status <> 'SUCCEEDED' OR output IS NOT NULL OR storage_key IS NOT NULL
    )
);

CREATE INDEX generation_attempt_reusable_idx
    ON generation_attempt(batch_id, generation_index, capability, prompt_hash, status, created_at DESC);

CREATE UNIQUE INDEX generation_attempt_single_flight_idx
    ON generation_attempt(batch_id, generation_index, capability, prompt_hash)
    WHERE status IN ('STARTED', 'SUCCEEDED');

CREATE INDEX generation_attempt_provider_request_id_idx
    ON generation_attempt(provider, provider_request_id)
    WHERE provider_request_id IS NOT NULL;

CREATE INDEX generation_attempt_provider_response_id_idx
    ON generation_attempt(provider, provider_response_id)
    WHERE provider_response_id IS NOT NULL;
