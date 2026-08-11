package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "generation_attempt")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class GenerationAttempt {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "generation_index", nullable = false)
    private int generationIndex;

    @Column(name = "retry_number", nullable = false)
    private int retryNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiCapability capability;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GenerationAttemptStatus status;

    @Column(name = "provider_response_id", columnDefinition = "TEXT")
    private String providerResponseId;

    @Column(name = "provider_request_id", columnDefinition = "TEXT")
    private String providerRequestId;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "storage_key", columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "media_content_type")
    private String mediaContentType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "provider_submitted_at")
    private OffsetDateTime providerSubmittedAt;

    @Column(name = "artifact_expires_at")
    private OffsetDateTime artifactExpiresAt;

    @Column(name = "next_download_retry_at")
    private OffsetDateTime nextDownloadRetryAt;

    @Column(name = "download_retry_count", nullable = false)
    private int downloadRetryCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private GenerationAttempt(
            UUID batchId,
            int generationIndex,
            int retryNumber,
            AiCapability capability,
            String provider,
            String model,
            String promptHash
    ) {
        if (generationIndex <= 0) {
            throw new DomainException("Generation index must be greater than zero");
        }
        if (retryNumber < 0) {
            throw new DomainException("Retry number cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.batchId = Objects.requireNonNull(batchId, "Generation batch id cannot be null");
        this.generationIndex = generationIndex;
        this.retryNumber = retryNumber;
        this.capability = Objects.requireNonNull(capability, "AI capability cannot be null");
        this.provider = DomainValidation.requireText(provider, "AI provider cannot be blank")
                .toLowerCase(Locale.ROOT);
        this.model = DomainValidation.requireText(model, "AI model cannot be blank");
        this.promptHash = requireHash(promptHash);
        this.status = GenerationAttemptStatus.STARTED;
        this.downloadRetryCount = 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    public static GenerationAttempt start(
            UUID batchId,
            int generationIndex,
            AiCapability capability,
            String provider,
            String model,
            String promptHash
    ) {
        return start(batchId, generationIndex, 0, capability, provider, model, promptHash);
    }

    public static GenerationAttempt start(
            UUID batchId,
            int generationIndex,
            int retryNumber,
            AiCapability capability,
            String provider,
            String model,
            String promptHash
    ) {
        return new GenerationAttempt(
                batchId, generationIndex, retryNumber, capability, provider, model, promptHash
        );
    }

    public void succeed(
            AiCapability resultCapability,
            String resultProvider,
            String resultModel,
            String providerResponseId,
            String providerRequestId,
            String output
    ) {
        requireStatus(GenerationAttemptStatus.STARTED);
        if (resultCapability != capability
                || !provider.equals(resultProvider)
                || !model.equals(resultModel)) {
            throw new DomainException("AI generation result does not match attempt");
        }
        this.output = DomainValidation.requireText(output, "AI generation output cannot be blank");
        String normalizedProviderResponseId = optionalValue(providerResponseId);
        String normalizedProviderRequestId = optionalValue(providerRequestId);
        if (normalizedProviderResponseId != null) {
            this.providerResponseId = normalizedProviderResponseId;
        }
        if (normalizedProviderRequestId != null) {
            this.providerRequestId = normalizedProviderRequestId;
        }
        this.status = GenerationAttemptStatus.SUCCEEDED;
        this.errorMessage = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSubmitted(
            String providerTaskId,
            String providerRequestId,
            OffsetDateTime submittedAt
    ) {
        requireStatus(GenerationAttemptStatus.STARTED);
        this.providerResponseId = DomainValidation.requireText(
                providerTaskId,
                "Provider video task id cannot be blank"
        );
        this.providerRequestId = optionalValue(providerRequestId);
        this.providerSubmittedAt = Objects.requireNonNull(
                submittedAt,
                "Provider submission time cannot be null"
        );
        this.status = GenerationAttemptStatus.SUBMITTED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markProcessing() {
        requireAnyStatus(
                GenerationAttemptStatus.SUBMITTED,
                GenerationAttemptStatus.PROVIDER_SUCCEEDED,
                GenerationAttemptStatus.DOWNLOAD_FAILED
        );
        this.status = GenerationAttemptStatus.PROCESSING;
        this.nextDownloadRetryAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markProviderSucceeded(
            String providerTaskId,
            String providerRequestId,
            OffsetDateTime expiresAt
    ) {
        requireAnyStatus(
                GenerationAttemptStatus.SUBMITTED,
                GenerationAttemptStatus.PROCESSING
        );
        this.providerResponseId = DomainValidation.requireText(
                providerTaskId,
                "Provider video task id cannot be blank"
        );
        this.providerRequestId = optionalValue(providerRequestId);
        this.artifactExpiresAt = Objects.requireNonNull(
                expiresAt,
                "Video artifact expiry cannot be null"
        );
        this.status = GenerationAttemptStatus.PROVIDER_SUCCEEDED;
        this.errorMessage = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDownloadFailed(String errorMessage, OffsetDateTime nextRetryAt) {
        requireAnyStatus(
                GenerationAttemptStatus.PROVIDER_SUCCEEDED,
                GenerationAttemptStatus.PROCESSING
        );
        this.status = GenerationAttemptStatus.DOWNLOAD_FAILED;
        this.errorMessage = DomainValidation.requireText(
                errorMessage,
                "Video download error cannot be blank"
        );
        this.nextDownloadRetryAt = Objects.requireNonNull(
                nextRetryAt,
                "Next video download retry time cannot be null"
        );
        this.downloadRetryCount++;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDownloaded(String storageKey, String mediaContentType) {
        requireAnyStatus(
                GenerationAttemptStatus.PROVIDER_SUCCEEDED,
                GenerationAttemptStatus.PROCESSING
        );
        this.storageKey = DomainValidation.requireText(storageKey, "Media storage key cannot be blank");
        this.mediaContentType = DomainValidation.requireText(
                mediaContentType,
                "Media content type cannot be blank"
        ).toLowerCase(Locale.ROOT);
        this.status = GenerationAttemptStatus.DOWNLOADED;
        this.errorMessage = null;
        this.nextDownloadRetryAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void completeDownloadedVideo(String output) {
        requireStatus(GenerationAttemptStatus.DOWNLOADED);
        this.output = DomainValidation.requireText(output, "AI generation output cannot be blank");
        this.status = GenerationAttemptStatus.SUCCEEDED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void awaitRegenerationConsent(String errorMessage) {
        requireAnyStatus(
                GenerationAttemptStatus.PROVIDER_SUCCEEDED,
                GenerationAttemptStatus.PROCESSING,
                GenerationAttemptStatus.DOWNLOAD_FAILED
        );
        this.status = GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT;
        this.errorMessage = DomainValidation.requireText(
                errorMessage,
                "Regeneration consent reason cannot be blank"
        );
        this.nextDownloadRetryAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void approveRegeneration() {
        requireStatus(GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT);
        this.status = GenerationAttemptStatus.REGENERATION_APPROVED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(String errorMessage, boolean submissionUnknown) {
        fail(errorMessage, submissionUnknown, null, null);
    }

    public void fail(String errorMessage, boolean submissionUnknown, String providerRequestId) {
        fail(errorMessage, submissionUnknown, null, providerRequestId);
    }

    public void fail(
            String errorMessage,
            boolean submissionUnknown,
            String providerResponseId,
            String providerRequestId
    ) {
        requireAnyStatus(
                GenerationAttemptStatus.STARTED,
                GenerationAttemptStatus.SUBMITTED,
                GenerationAttemptStatus.PROCESSING,
                GenerationAttemptStatus.PROVIDER_SUCCEEDED
        );
        this.status = submissionUnknown ? GenerationAttemptStatus.UNKNOWN : GenerationAttemptStatus.FAILED;
        this.errorMessage = DomainValidation.requireText(
                errorMessage,
                "Generation attempt error cannot be blank"
        );
        String normalizedProviderResponseId = optionalValue(providerResponseId);
        String normalizedProviderRequestId = optionalValue(providerRequestId);
        if (normalizedProviderResponseId != null) {
            this.providerResponseId = normalizedProviderResponseId;
        }
        if (normalizedProviderRequestId != null) {
            this.providerRequestId = normalizedProviderRequestId;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void invalidate(String errorMessage) {
        requireStatus(GenerationAttemptStatus.SUCCEEDED);
        this.status = GenerationAttemptStatus.INVALID;
        this.errorMessage = DomainValidation.requireText(
                errorMessage,
                "Generation attempt error cannot be blank"
        );
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordStoredMedia(String storageKey, String mediaContentType) {
        requireStatus(GenerationAttemptStatus.SUCCEEDED);
        this.storageKey = DomainValidation.requireText(storageKey, "Media storage key cannot be blank");
        this.mediaContentType = DomainValidation.requireText(
                mediaContentType,
                "Media content type cannot be blank"
        )
                .toLowerCase(Locale.ROOT);
        if (output != null && output.startsWith("data:")) {
            this.output = null;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    private void requireStatus(GenerationAttemptStatus expected) {
        if (status != expected) {
            throw new DomainException("Generation attempt must be " + expected + " but was " + status);
        }
    }

    private void requireAnyStatus(GenerationAttemptStatus... expectedStatuses) {
        for (GenerationAttemptStatus expected : expectedStatuses) {
            if (status == expected) {
                return;
            }
        }
        throw new DomainException("Generation attempt has invalid status for this transition: " + status);
    }

    private static String requireHash(String value) {
        String hash = DomainValidation.requireText(value, "Prompt hash cannot be blank")
                .toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new DomainException("Prompt hash must be a SHA-256 value");
        }
        return hash;
    }

    private static String optionalValue(String value) {
        return DomainValidation.normalizeOptionalText(value);
    }
}
