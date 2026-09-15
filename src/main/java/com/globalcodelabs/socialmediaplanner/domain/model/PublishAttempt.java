package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "publish_attempt")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class PublishAttempt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    @Getter(AccessLevel.NONE)
    private Content content;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "external_post_id")
    private String externalPostId;

    private PublishAttempt(
            Content content,
            boolean success,
            String errorMessage,
            String externalPostId
    ) {
        this.id = UUID.randomUUID();
        this.content = Objects.requireNonNull(content, "Content cannot be null");
        this.attemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.success = success;
        this.errorMessage = errorMessage;
        this.externalPostId = externalPostId;
    }

    public static PublishAttempt success(Content content, String externalPostId) {
        return new PublishAttempt(
                content,
                true,
                null,
                requireValue(externalPostId, "External post id cannot be blank")
        );
    }

    public static PublishAttempt failure(Content content, String errorMessage) {
        return new PublishAttempt(
                content,
                false,
                requireValue(errorMessage, "Error message cannot be blank"),
                null
        );
    }

    public UUID contentId() {
        return content.id();
    }

    public Platform platform() {
        return content.platform();
    }

    private static String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
