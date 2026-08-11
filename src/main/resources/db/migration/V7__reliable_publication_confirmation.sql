ALTER TABLE content
    ADD COLUMN publish_operation_id UUID,
    ADD COLUMN external_post_id VARCHAR,
    ADD COLUMN publishing_started_at TIMESTAMPTZ,
    ADD COLUMN publication_checked_at TIMESTAMPTZ;

ALTER TABLE content DROP CONSTRAINT content_status_check;
ALTER TABLE content ADD CONSTRAINT content_status_check
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHING', 'REVIEW_REQUIRED', 'PUBLISHED', 'FAILED'));

ALTER TABLE content DROP CONSTRAINT content_schedule_check;
ALTER TABLE content ADD CONSTRAINT content_schedule_check CHECK (
    (status = 'DRAFT' AND scheduled_at IS NULL)
    OR (status IN ('SCHEDULED', 'PUBLISHING', 'REVIEW_REQUIRED', 'PUBLISHED', 'FAILED') AND scheduled_at IS NOT NULL)
);

ALTER TABLE content ADD CONSTRAINT content_publishing_state_check CHECK (
    (status = 'PUBLISHING' AND publish_operation_id IS NOT NULL AND publishing_started_at IS NOT NULL)
    OR status <> 'PUBLISHING'
);

CREATE UNIQUE INDEX content_publish_operation_id_uidx
    ON content(publish_operation_id)
    WHERE publish_operation_id IS NOT NULL;

CREATE UNIQUE INDEX content_platform_external_post_id_uidx
    ON content(platform, external_post_id)
    WHERE external_post_id IS NOT NULL;

CREATE INDEX content_pending_publication_confirmation_idx
    ON content(publication_checked_at, publishing_started_at)
    WHERE status = 'PUBLISHING' AND external_post_id IS NOT NULL;

CREATE TABLE application_setting (
    setting_key VARCHAR PRIMARY KEY,
    setting_value VARCHAR NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
