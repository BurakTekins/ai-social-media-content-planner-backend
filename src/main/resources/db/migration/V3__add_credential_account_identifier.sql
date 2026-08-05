ALTER TABLE api_credential
    ADD COLUMN account_identifier TEXT;

ALTER TABLE api_credential
    ADD CONSTRAINT api_credential_account_identifier_check CHECK (
        (provider_name IN ('linkedin','instagram') AND account_identifier IS NOT NULL)
        OR (provider_name NOT IN ('linkedin','instagram'))
    );
