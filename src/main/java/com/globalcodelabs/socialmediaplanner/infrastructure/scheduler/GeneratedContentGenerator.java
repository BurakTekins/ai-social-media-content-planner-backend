package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMediaContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
class GeneratedContentGenerator {

    private final GenerationAttemptRepository generationAttemptRepository;
    private final MediaContentLoader mediaContentLoader;
    private final LocalMediaStorage mediaStorage;
    private final GenerationAttemptLifecycle attemptLifecycle;
    private final GenerationAttemptExecutor attemptExecutor;
    private final ObjectMapper objectMapper;

    Content generate(GenerationBatch batch, List<ExtractedSource> sources, int index) {
        String prompt = GenerationPromptFactory.textPrompt(batch, sources, index);
        TextGenerationOutcome textGeneration = generateValidText(batch, index, prompt);
        Content content = textGeneration.content();
        content.recordTextGeneration(textGeneration.attempt().provider(), textGeneration.attempt().model());

        if (batch.includeImage()) {
            addMedia(batch, index, content, MediaType.IMAGE, batch.imageProvider(), batch.imageModel());
        }
        if (batch.includeVideo()) {
            addMedia(batch, index, content, MediaType.VIDEO, batch.videoProvider(), batch.videoModel());
        }
        validateRequestedMedia(batch, content);
        return content;
    }

    private TextGenerationOutcome generateValidText(GenerationBatch batch, int index, String prompt) {
        GenerationAttempt initialAttempt = generateText(batch, index, prompt);
        try {
            return createTextGenerationOutcome(batch, index, initialAttempt);
        } catch (RuntimeException initialFailure) {
            attemptLifecycle.invalidate(initialAttempt, initialFailure);

            String correctionPrompt = GenerationPromptFactory.correctionPrompt(
                    prompt,
                    initialAttempt.output(),
                    GenerationAttemptSupport.errorMessage(initialFailure)
            );
            GenerationAttempt correctionAttempt = generateText(batch, index, correctionPrompt);
            GeneratedText correctedText = null;
            try {
                correctedText = parseGeneratedText(correctionAttempt.output());
                return new TextGenerationOutcome(
                        correctionAttempt,
                        createGeneratedContent(batch, index, correctedText)
                );
            } catch (RuntimeException correctionFailure) {
                attemptLifecycle.invalidate(correctionAttempt, correctionFailure);
                if (correctionFailure instanceof DomainException && correctedText != null) {
                    return applyTwitterHashtagFallback(
                            batch,
                            index,
                            correctionAttempt,
                            correctedText,
                            correctionFailure
                    );
                }
                throw correctionFailure;
            }
        }
    }

    private GenerationAttempt generateText(GenerationBatch batch, int index, String prompt) {
        return attemptExecutor.execute(
                batch,
                index,
                new AiGenerationRequest(batch.textProvider(), AiCapability.TEXT, prompt, batch.textModel())
        );
    }

    private TextGenerationOutcome createTextGenerationOutcome(
            GenerationBatch batch,
            int index,
            GenerationAttempt attempt
    ) {
        GeneratedText generatedText = parseGeneratedText(attempt.output());
        return new TextGenerationOutcome(attempt, createGeneratedContent(batch, index, generatedText));
    }

    private Content createGeneratedContent(GenerationBatch batch, int index, GeneratedText generatedText) {
        return Content.createGenerated(
                generatedTitle(batch, index), batch.platform(), batch.contentType(),
                generatedText.text(), generatedText.hashtags(),
                batch.id(), index
        );
    }

