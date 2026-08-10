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
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
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
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationStrategy;
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
    private static final List<ContentAngle> CONTENT_ANGLES = List.of(
            new ContentAngle(
                    "BENEFIT_FOCUSED",
                    "Lead with the most concrete source-backed benefit and explain why it matters to the audience."
            ),
            new ContentAngle(
                    "QUESTION_LED",
                    "Open with a relevant question, then answer it using only facts supported by the sources."
            ),
            new ContentAngle(
                    "CTA_FOCUSED",
                    "Build the content toward one clear, source-supported call to action."
            ),
            new ContentAngle(
                    "PROBLEM_SOLUTION",
                    "Frame a problem described or implied by the sources, then present the source-backed solution."
            ),
            new ContentAngle(
                    "FACT_LED",
                    "Lead with a concrete fact or detail from the sources and turn it into a concise takeaway."
            )
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
        List<ExtractedSource> extractedSources = extractSources(batch);
        if (extractedSources.isEmpty()) {
            throw new IllegalStateException("No source could be extracted for generation batch");
        }
        if (batch.generationStrategy() == GenerationStrategy.SOURCE_BASED
                && extractedSources.size() < batch.requestedCount()) {
            log.warn(
                    "Source-based generation will reuse extracted sources round-robin extractedSourceCount={} requestedCount={}",
                    extractedSources.size(),
                    batch.requestedCount()
            );
        }

        for (int index = 1; index <= batch.requestedCount(); index++) {
            if (completedIndexes.contains(index)) {
                continue;
            }
            Content content = generateContent(batch, extractedSources, index);
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

    private List<ExtractedSource> extractSources(GenerationBatch batch) {
        List<ExtractedSource> extractedSources = new ArrayList<>();
        List<ContentSource> orderedSources = batch.sources().stream()
                .sorted(Comparator.comparing(ContentSource::createdAt).thenComparing(ContentSource::id))
                .toList();
        for (int sourceIndex = 0; sourceIndex < orderedSources.size(); sourceIndex++) {
            ContentSource source = orderedSources.get(sourceIndex);
            if (source.status() == ContentSourceStatus.COMPLETED) {
                extractedSources.add(toExtractedSource(source, sourceIndex));
                continue;
            }
            try {
                source.startProcessing();
                generationBatchRepository.save(batch);
                String extractedText = sourceTextExtractor.extract(source.sourceType(), source.sourceValue());
                source.complete(extractedText);
                extractedSources.add(toExtractedSource(source, sourceIndex));
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
        return List.copyOf(extractedSources);
    }

    private Content generateContent(GenerationBatch batch, List<ExtractedSource> sources, int index) {
        String prompt = buildTextPrompt(batch, sources, index);
        TextGenerationOutcome textGeneration = generateValidText(batch, index, prompt);
        Content content = textGeneration.content();
        content.recordTextGeneration(textGeneration.attempt().provider(), textGeneration.attempt().model());

        if (batch.includeImage()) {
            addMedia(batch, index, content, MediaType.IMAGE, batch.imageProvider(), batch.imageModel());
        }
        if (batch.includeVideo()) {
            addMedia(batch, index, content, MediaType.VIDEO, batch.videoProvider(), batch.videoModel());
        }
        return content;
    }

    private TextGenerationOutcome generateValidText(GenerationBatch batch, int index, String prompt) {
        GenerationAttempt initialAttempt = generateText(batch, index, prompt);
        try {
            return createTextGenerationOutcome(batch, index, initialAttempt);
        } catch (RuntimeException initialFailure) {
            invalidateAttempt(initialAttempt, initialFailure);

            String correctionPrompt = buildCorrectionPrompt(
                    prompt,
                    initialAttempt.output(),
                    initialFailure
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
                invalidateAttempt(correctionAttempt, correctionFailure);
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
        return generate(
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
                batch.platform(), batch.contentType(), generatedText.text(), generatedText.hashtags(),
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
                        batch.platform(), batch.contentType(), generatedText.text(), remainingHashtags,
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

    private String buildTextPrompt(GenerationBatch batch, List<ExtractedSource> sources, int index) {
        ContentAngle angle = CONTENT_ANGLES.get((index - 1) % CONTENT_ANGLES.size());
        SourcePromptContext sourceContext = buildSourcePromptContext(batch.generationStrategy(), sources, index);
        return """
                Generate one distinct social media content item using the supplied source context.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Do not wrap the JSON in Markdown or add explanations.
                Treat every conditional offer, discount, benefit, eligibility rule, prerequisite, limitation, and duration
                in the sources as one indivisible claim-condition unit. If you mention the claim, include all of its
                conditions accurately; otherwise omit the entire claim. Never present a conditional claim as unconditional.
                Platform: %s
                Content type: %s
                Content number: %d of %d
                Generation strategy: %s
                Strategy requirements:
                %s
                Creative angle: %s
                Angle requirements: %s
                Platform requirements:
                %s
                Source context:
                %s
                """.formatted(
                batch.platform(), batch.contentType(), index, batch.requestedCount(),
                batch.generationStrategy(), sourceContext.instructions(),
                angle.name(), angle.instructions(),
                platformRequirements(batch.platform(), batch.contentType()), sourceContext.content()
        );
    }

    private static SourcePromptContext buildSourcePromptContext(
            GenerationStrategy strategy,
            List<ExtractedSource> sources,
            int generationIndex
    ) {
        if (strategy == GenerationStrategy.COMBINED) {
            return new SourcePromptContext(
                    "Treat all sources as one combined information pool. Use the assigned creative angle to make this "
                            + "content materially different from the other requested items.",
                    sources.stream().map(GenerationBatchJob::formatSource).reduce(
                            (left, right) -> left + System.lineSeparator() + System.lineSeparator() + right
                    ).orElseThrow()
            );
        }

        int primarySourceIndex = (generationIndex - 1) % sources.size();
        ExtractedSource primarySource = sources.get(primarySourceIndex);
        String supportingSources = sources.stream()
                .filter(source -> source != primarySource)
                .map(GenerationBatchJob::formatSource)
                .reduce((left, right) -> left + System.lineSeparator() + System.lineSeparator() + right)
                .orElse("None.");
        return new SourcePromptContext(
                "The primary source must determine the central message. Supporting sources may add context, but must "
                        + "not displace or contradict the primary source.",
                "Primary source:\n%s\n\nSupporting sources:\n%s"
                        .formatted(formatSource(primarySource), supportingSources)
        );
    }

    private static ExtractedSource toExtractedSource(ContentSource source, int sourceIndex) {
        return new ExtractedSource(
                sourceIndex + 1,
                source.id(),
                source.sourceType().name(),
                source.extractedText()
        );
    }

    private static String formatSource(ExtractedSource source) {
        return "[Source %d | type=%s | id=%s]\n%s"
                .formatted(source.number(), source.type(), source.id(), source.text());
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
            case TWITTER -> "The published value is formatted as text, two newline characters, then "
                    + "space-separated hashtags prefixed with #. Its X weighted length must not exceed 280. "
                    + "Target at most 250 weighted characters, use at most 3 short hashtags, and use a concise Tweet format. "
                    + "Return hashtags in descending order of importance, with the most important hashtag first.";
        };
    }

    private String buildCorrectionPrompt(
            String originalPrompt,
            String invalidOutput,
            RuntimeException validationFailure
    ) {
        return """
                Correct the previous response so it satisfies every original requirement.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Preserve the main message, but shorten the text and hashtags as needed.
                Preserve every condition attached to an offer, discount, benefit, eligibility rule, prerequisite,
                limitation, or duration. Never shorten a claim by removing its conditions; omit the whole claim instead.
                For Twitter, target at most 250 X weighted characters after formatting and use at most 3 short hashtags.
                Return hashtags in descending order of importance, with the most important hashtag first.
                Do not wrap the JSON in Markdown or add explanations.
                Validation error:
                %s
                Previous invalid response:
                %s
                Original requirements:
                %s
                """.formatted(errorMessage(validationFailure), invalidOutput, originalPrompt);
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

    private record TextGenerationOutcome(GenerationAttempt attempt, Content content) {
    }

    private record ExtractedSource(int number, UUID id, String type, String text) {
    }

    private record SourcePromptContext(String instructions, String content) {
    }

    private record ContentAngle(String name, String instructions) {
    }

    private static final class ConcurrentGenerationException extends RuntimeException {
    }
}
