package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceType;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.policy.ContentPolicy;
import com.globalcodelabs.socialmediaplanner.domain.policy.GenerationBatchPolicy;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "generation_batch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class GenerationBatch {

    @Id
    private UUID id;

    @Column(nullable = false, length = 240)
    private String title;

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

    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provider", column = @Column(name = "text_provider", nullable = false)),
            @AttributeOverride(name = "model", column = @Column(name = "text_model", nullable = false))
    })
    @Getter(AccessLevel.NONE)
    private AiModelSelection textModelSelection;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provider", column = @Column(name = "image_provider")),
            @AttributeOverride(name = "model", column = @Column(name = "image_model"))
    })
    @Getter(AccessLevel.NONE)
    private AiModelSelection imageModelSelection;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provider", column = @Column(name = "video_provider")),
            @AttributeOverride(name = "model", column = @Column(name = "video_model"))
    })
    @Getter(AccessLevel.NONE)
    private AiModelSelection videoModelSelection;

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
    @Getter(AccessLevel.NONE)
    private List<ContentSource> sources = new ArrayList<>();

    private GenerationBatch(
            String title,
            Platform platform,
            ContentType contentType,
            int requestedCount,
            AiModelSelection textModelSelection,
            AiModelSelection imageModelSelection,
            AiModelSelection videoModelSelection,
            Integer videoDurationSeconds,
            GenerationStrategy requestedStrategy,
            int sourceCount
    ) {
        GenerationBatchPolicy.validatePlatformContentType(platform, contentType);
        ContentPolicy.validatePlatformMediaSelection(
                platform,
                contentType,
                imageModelSelection != null,
                videoModelSelection != null
        );
        if (requestedCount <= 0) {
            throw new DomainException("Requested count must be greater than zero");
        }
        if (sourceCount <= 0) {
            throw new DomainException("At least one source is required");
        }
        GenerationBatchPolicy.Selection strategySelection = GenerationBatchPolicy.selectStrategy(
                requestedStrategy,
                sourceCount,
                requestedCount
        );

        this.id = UUID.randomUUID();
        this.title = GenerationBatchPolicy.normalizeTitle(title);
        this.platform = platform;
        this.contentType = contentType;
        this.requestedCount = requestedCount;
        this.completedCount = 0;
        this.status = GenerationBatchStatus.IN_PROGRESS;
        this.retryCount = 0;
        this.includeImage = imageModelSelection != null;
        this.includeVideo = videoModelSelection != null;
        validateVideoDuration(videoModelSelection, videoDurationSeconds);
        this.videoDurationSeconds = videoDurationSeconds;
        this.textModelSelection = Objects.requireNonNull(
                textModelSelection,
                "Text model selection cannot be null"
        );
        this.imageModelSelection = imageModelSelection;
        this.videoModelSelection = videoModelSelection;
        this.generationStrategy = strategySelection.strategy();
        this.strategySelectionReason = strategySelection.reason();
        this.strategyWarning = strategySelection.warning();
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    public static GenerationBatch create(
            String title,
            Platform platform,
            ContentType contentType,
            int requestedCount,
            AiModelSelection textModelSelection,
            AiModelSelection imageModelSelection,
            AiModelSelection videoModelSelection,
            Integer videoDurationSeconds,
            GenerationStrategy requestedStrategy,
            int sourceCount
    ) {
        return new GenerationBatch(
                title, platform, contentType, requestedCount,
                textModelSelection, imageModelSelection, videoModelSelection,
                videoDurationSeconds,
                requestedStrategy, sourceCount
        );
    }

    public void addLinkSource(String url) {
        ensureInProgress();
        sources.add(ContentSource.create(this, ContentSourceType.LINK, url));
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void addDocumentSource(String storageKey) {
        ensureInProgress();
        sources.add(ContentSource.create(this, ContentSourceType.DOCUMENT, storageKey));
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void recordCompletedContent() {
        ensureInProgress();
        if (completedCount >= requestedCount) {
            throw new DomainException("Completed count cannot exceed requested count");
        }
        completedCount++;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
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
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
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
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void retry() {
        if (status != GenerationBatchStatus.FAILED) {
            throw new DomainException("Only failed generation batch can be retried");
        }
        OffsetDateTime retriedAt = OffsetDateTime.now(ZoneOffset.UTC);
        status = GenerationBatchStatus.IN_PROGRESS;
        retryCount++;
        lastRetryAt = retriedAt;
        updatedAt = retriedAt;
    }

    public List<ContentSource> sources() {
        return List.copyOf(sources);
    }

    public String textProvider() {
        return textModelSelection.provider();
    }

    public String textModel() {
        return textModelSelection.model();
    }

    public String imageProvider() {
        return imageModelSelection == null ? null : imageModelSelection.provider();
    }

    public String imageModel() {
        return imageModelSelection == null ? null : imageModelSelection.model();
    }

    public String videoProvider() {
        return videoModelSelection == null ? null : videoModelSelection.provider();
    }

    public String videoModel() {
        return videoModelSelection == null ? null : videoModelSelection.model();
    }

    private void ensureInProgress() {
        if (status != GenerationBatchStatus.IN_PROGRESS) {
            throw new DomainException("Generation batch is not in progress");
        }
    }

    private static String normalizedOptionalValue(String value) {
        return DomainValidation.normalizeOptionalText(value);
    }

    private static void validateVideoDuration(
            AiModelSelection videoModelSelection,
            Integer videoDurationSeconds
    ) {
        if (videoModelSelection == null && videoDurationSeconds != null) {
            throw new DomainException("Video duration must be empty when video generation is disabled");
        }
        if (videoModelSelection != null && (videoDurationSeconds == null || videoDurationSeconds <= 0)) {
            throw new DomainException("Video duration is required when video generation is enabled");
        }
    }

}
