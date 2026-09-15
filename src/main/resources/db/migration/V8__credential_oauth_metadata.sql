ALTER TABLE api_credential
    ADD COLUMN owner_id UUID,
    ADD COLUMN account_display_name VARCHAR(255),
    ADD COLUMN granted_scopes TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN refresh_token_expires_at TIMESTAMPTZ,
    ADD COLUMN validation_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN last_validated_at TIMESTAMPTZ,
    ADD COLUMN validation_error TEXT;

ALTER TABLE api_credential
    DROP CONSTRAINT api_credential_type_provider_unique;

ALTER TABLE api_credential
    ADD CONSTRAINT api_credential_owner_type_provider_unique
        UNIQUE NULLS NOT DISTINCT (owner_id, credential_type, provider_name),
    ADD CONSTRAINT api_credential_validation_status_check
        CHECK (validation_status IN ('UNVERIFIED', 'VALID', 'INVALID')),
    ADD CONSTRAINT api_credential_provider_check CHECK (
        (credential_type = 'AI_PROVIDER'
            AND provider_name IN ('openai', 'anthropic', 'gemini', 'deepseek', 'qwen'))
        OR
        (credential_type = 'SOCIAL_PLATFORM'
            AND provider_name IN ('twitter', 'linkedin', 'instagram'))
    );
