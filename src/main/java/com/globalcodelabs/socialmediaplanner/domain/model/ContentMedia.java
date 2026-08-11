package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "content_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class ContentMedia {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    @Getter(AccessLevel.NONE)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "public_url", columnDefinition = "TEXT")
    private String publicUrl;

    @Column(name = "model_provider")
    private String modelProvider;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private ContentMedia(
            Content content,
            MediaType mediaType,
            String storageKey,
            String publicUrl,
            String modelProvider,
            String modelId
    ) {
        this.id = UUID.randomUUID();
        this.content = Objects.requireNonNull(content, "Content cannot be null");
        this.mediaType = Objects.requireNonNull(mediaType, "Media type cannot be null");
        this.storageKey = requireStorageKey(storageKey);
        validatePublicUrl(publicUrl);
        this.publicUrl = publicUrl;
        this.modelProvider = modelProvider;
        this.modelId = modelId;
        this.createdAt = OffsetDateTime.now();
    }

    static ContentMedia create(
            Content content,
            MediaType mediaType,
            String storageKey,
            String publicUrl,
            String modelProvider,
            String modelId
    ) {
        return new ContentMedia(content, mediaType, storageKey, publicUrl, modelProvider, modelId);
    }

    void replaceWith(
            String storageKey,
            String publicUrl,
            String modelProvider,
            String modelId
    ) {
        this.storageKey = requireStorageKey(storageKey);
        validatePublicUrl(publicUrl);
        this.publicUrl = publicUrl;
        this.modelProvider = modelProvider;
        this.modelId = modelId;
        this.createdAt = OffsetDateTime.now();
    }

    private static String requireStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException("Storage key cannot be blank");
        }
        return storageKey.trim();
    }

    private static void validatePublicUrl(String publicUrl) {
        if (publicUrl == null) {
            return;
        }
        try {
            if (!URI.create(publicUrl).isAbsolute()) {
                throw new DomainException("Public URL must be absolute");
            }
        } catch (IllegalArgumentException exception) {
            throw new DomainException("Public URL is invalid");
        }
    }
}
