CREATE TABLE publish_attempt (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    success BOOLEAN NOT NULL,
    error_message TEXT,
    external_post_id VARCHAR
);

CREATE TABLE api_credential (
    id UUID PRIMARY KEY,
    credential_type VARCHAR NOT NULL,
    provider_name VARCHAR NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT api_credential_type_check CHECK (credential_type IN ('AI_PROVIDER', 'SOCIAL_PLATFORM')),
    CONSTRAINT api_credential_type_provider_unique UNIQUE (credential_type, provider_name)
);

CREATE TABLE ai_model_cache (
    id UUID PRIMARY KEY,
    provider_name VARCHAR NOT NULL,
    model_id VARCHAR NOT NULL,
    display_name VARCHAR,
    capability VARCHAR NOT NULL,
    raw_metadata JSONB,
    last_synced_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ai_model_cache_capability_check CHECK (capability IN ('TEXT', 'IMAGE', 'VIDEO')),
    CONSTRAINT ai_model_cache_provider_model_unique UNIQUE (provider_name, model_id)
);

CREATE INDEX publish_attempt_content_id_idx ON publish_attempt(content_id);
