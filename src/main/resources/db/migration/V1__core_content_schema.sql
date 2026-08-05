CREATE TABLE generation_batch (
    id UUID PRIMARY KEY,
    platform VARCHAR NOT NULL,
    content_type VARCHAR NOT NULL,
    requested_count INT NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    status VARCHAR NOT NULL DEFAULT 'IN_PROGRESS',
    include_image BOOLEAN NOT NULL DEFAULT FALSE,
    include_video BOOLEAN NOT NULL DEFAULT FALSE,
    text_provider VARCHAR NOT NULL,
    text_model VARCHAR NOT NULL,
    image_provider VARCHAR,
    image_model VARCHAR,
    video_provider VARCHAR,
    video_model VARCHAR,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT generation_batch_platform_check CHECK (platform IN ('LINKEDIN', 'INSTAGRAM', 'TWITTER')),
    CONSTRAINT generation_batch_content_type_check CHECK (content_type IN ('POST', 'REEL', 'TWEET')),
    CONSTRAINT generation_batch_status_check CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT generation_batch_platform_content_type_check CHECK (
        (platform = 'INSTAGRAM' AND content_type IN ('POST', 'REEL'))
        OR (platform = 'LINKEDIN' AND content_type = 'POST')
        OR (platform = 'TWITTER' AND content_type = 'TWEET')
    ),
    CONSTRAINT generation_batch_requested_count_check CHECK (requested_count > 0),
    CONSTRAINT generation_batch_completed_count_check CHECK (
        completed_count >= 0
        AND completed_count <= requested_count
    ),
    CONSTRAINT generation_batch_image_provider_model_check CHECK (
        (include_image AND image_provider IS NOT NULL AND image_model IS NOT NULL)
        OR (NOT include_image AND image_provider IS NULL AND image_model IS NULL)
    ),
    CONSTRAINT generation_batch_video_provider_model_check CHECK (
        (include_video AND video_provider IS NOT NULL AND video_model IS NOT NULL)
        OR (NOT include_video AND video_provider IS NULL AND video_model IS NULL)
    )
);

CREATE TABLE content_source (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES generation_batch(id),
    source_type VARCHAR NOT NULL,
    source_value TEXT NOT NULL,
    extracted_text TEXT,
    status VARCHAR NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT content_source_type_check CHECK (source_type IN ('LINK', 'DOCUMENT')),
    CONSTRAINT content_source_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE content (
    id UUID PRIMARY KEY,
    batch_id UUID REFERENCES generation_batch(id),
    platform VARCHAR NOT NULL,
    content_type VARCHAR NOT NULL,
    status VARCHAR NOT NULL DEFAULT 'DRAFT',
    text TEXT NOT NULL,
    hashtags TEXT[] NOT NULL DEFAULT '{}',
    text_provider VARCHAR,
    text_model VARCHAR,
    scheduled_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT content_platform_check CHECK (platform IN ('LINKEDIN', 'INSTAGRAM', 'TWITTER')),
    CONSTRAINT content_type_check CHECK (content_type IN ('POST', 'REEL', 'TWEET')),
    CONSTRAINT content_status_check CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT content_platform_content_type_check CHECK (
        (platform = 'INSTAGRAM' AND content_type IN ('POST', 'REEL'))
        OR (platform = 'LINKEDIN' AND content_type = 'POST')
        OR (platform = 'TWITTER' AND content_type = 'TWEET')
    ),
    CONSTRAINT content_schedule_check CHECK (
        (status = 'DRAFT' AND scheduled_at IS NULL)
        OR (status IN ('SCHEDULED', 'PUBLISHED', 'FAILED') AND scheduled_at IS NOT NULL)
    ),
    CONSTRAINT content_published_check CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT content_text_provider_model_check CHECK (
        (text_provider IS NULL AND text_model IS NULL)
        OR (text_provider IS NOT NULL AND text_model IS NOT NULL)
    )
);

CREATE TABLE content_media (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    media_type VARCHAR NOT NULL,
    storage_key TEXT NOT NULL,
    public_url TEXT,
    model_provider VARCHAR,
    model_id VARCHAR,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT content_media_type_check CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT content_media_content_type_unique UNIQUE (content_id, media_type)
);

CREATE INDEX content_source_batch_id_idx ON content_source(batch_id);
CREATE INDEX content_status_idx ON content(status);
CREATE INDEX content_scheduled_at_idx ON content(scheduled_at) WHERE status = 'SCHEDULED';
CREATE INDEX content_batch_id_idx ON content(batch_id);
