CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX content_title_trgm_idx
    ON content USING GIN (LOWER(title) gin_trgm_ops);

CREATE INDEX content_text_trgm_idx
    ON content USING GIN (LOWER(text) gin_trgm_ops);
