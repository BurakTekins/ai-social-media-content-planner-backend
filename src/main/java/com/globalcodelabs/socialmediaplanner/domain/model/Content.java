package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentOperationNotAllowedException;
import com.globalcodelabs.socialmediaplanner.domain.event.ContentEvent;
import com.globalcodelabs.socialmediaplanner.domain.event.ContentPublicationFailed;
import com.globalcodelabs.socialmediaplanner.domain.event.ContentPublished;
import com.globalcodelabs.socialmediaplanner.domain.event.ContentScheduled;
import com.globalcodelabs.socialmediaplanner.common.exception.InvalidContentStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
public class Content {

    @Id
    private UUID id;

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
    private List<ContentMedia> media = new ArrayList<>();

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Transient
    private final List<ContentEvent> domainEvents = new ArrayList<>();

    private Content(
            UUID id,
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            UUID batchId,
            Integer generationIndex,
            OffsetDateTime createdAt
    ) {
        this.id = Objects.requireNonNull(id, "Content id cannot be null");
        this.platform = Objects.requireNonNull(platform, "Platform cannot be null");
        this.contentType = Objects.requireNonNull(contentType, "Content type cannot be null");
        validatePlatformContentType(this.platform, this.contentType);
        this.text = requireText(text);
        this.hashtags = sanitizeHashtags(hashtags);
        this.batchId = batchId;
        this.generationIndex = generationIndex;
        this.status = ContentStatus.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Content create(
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            UUID batchId
    ) {
        return new Content(
                UUID.randomUUID(), platform, contentType, text, hashtags, batchId,
                null,
                OffsetDateTime.now()
        );
    }

    public static Content createGenerated(
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
                UUID.randomUUID(), platform, contentType, text, hashtags, batchId,
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
        this.textProvider = requireGenerationValue(provider, "Text provider cannot be blank")
                .toLowerCase(Locale.ROOT);
        this.textModel = requireGenerationValue(model, "Text model cannot be blank");
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateDraft(String text, List<String> hashtags) {
        requireDraftOperation("Only draft content can be edited");
        if (text == null && hashtags == null) {
            throw new DomainException("At least one of text or hashtags must be provided");
        }

        String updatedText = text == null ? this.text : requireText(text);
        String[] updatedHashtags = hashtags == null ? this.hashtags : sanitizeHashtags(hashtags);

        this.text = updatedText;
        this.hashtags = updatedHashtags;
        this.updatedAt = OffsetDateTime.now();
    }

    public void schedule(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.DRAFT, ContentStatus.SCHEDULED);
        validateScheduledAt(scheduledAt);

        this.status = ContentStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.updatedAt = OffsetDateTime.now();
        this.domainEvents.add(new ContentScheduled(id, scheduledAt, updatedAt));
    }

    public void reschedule(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.SCHEDULED);
        validateScheduledAt(scheduledAt);

        this.scheduledAt = scheduledAt;
        this.updatedAt = OffsetDateTime.now();
        this.domainEvents.add(new ContentScheduled(id, scheduledAt, updatedAt));
    }

    public void retryPublishing(OffsetDateTime scheduledAt) {
        requireStatus(ContentStatus.FAILED, ContentStatus.SCHEDULED);
        validateScheduledAt(scheduledAt);

        this.status = ContentStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.publishedAt = null;
        this.failureReason = null;
        this.updatedAt = OffsetDateTime.now();
        this.domainEvents.add(new ContentScheduled(id, scheduledAt, updatedAt));
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

    public void markPublished() {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.PUBLISHED);

        this.status = ContentStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        this.failureReason = null;
        this.updatedAt = publishedAt;
        this.domainEvents.add(new ContentPublished(id, publishedAt));
    }

    public void markFailed(String reason) {
        requireStatus(ContentStatus.SCHEDULED, ContentStatus.FAILED);
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Failure reason cannot be blank");
        }

        this.status = ContentStatus.FAILED;
        this.failureReason = reason.trim();
        this.updatedAt = OffsetDateTime.now();
        this.domainEvents.add(new ContentPublicationFailed(id, failureReason, updatedAt));
    }

    public List<ContentEvent> pullDomainEvents() {
        List<ContentEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
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

    public ContentStatus status() {
        return status;
    }

    public String text() {
        return text;
    }

    public List<String> hashtags() {
        return Arrays.stream(hashtags).toList();
    }

    public List<ContentMedia> media() {
        return List.copyOf(media);
    }

    public UUID batchId() {
        return batchId;
    }

    public Integer generationIndex() {
        return generationIndex;
    }

    public String textProvider() {
        return textProvider;
    }

    public String textModel() {
        return textModel;
    }

    public OffsetDateTime scheduledAt() {
        return scheduledAt;
    }

    public OffsetDateTime publishedAt() {
        return publishedAt;
    }

    public String failureReason() {
        return failureReason;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
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

    private static void validateScheduledAt(OffsetDateTime scheduledAt) {
        if (scheduledAt == null || !scheduledAt.isAfter(OffsetDateTime.now())) {
            throw new DomainException("Scheduled time must be in the future");
        }
    }

    private static void validatePlatformContentType(Platform platform, ContentType contentType) {
        if (!platform.supports(contentType)) {
            throw new DomainException(
                    "Content type %s is not supported for platform %s".formatted(contentType, platform)
            );
        }
    }

    private static String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException("Content text cannot be blank");
        }
        return text.trim();
    }

    private static String[] sanitizeHashtags(List<String> hashtags) {
        if (hashtags == null) {
            return new String[0];
        }
        if (hashtags.stream().anyMatch(hashtag -> hashtag == null || hashtag.isBlank())) {
            throw new DomainException("Hashtags cannot contain blank values");
        }
        return hashtags.stream().map(String::trim).toArray(String[]::new);
    }

    private static String requireGenerationValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
        return value.trim();
    }

}
