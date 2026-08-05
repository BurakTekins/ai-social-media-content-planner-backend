ALTER TABLE generation_batch
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT,
    ADD COLUMN last_retry_at TIMESTAMPTZ;

ALTER TABLE generation_batch
    ADD CONSTRAINT generation_batch_retry_count_check CHECK (retry_count >= 0);
