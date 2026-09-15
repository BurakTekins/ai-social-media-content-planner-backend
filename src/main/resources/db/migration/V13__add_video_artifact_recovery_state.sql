ALTER TABLE generation_attempt
    ALTER COLUMN status TYPE VARCHAR(40),
    ADD COLUMN provider_submitted_at TIMESTAMPTZ,
    ADD COLUMN artifact_expires_at TIMESTAMPTZ,
    ADD COLUMN next_download_retry_at TIMESTAMPTZ,
    ADD COLUMN download_retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE generation_attempt
    DROP CONSTRAINT generation_attempt_status_check,
    ADD CONSTRAINT generation_attempt_status_check CHECK (
        status IN (
            'STARTED',
            'SUBMITTED',
            'PROCESSING',
            'PROVIDER_SUCCEEDED',
            'DOWNLOAD_FAILED',
            'DOWNLOADED',
            'AWAITING_REGENERATION_CONSENT',
            'REGENERATION_APPROVED',
            'SUCCEEDED',
            'FAILED',
            'UNKNOWN',
            'INVALID'
        )
    ),
    ADD CONSTRAINT generation_attempt_download_retry_count_check CHECK (
        download_retry_count >= 0
    );

DROP INDEX generation_attempt_single_flight_idx;

CREATE UNIQUE INDEX generation_attempt_single_flight_idx
    ON generation_attempt(batch_id, generation_index, capability, prompt_hash)
    WHERE status IN (
        'STARTED',
        'SUBMITTED',
        'PROCESSING',
        'PROVIDER_SUCCEEDED',
        'DOWNLOAD_FAILED',
        'DOWNLOADED',
        'AWAITING_REGENERATION_CONSENT',
        'SUCCEEDED'
    );

CREATE INDEX generation_attempt_download_retry_idx
    ON generation_attempt(next_download_retry_at, batch_id)
    WHERE status = 'DOWNLOAD_FAILED';
