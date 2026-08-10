package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "generation_batch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationBatch {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GenerationBatchStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_retry_at")
    private OffsetDateTime lastRetryAt;

    @Column(name = "include_image", nullable = false)
    private boolean includeImage;

    @Column(name = "include_video", nullable = false)
    private boolean includeVideo;

    @Column(name = "text_provider", nullable = false)
    private String textProvider;

    @Column(name = "text_model", nullable = false)
    private String textModel;

    @Column(name = "image_provider")
    private String imageProvider;

    @Column(name = "image_model")
    private String imageModel;

    @Column(name = "video_provider")
    private String videoProvider;

    @Column(name = "video_model")
    private String videoModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_strategy", nullable = false, length = 20)
    private GenerationStrategy generationStrategy;

    @Column(name = "strategy_selection_reason", nullable = false, columnDefinition = "TEXT")
    private String strategySelectionReason;

    @Column(name = "strategy_warning", columnDefinition = "TEXT")
    private String strategyWarning;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContentSource> sources = new ArrayList<>();

    private GenerationBatch(
            Platform platform,
            ContentType contentType,
            int requestedCount,
            boolean includeImage,
            boolean includeVideo,
            String textProvider,
            String textModel,
            String imageProvider,
            String imageModel,
            String videoProvider,
            String videoModel,
            GenerationStrategy requestedStrategy,
            int sourceCount
    ) {
        validatePlatformContentType(platform, contentType);
        if (requestedCount <= 0) {
            throw new DomainException("Requested count must be greater than zero");
        }
        if (sourceCount <= 0) {
            throw new DomainException("At least one source is required");
        }
        validateOptionalModel(includeImage, imageProvider, imageModel, "image");
        validateOptionalModel(includeVideo, videoProvider, videoModel, "video");

        StrategySelection strategySelection = selectStrategy(requestedStrategy, sourceCount, requestedCount);

        this.id = UUID.randomUUID();
        this.platform = platform;
        this.contentType = contentType;
        this.requestedCount = requestedCount;
        this.completedCount = 0;
        this.status = GenerationBatchStatus.IN_PROGRESS;
        this.retryCount = 0;
        this.includeImage = includeImage;
        this.includeVideo = includeVideo;
        this.textProvider = requireProvider(textProvider, "Text provider cannot be blank");
        this.textModel = requireValue(textModel, "Text model cannot be blank");
        this.imageProvider = normalizedOptionalProvider(imageProvider);
        this.imageModel = normalizedOptionalValue(imageModel);
        this.videoProvider = normalizedOptionalProvider(videoProvider);
        this.videoModel = normalizedOptionalValue(videoModel);
        this.generationStrategy = strategySelection.strategy();
        this.strategySelectionReason = strategySelection.reason();
        this.strategyWarning = strategySelection.warning();
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    public static GenerationBatch create(
            Platform platform,
            ContentType contentType,
            int requestedCount,
            boolean includeImage,
            boolean includeVideo,
            String textProvider,
            String textModel,
            String imageProvider,
            String imageModel,
            String videoProvider,
            String videoModel,
            GenerationStrategy requestedStrategy,
            int sourceCount
    ) {
        return new GenerationBatch(
                platform, contentType, requestedCount, includeImage, includeVideo,
                textProvider, textModel, imageProvider, imageModel, videoProvider, videoModel,
                requestedStrategy, sourceCount
        );
    }

    public void addLinkSource(String url) {
        ensureInProgress();
        sources.add(ContentSource.create(this, ContentSourceType.LINK, url));
        updatedAt = OffsetDateTime.now();
    }

    public void addDocumentSource(String storageKey) {
        ensureInProgress();
        sources.add(ContentSource.create(this, ContentSourceType.DOCUMENT, storageKey));
        updatedAt = OffsetDateTime.now();
    }

    public void recordCompletedContent() {
        ensureInProgress();
        if (completedCount >= requestedCount) {
            throw new DomainException("Completed count cannot exceed requested count");
        }
        completedCount++;
        updatedAt = OffsetDateTime.now();
        if (completedCount == requestedCount) {
            status = GenerationBatchStatus.COMPLETED;
        }
    }

    public void reconcileCompletedCount(int persistedContentCount) {
        ensureInProgress();
        if (persistedContentCount < 0 || persistedContentCount > requestedCount) {
            throw new DomainException("Persisted content count must be between zero and requested count");
        }
        completedCount = persistedContentCount;
        updatedAt = OffsetDateTime.now();
        if (completedCount == requestedCount) {
            status = GenerationBatchStatus.COMPLETED;
        }
    }

    public void markFailed() {
        markFailed(null);
    }

    public void markFailed(String errorMessage) {
        ensureInProgress();
        status = GenerationBatchStatus.FAILED;
        lastError = normalizedOptionalValue(errorMessage);
        updatedAt = OffsetDateTime.now();
    }

    public void retry() {
        if (status != GenerationBatchStatus.FAILED) {
            throw new DomainException("Only failed generation batch can be retried");
        }
        OffsetDateTime retriedAt = OffsetDateTime.now();
        status = GenerationBatchStatus.IN_PROGRESS;
        retryCount++;
        lastRetryAt = retriedAt;
        updatedAt = retriedAt;
    }

    public UUID id() {
        return id;
    }

    public Platform platform() {
        return platform;
    }

    public ContentType contentType() {
        return contentType;
    }

    public int requestedCount() {
        return requestedCount;
    }

    public int completedCount() {
        return completedCount;
    }

    public GenerationBatchStatus status() {
        return status;
    }

    public int retryCount() {
        return retryCount;
    }

    public String lastError() {
        return lastError;
    }

    public OffsetDateTime lastRetryAt() {
        return lastRetryAt;
    }

    public boolean includeImage() {
        return includeImage;
    }

    public boolean includeVideo() {
        return includeVideo;
    }

    public String textProvider() {
        return textProvider;
    }

    public String textModel() {
        return textModel;
    }

    public String imageProvider() {
        return imageProvider;
    }

    public String imageModel() {
        return imageModel;
    }

    public String videoProvider() {
        return videoProvider;
    }

    public String videoModel() {
        return videoModel;
    }

    public GenerationStrategy generationStrategy() {
        return generationStrategy;
    }

    public String strategySelectionReason() {
        return strategySelectionReason;
    }

    public String strategyWarning() {
        return strategyWarning;
    }

    public List<ContentSource> sources() {
        return List.copyOf(sources);
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private void ensureInProgress() {
        if (status != GenerationBatchStatus.IN_PROGRESS) {
            throw new DomainException("Generation batch is not in progress");
        }
    }

    private static void validatePlatformContentType(Platform platform, ContentType contentType) {
        Objects.requireNonNull(platform, "Platform cannot be null");
        Objects.requireNonNull(contentType, "Content type cannot be null");
        if (!platform.supports(contentType)) {
            throw new DomainException("Content type " + contentType + " is not supported by " + platform);
        }
    }

    private static void validateOptionalModel(boolean included, String provider, String model, String capability) {
        boolean providerPresent = provider != null && !provider.isBlank();
        boolean modelPresent = model != null && !model.isBlank();
        if (included && (!providerPresent || !modelPresent)) {
            throw new DomainException(capability + " provider and model are required");
        }
        if (!included && (providerPresent || modelPresent)) {
            throw new DomainException(capability + " provider and model must be empty when disabled");
        }
    }

    private static String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
        return value.trim();
    }

    private static String normalizedOptionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireProvider(String value, String message) {
        return requireValue(value, message).toLowerCase(Locale.ROOT);
    }

    private static String normalizedOptionalProvider(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static StrategySelection selectStrategy(
            GenerationStrategy requestedStrategy,
            int sourceCount,
            int requestedCount
    ) {
        if (requestedStrategy == null) {
            if (sourceCount == requestedCount) {
                return new StrategySelection(
                        GenerationStrategy.SOURCE_BASED,
                        "Defaulted to SOURCE_BASED because source count (%d) equals requested content count (%d)."
                                .formatted(sourceCount, requestedCount),
                        null
                );
            }
            return new StrategySelection(
                    GenerationStrategy.COMBINED,
                    "Defaulted to COMBINED because source count (%d) differs from requested content count (%d)."
                            .formatted(sourceCount, requestedCount),
                    null
            );
        }

        String warning = requestedStrategy == GenerationStrategy.SOURCE_BASED && sourceCount < requestedCount
                ? ("SOURCE_BASED was explicitly selected with fewer sources (%d) than requested contents (%d); "
                + "sources will be reused round-robin.").formatted(sourceCount, requestedCount)
                : null;
        return new StrategySelection(
                requestedStrategy,
                "User selected %s strategy.".formatted(requestedStrategy),
                warning
        );
    }

    private record StrategySelection(
            GenerationStrategy strategy,
            String reason,
            String warning
    ) {
    }
}
