ALTER TABLE generation_batch
    ADD COLUMN video_duration_seconds INT;

UPDATE generation_batch
SET video_duration_seconds = CASE
    WHEN video_provider = 'gemini' AND video_model LIKE 'veo-3.1%' THEN 4
    ELSE 5
END
WHERE include_video = TRUE;

ALTER TABLE generation_batch
    ADD CONSTRAINT generation_batch_video_duration_check CHECK (
        (include_video = TRUE AND video_duration_seconds > 0)
        OR (include_video = FALSE AND video_duration_seconds IS NULL)
    );
