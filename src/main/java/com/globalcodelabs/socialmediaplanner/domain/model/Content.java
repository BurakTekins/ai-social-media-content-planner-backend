package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentOperationNotAllowedException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.policy.ContentPolicy;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import com.globalcodelabs.socialmediaplanner.common.exception.InvalidContentStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.BatchSize;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class Content {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "TEXT[]")
    @Getter(AccessLevel.NONE)
    private String[] hashtags;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "generation_index")
    private Integer generationIndex;

    @Column(name = "text_provider")
    private String textProvider;

    @Column(name = "text_model")
    private String textModel;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    @Getter(AccessLevel.NONE)
    private List<ContentMedia> media = new ArrayList<>();

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "publish_operation_id")
    private UUID publishOperationId;

    @Column(name = "external_post_id")
    private String externalPostId;

    @Column(name = "publishing_started_at")
    private OffsetDateTime publishingStartedAt;

    @Column(name = "publication_checked_at")
    private OffsetDateTime publicationCheckedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    private Content(
            UUID id,
            String title,
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            UUID batchId,
            Integer generationIndex,
            OffsetDateTime createdAt
    ) {
        this.id = Objects.requireNonNull(id, "Content id cannot be null");
        this.title = ContentPolicy.normalizeTitle(title);
        this.platform = Objects.requireNonNull(platform, "Platform cannot be null");
        this.contentType = Objects.requireNonNull(contentType, "Content type cannot be null");
        ContentPolicy.validatePlatformContentType(this.platform, this.contentType);
        this.text = DomainValidation.requireText(text, "Content text cannot be blank");
        this.hashtags = ContentPolicy.sanitizeHashtags(hashtags);
        ContentPolicy.validateContent(this.platform, this.text, this.hashtags);
        this.batchId = batchId;
        this.generationIndex = generationIndex;
        this.status = ContentStatus.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Content create(
            String title,
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            UUID batchId
    ) {
        return new Content(
                UUID.randomUUID(), title, platform, contentType, text, hashtags, batchId,
                null,
                OffsetDateTime.now()
        );
    }

    public static Content createGenerated(
            String title,
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            UUID batchId,
            int generationIndex
    ) {
        Objects.requireNonNull(batchId, "Generation batch id cannot be null");
        if (generationIndex <= 0) {
            throw new DomainException("Generation index must be greater than zero");
        }
        return new Content(
                UUID.randomUUID(), title, platform, contentType, text, hashtags, batchId,
                generationIndex, OffsetDateTime.now()
        );
    }

    public void addMedia(
            MediaType mediaType,
            String storageKey,
            String publicUrl,
            String modelProvider,
            String modelId
    ) {
        requireDraftOperation("Only draft content media can be changed");
        boolean alreadyExists = media.stream().anyMatch(existing -> existing.mediaType() == mediaType);
        if (alreadyExists) {
            throw new DomainException("Content already has " + mediaType + " media");
        }
        media.add(ContentMedia.create(this, mediaType, storageKey, publicUrl, modelProvider, modelId));
        updatedAt = OffsetDateTime.now();
    }

    public String replaceMedia(
            MediaType mediaType,
            String storageKey,
            String publicUrl,
            String modelProvider,
            String modelId
    ) {
        requireDraftOperation("Only draft content media can be changed");
        ContentMedia existing = findMedia(mediaType).orElse(null);
        String replacedStorageKey = null;
        if (existing == null) {
            media.add(ContentMedia.create(
                    this, mediaType, storageKey, publicUrl, modelProvider, modelId
            ));
        } else {
            replacedStorageKey = existing.storageKey();
            existing.replaceWith(storageKey, publicUrl, modelProvider, modelId);
        }
        updatedAt = OffsetDateTime.now();
        return replacedStorageKey;
    }

    public String removeMedia(MediaType mediaType) {
        requireDraftOperation("Only draft content media can be changed");
        ContentMedia existing = findMedia(mediaType)
                .orElseThrow(() -> new DomainException("Content does not have " + mediaType + " media"));
        media.remove(existing);
        updatedAt = OffsetDateTime.now();
        return existing.storageKey();
    }

    public Optional<ContentMedia> findMedia(MediaType mediaType) {
        return media.stream()
                .filter(existing -> existing.mediaType() == mediaType)
                .findFirst();
    }

    public void recordTextGeneration(String provider, String model) {
        if (status != ContentStatus.DRAFT) {
            throw new DomainException("Text generation metadata can only be changed for draft content");
        }
        this.textProvider = DomainValidation.requireText(provider, "Text provider cannot be blank")
                .toLowerCase(Locale.ROOT);
        this.textModel = DomainValidation.requireText(model, "Text model cannot be blank");
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateDraft(String title, String text, List<String> hashtags) {
        requireDraftOperation("Only draft content can be edited");
        if (title == null && text == null && hashtags == null) {
            throw new DomainException("At least one of title, text or hashtags must be provided");
        }

        String updatedTitle = title == null ? this.title : ContentPolicy.normalizeTitle(title);
        String updatedText = text == null
                ? this.text
                : DomainValidation.requireText(text, "Content text cannot be blank");
        String[] updatedHashtags = hashtags == null
                ? this.hashtags
                : ContentPolicy.sanitizeHashtags(hashtags);
        ContentPolicy.validateContent(platform, updatedText, updatedHashtags);

        this.title = updatedTitle;
        this.text = updatedText;
        this.hashtags = updatedHashtags;
        this.updatedAt = OffsetDateTime.now();
    }

    public void schedule(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.DRAFT, ContentStatus.SCHEDULED);
        ContentPolicy.validateScheduledAt(scheduledAt);
        ContentPolicy.validateContent(platform, text, hashtags);

        this.status = ContentStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void reschedule(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.SCHEDULED);
        ContentPolicy.validateScheduledAt(scheduledAt);

        this.scheduledAt = scheduledAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void retryPublishing(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.FAILED, ContentStatus.SCHEDULED);
        ContentPolicy.validateScheduledAt(scheduledAt);

        this.status = ContentStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.publishedAt = null;
        this.publishOperationId = null;
        this.externalPostId = null;
        this.publishingStartedAt = null;
        this.publicationCheckedAt = null;
        this.failureReason = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancelSchedule() {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.DRAFT);

        this.status = ContentStatus.DRAFT;
        this.scheduledAt = null;
        this.publishedAt = null;
        this.failureReason = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void ensureDeletable() {
        requireDraftOperation("Only draft content can be deleted");
    }

    public void ensureRegeneratable() {
        requireDraftOperation("Only draft content can be regenerated");
    }

    public void startPublishing(UUID operationId) {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.PUBLISHING);
        this.status = ContentStatus.PUBLISHING;
        this.publishOperationId = Objects.requireNonNull(operationId, "Publish operation id cannot be null");
        this.publishingStartedAt = OffsetDateTime.now();
        this.publicationCheckedAt = null;
        this.externalPostId = null;
        this.failureReason = null;
        this.updatedAt = publishingStartedAt;
    }

    public void recordExternalPostId(String externalPostId) {
        requireStatus(ContentStatus.PUBLISHING, ContentStatus.PUBLISHING);
        this.externalPostId = DomainValidation.requireText(externalPostId, "External post id cannot be blank");
        this.publicationCheckedAt = OffsetDateTime.now();
        this.failureReason = null;
        this.updatedAt = publicationCheckedAt;
    }

    public void recordPublicationCheck() {
        requireStatus(ContentStatus.PUBLISHING, ContentStatus.PUBLISHING);
        this.publicationCheckedAt = OffsetDateTime.now();
        this.updatedAt = publicationCheckedAt;
    }

    public void markPublishingUncertain(String reason) {
        requireStatus(ContentStatus.PUBLISHING, ContentStatus.PUBLISHING);
        this.failureReason = DomainValidation.requireText(
                reason,
                "Publishing uncertainty reason cannot be blank"
        );
        this.updatedAt = OffsetDateTime.now();
    }

    public void requirePublicationReview(String reason) {
        requireStatus(ContentStatus.PUBLISHING, ContentStatus.REVIEW_REQUIRED);
        this.status = ContentStatus.REVIEW_REQUIRED;
        this.failureReason = DomainValidation.requireText(reason, "Publication review reason cannot be blank");
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPublished() {
        requireStatus(ContentStatus.PUBLISHING, ContentStatus.PUBLISHED);
        if (externalPostId == null || externalPostId.isBlank()) {
            throw new DomainException("External post id is required before publication confirmation");
        }

        this.status = ContentStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        this.failureReason = null;
        this.updatedAt = publishedAt;
    }

    public void markFailed(String reason) {
        if (status != ContentStatus.SCHEDULED && status != ContentStatus.PUBLISHING) {
            throw new InvalidContentStateTransitionException(status, ContentStatus.FAILED);
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Failure reason cannot be blank");
        }

        this.status = ContentStatus.FAILED;
        this.failureReason = reason.trim();
        this.updatedAt = OffsetDateTime.now();
    }

    public List<String> hashtags() {
        return Arrays.stream(hashtags).toList();
    }

    public List<ContentMedia> media() {
        return List.copyOf(media);
    }

    private void requireStatus(ContentStatus expected, ContentStatus target) {
        if (status != expected) {
            throw new InvalidContentStateTransitionException(status, target);
        }
    }

    private void requireDraftOperation(String message) {
        if (status != ContentStatus.DRAFT) {
            throw new ContentOperationNotAllowedException(message);
        }
    }

}
