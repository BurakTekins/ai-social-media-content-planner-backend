ALTER TABLE generation_batch
    ADD COLUMN generation_strategy VARCHAR(20),
    ADD COLUMN strategy_selection_reason TEXT,
    ADD COLUMN strategy_warning TEXT;

UPDATE generation_batch
SET generation_strategy = 'COMBINED',
    strategy_selection_reason = 'Existing batch migrated to COMBINED strategy.';

ALTER TABLE generation_batch
    ALTER COLUMN generation_strategy SET NOT NULL,
    ALTER COLUMN strategy_selection_reason SET NOT NULL,
    ADD CONSTRAINT generation_batch_generation_strategy_check
        CHECK (generation_strategy IN ('SOURCE_BASED', 'COMBINED'));