    private TextGenerationOutcome applyTwitterHashtagFallback(
            GenerationBatch batch,
            int index,
            GenerationAttempt attempt,
            GeneratedText generatedText,
            RuntimeException originalFailure
    ) {
        if (batch.platform() != Platform.TWITTER || generatedText.hashtags().isEmpty()) {
            throw originalFailure;
        }

        List<String> remainingHashtags = new ArrayList<>(generatedText.hashtags());
        int originalHashtagCount = remainingHashtags.size();
        while (!remainingHashtags.isEmpty()) {
            remainingHashtags.removeLast();
            try {
                Content content = Content.createGenerated(
                        generatedTitle(batch, index), batch.platform(), batch.contentType(),
                        generatedText.text(), remainingHashtags,
                        batch.id(), index
                );
                log.warn(
                        "Twitter generation required deterministic hashtag fallback removedHashtagCount={} remainingHashtagCount={}",
                        originalHashtagCount - remainingHashtags.size(),
                        remainingHashtags.size()
                );
                return new TextGenerationOutcome(attempt, content);
            } catch (DomainException ignored) {
            }
        }
        throw originalFailure;
    }

    private static String generatedTitle(GenerationBatch batch, int index) {
        return batch.title() + " " + index;
    }

    private void addMedia(
            GenerationBatch batch,
            int generationIndex,
            Content content,
            MediaType mediaType,
            String provider,
            String model
    ) {
        AiCapability capability = mediaType == MediaType.IMAGE ? AiCapability.IMAGE : AiCapability.VIDEO;
        GenerationAttempt attempt = attemptExecutor.execute(
                batch,
                generationIndex,
                new AiGenerationRequest(
                        provider,
                        capability,
                        GenerationPromptFactory.mediaPrompt(content, mediaType),
                        model,
                        capability == AiCapability.VIDEO ? batch.videoDurationSeconds() : null
                )
        );

        if (attempt.storageKey() != null) {
            content.addMedia(mediaType, attempt.storageKey(), null, attempt.provider(), attempt.model());
            return;
        }
        if (attempt.output().startsWith("mock://")) {
            String storageKey = "generated/" + provider + "/"
                    + mediaType.name().toLowerCase(Locale.ROOT) + "/" + content.id();
            content.addMedia(mediaType, storageKey, attempt.output(), attempt.provider(), attempt.model());
            return;
        }

        StoredMediaContent mediaContent;
        try {
            mediaContent = mediaContentLoader.load(mediaType, attempt.output());
        } catch (RuntimeException exception) {
            attemptLifecycle.invalidateMediaAfterRetry(batch, attempt, exception);
            throw exception;
        }

        StoredMedia storedMedia;
        try {
            storedMedia = mediaStorage.store(
                    mediaType,
                    mediaContent.contentType(),
                    mediaContent.bytes()
            );
        } catch (IllegalArgumentException exception) {
            attemptLifecycle.invalidateMediaAfterRetry(batch, attempt, exception);
            throw exception;
        }
        try {
            attempt.recordStoredMedia(storedMedia.storageKey(), storedMedia.contentType());
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException exception) {
            attemptLifecycle.deleteStoredMedia(storedMedia.storageKey());
            throw exception;
        }
        content.addMedia(mediaType, storedMedia.storageKey(), null, attempt.provider(), attempt.model());
    }

    private GeneratedText parseGeneratedText(String output) {
        try {
            GeneratedText generatedText = objectMapper.readValue(output, GeneratedText.class);
            if (generatedText.text() == null || generatedText.text().isBlank()) {
                throw new IllegalStateException("AI response text cannot be blank");
            }
            if (generatedText.hashtags() == null || generatedText.hashtags().isEmpty()) {
                throw new IllegalStateException("AI response must include at least one hashtag");
            }
            return new GeneratedText(generatedText.text(), generatedText.hashtags());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI response is not valid content JSON", exception);
        }
    }

    private static void validateRequestedMedia(GenerationBatch batch, Content content) {
        if (batch.includeImage() && content.findMedia(MediaType.IMAGE).isEmpty()) {
            throw new IllegalStateException("Generated content is missing the requested image");
        }
        if (batch.includeVideo() && content.findMedia(MediaType.VIDEO).isEmpty()) {
            throw new IllegalStateException("Generated content is missing the requested video");
        }
    }

    private record GeneratedText(String text, List<String> hashtags) {
    }

    private record TextGenerationOutcome(GenerationAttempt attempt, Content content) {
    }
}
