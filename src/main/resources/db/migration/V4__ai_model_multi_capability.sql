ALTER TABLE ai_model_cache
    DROP CONSTRAINT ai_model_cache_provider_model_unique;

ALTER TABLE ai_model_cache
    ADD CONSTRAINT ai_model_cache_provider_model_capability_unique
        UNIQUE (provider_name, model_id, capability);
