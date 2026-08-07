package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.extraction.SourceTextExtractor;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBatchProcessingService;
import com.globalcodelabs.socialmediaplanner.application.service.impl.GeneratedContentFinalizer;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialUnavailableException;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSource;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContent;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationBatchJob implements GenerationBatchProcessingService {

    private static final String JOB_NAME = "batch-generation";
    private static final List<GenerationAttemptStatus> IN_FLIGHT_ATTEMPT_STATUSES = List.of(
            GenerationAttemptStatus.STARTED
    );

    private final GenerationBatchRepository generationBatchRepository;
    private final ContentRepository contentRepository;
    private final GenerationAttemptRepository generationAttemptRepository;
    private final GeneratedContentFinalizer generatedContentFinalizer;
    private final SourceTextExtractor sourceTextExtractor;
    private final AiProviderFactory aiProviderFactory;
    private final MediaContentLoader mediaContentLoader;
    private final MediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    @Async("generationTaskExecutor")
    @Override
    public void start(UUID batchId) {
        long startedAt = System.nanoTime();
        MdcUtil.putCorrelationId("job-" + JOB_NAME + "-" + UUID.randomUUID());
        MdcUtil.putJobName(JOB_NAME);
        MdcUtil.putBatchId(batchId.toString());

        try {
            log.info("Generation batch job started");
            process(batchId);
            log.info("Generation batch job completed durationMs={}", elapsedMilliseconds(startedAt));
        } catch (ConcurrentGenerationException exception) {
            log.info("Duplicate generation batch job stopped without another provider call");
        } catch (Exception exception) {
            markBatchFailed(batchId, exception);
            log.error("Generation batch job failed durationMs={} errorType={} error={}",
                    elapsedMilliseconds(startedAt), exception.getClass().getSimpleName(), errorMessage(exception));
        } finally {
            MdcUtil.clear();
        }
    }

    private void process(UUID batchId) {
        Set<Integer> completedIndexes = new HashSet<>(
                contentRepository.findGenerationIndexesByBatchId(batchId)
        );
        generatedContentFinalizer.reconcileCompletedCount(batchId, completedIndexes.size());

        GenerationBatch batch = loadBatch(batchId);
        if (batch.status() == GenerationBatchStatus.COMPLETED) {
            return;
        }
        String sourceText = extractSources(batch);
        if (sourceText.isBlank()) {
            throw new IllegalStateException("No source could be extracted for generation batch");
        }

        for (int index = 1; index <= batch.requestedCount(); index++) {
            if (completedIndexes.contains(index)) {
                continue;
            }
            Content content = generateContent(batch, sourceText, index);
            boolean stored = generatedContentFinalizer.storeGeneratedContent(batch.id(), index, content);
            if (!stored) {
                completedIndexes.add(index);
                continue;
            }
            batch = loadBatch(batchId);
            completedIndexes.add(index);
            log.info("Content generated completedCount={} requestedCount={}",
                    batch.completedCount(), batch.requestedCount());
        }
    }

    private String extractSources(GenerationBatch batch) {
        List<String> extractedSources = new ArrayList<>();
        List<ContentSource> orderedSources = batch.sources().stream()
                .sorted(Comparator.comparing(ContentSource::createdAt).thenComparing(ContentSource::id))
                .toList();
        for (ContentSource source : orderedSources) {
            if (source.status() == ContentSourceStatus.COMPLETED) {
                extractedSources.add(source.extractedText());
                continue;
            }
            try {
                source.startProcessing();
                generationBatchRepository.save(batch);
                String extractedText = sourceTextExtractor.extract(source.sourceType(), source.sourceValue());
                source.complete(extractedText);
                extractedSources.add(extractedText);
                generationBatchRepository.save(batch);
            } catch (Exception exception) {
                if (source.status() == ContentSourceStatus.PENDING
                        || source.status() == ContentSourceStatus.PROCESSING) {
                    source.fail(errorMessage(exception));
                    generationBatchRepository.save(batch);
                }
                log.warn("Source extraction failed sourceId={} errorType={} error={}",
                        source.id(), exception.getClass().getSimpleName(), errorMessage(exception));
            }
        }
        return String.join(System.lineSeparator(), extractedSources);
    }

    private Content generateContent(GenerationBatch batch, String sourceText, int index) {
        String prompt = buildTextPrompt(batch, sourceText, index);
        GenerationAttempt textAttempt = generate(
                batch,
                index,
                new AiGenerationRequest(batch.textProvider(), AiCapability.TEXT, prompt, batch.textModel())
        );
        GeneratedText generatedText;
        try {
            generatedText = parseGeneratedText(textAttempt.output());
        } catch (RuntimeException exception) {
            invalidateAttempt(textAttempt, exception);
            throw exception;
        }

        Content content = Content.createGenerated(
                batch.platform(), batch.contentType(), generatedText.text(), generatedText.hashtags(),
                batch.id(), index
        );
        content.recordTextGeneration(textAttempt.provider(), textAttempt.model());

        if (batch.includeImage()) {
            addMedia(batch, index, content, MediaType.IMAGE, batch.imageProvider(), batch.imageModel());
        }
        if (batch.includeVideo()) {
            addMedia(batch, index, content, MediaType.VIDEO, batch.videoProvider(), batch.videoModel());
        }
        return content;
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
        GenerationAttempt attempt = generate(
                batch,
                generationIndex,
                new AiGenerationRequest(
                        provider, capability, "Generate media for: " + content.text(), model
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

        MediaContent mediaContent;
        try {
            mediaContent = mediaContentLoader.load(mediaType, attempt.output());
        } catch (RuntimeException exception) {
            invalidateMediaAfterRetry(batch, attempt, exception);
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
            invalidateMediaAfterRetry(batch, attempt, exception);
            throw exception;
        }
        try {
            attempt.recordStoredMedia(storedMedia.storageKey(), storedMedia.contentType());
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException exception) {
            deleteStoredMedia(storedMedia.storageKey());
            throw exception;
        }
        content.addMedia(mediaType, storedMedia.storageKey(), null, attempt.provider(), attempt.model());
    }

    private GenerationAttempt generate(
            GenerationBatch batch,
            int generationIndex,
            AiGenerationRequest request
    ) {
        String promptHash = promptHash(request.prompt());
        GenerationAttempt reusable = generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        batch.id(), generationIndex, request.capability(), promptHash,
                        GenerationAttemptStatus.SUCCEEDED
                )
                .orElse(null);
        if (reusable != null) {
            return reuseCompletedAttempt(batch, generationIndex, request, reusable);
        }

        GenerationAttempt attempt = GenerationAttempt.start(
                batch.id(), generationIndex, batch.retryCount(), request.capability(),
                request.provider(), request.model(), promptHash
        );
        try {
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException exception) {
            GenerationAttempt completedAttempt = generationAttemptRepository
                    .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                            batch.id(), generationIndex, request.capability(), promptHash,
                            GenerationAttemptStatus.SUCCEEDED
                    )
                    .orElse(null);
            if (completedAttempt != null) {
                return reuseCompletedAttempt(batch, generationIndex, request, completedAttempt);
            }
            boolean activeAttemptExists = generationAttemptRepository
                    .existsByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusIn(
                            batch.id(), generationIndex, request.capability(), promptHash,
                            IN_FLIGHT_ATTEMPT_STATUSES
                    );
            if (activeAttemptExists) {
                throw new ConcurrentGenerationException();
            }
            throw exception;
        }

        AiGenerationResult result;
        try {
            result = aiProviderFactory.resolve(request.provider()).generate(request);
        } catch (RuntimeException exception) {
            failAttempt(attempt, exception);
            throw exception;
        }
        attempt.succeed(
                result.capability(),
                result.provider(),
                result.model(),
                result.providerResponseId(),
                result.providerRequestId(),
                result.output()
        );
        return generationAttemptRepository.saveAndFlush(attempt);
    }

    private GenerationAttempt reuseCompletedAttempt(
            GenerationBatch batch,
            int generationIndex,
            AiGenerationRequest request,
            GenerationAttempt attempt
    ) {
        if (attempt.retryNumber() >= batch.retryCount()) {
            throw new ConcurrentGenerationException();
        }
        log.info("AI generation result reused capability={} model={} generationIndex={}",
                request.capability(), request.model(), generationIndex);
        return attempt;
    }

    private String buildTextPrompt(GenerationBatch batch, String sourceText, int index) {
        return """
                Generate one distinct social media content item using the supplied sources as common reference.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Do not wrap the JSON in Markdown or add explanations.
                Platform: %s
                Content type: %s
                Content number: %d of %d
                Platform requirements:
                %s
                Sources:
                %s
                """.formatted(
                batch.platform(), batch.contentType(), index, batch.requestedCount(),
                platformRequirements(batch.platform(), batch.contentType()), sourceText
        );
    }

    private static String platformRequirements(
            Platform platform,
            ContentType contentType
    ) {
        return switch (platform) {
            case LINKEDIN -> "Use a professional, informative tone suitable for a LinkedIn post.";
            case INSTAGRAM -> contentType == ContentType.REEL
                    ? "Write a short, high-impact caption that complements video-first Reel content."
                    : "Write a concise, engaging caption suitable for a visual Instagram feed post.";
            case TWITTER -> "Keep the final text and hashtags together within 280 characters. "
                    + "Use a concise Tweet format.";
        };
    }

    private GeneratedText parseGeneratedText(String output) {
        try {
            GeneratedText generatedText = objectMapper.readValue(output, GeneratedText.class);
            if (generatedText.text() == null || generatedText.text().isBlank()) {
                throw new IllegalStateException("AI response text cannot be blank");
            }
            return new GeneratedText(
                    generatedText.text(),
                    generatedText.hashtags() == null ? List.of() : generatedText.hashtags()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI response is not valid content JSON", exception);
        }
    }

    private GenerationBatch loadBatch(UUID batchId) {
        return generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Generation batch not found: " + batchId));
    }

    private void markBatchFailed(UUID batchId, Exception failure) {
        String failureMessage = errorMessage(failure);
        generationBatchRepository.findOneById(batchId).ifPresent(batch -> {
            if (batch.status() == GenerationBatchStatus.IN_PROGRESS) {
                batch.markFailed(failureMessage);
                generationBatchRepository.save(batch);
            }
        });
    }

    private void failAttempt(GenerationAttempt attempt, RuntimeException failure) {
        try {
            attempt.fail(
                    errorMessage(failure),
                    isSubmissionUnknown(failure),
                    providerResponseId(failure),
                    providerRequestId(failure)
            );
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not persist failed AI generation attempt attemptId={}",
                    attempt.id(), persistenceFailure);
        }
    }

    private void invalidateAttempt(GenerationAttempt attempt, RuntimeException failure) {
        try {
            attempt.invalidate(errorMessage(failure));
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not invalidate AI generation attempt attemptId={}",
                    attempt.id(), persistenceFailure);
        }
    }

    private void invalidateMediaAfterRetry(
            GenerationBatch batch,
            GenerationAttempt attempt,
            RuntimeException failure
    ) {
        if (batch.retryCount() > 0) {
            invalidateAttempt(attempt, failure);
        }
    }

    private void deleteStoredMedia(String storageKey) {
        try {
            mediaStorage.delete(storageKey);
        } catch (RuntimeException exception) {
            log.warn("Could not delete uncommitted generated media storageKey={}", storageKey, exception);
        }
    }

    private static boolean isSubmissionUnknown(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException
                    || cause instanceof ApiCredentialUnavailableException
                    || cause instanceof IllegalArgumentException
                    || cause instanceof UnsupportedOperationException) {
                return false;
            }
            if (cause instanceof RestClientResponseException responseException) {
                return !responseException.getStatusCode().is4xxClientError();
            }
        }
        return true;
    }

    private static String providerRequestId(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException responseException
                    && responseException.providerRequestId() != null) {
                return responseException.providerRequestId();
            }
            if (cause instanceof RestClientResponseException responseException
                    && responseException.getResponseHeaders() != null) {
                for (String header : List.of("x-request-id", "request-id", "x-goog-request-id")) {
                    String value = responseException.getResponseHeaders().getFirst(header);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String providerResponseId(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException responseException
                    && responseException.providerResponseId() != null) {
                return responseException.providerResponseId();
            }
        }
        return null;
    }

    private static String promptHash(String prompt) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String errorMessage(Throwable exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        String sanitized = message
                .replaceAll("data:[^\\s,;]+;base64,[A-Za-z0-9+/=]+", "data:[REDACTED]")
                .replaceAll("(https?://[^\\s?]+)\\?[^\\s,]+", "$1?[REDACTED]")
                .replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]")
                .replaceAll(
                        "(?i)((?:api[-_ ]?key|token|authorization|x-api-key)\\s*[=:]\\s*)[^\\s,;]+",
                        "$1[REDACTED]"
                );
        return sanitized.length() <= 2000 ? sanitized : sanitized.substring(0, 2000);
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record GeneratedText(String text, List<String> hashtags) {
    }

    private static final class ConcurrentGenerationException extends RuntimeException {
    }
}
