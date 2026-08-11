package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceType;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "content_source")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class ContentSource {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    @Getter(AccessLevel.NONE)
    private GenerationBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ContentSourceType sourceType;

    @Column(name = "source_value", nullable = false, columnDefinition = "TEXT")
    private String sourceValue;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentSourceStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private ContentSource(GenerationBatch batch, ContentSourceType sourceType, String sourceValue) {
        this.id = UUID.randomUUID();
        this.batch = Objects.requireNonNull(batch, "Generation batch cannot be null");
        this.sourceType = Objects.requireNonNull(sourceType, "Source type cannot be null");
        this.sourceValue = DomainValidation.requireText(sourceValue, "Source value cannot be blank");
        this.status = ContentSourceStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    static ContentSource create(GenerationBatch batch, ContentSourceType sourceType, String sourceValue) {
        return new ContentSource(batch, sourceType, sourceValue);
    }

    public void startProcessing() {
        if (status != ContentSourceStatus.PENDING
                && status != ContentSourceStatus.PROCESSING
                && status != ContentSourceStatus.FAILED) {
            throw new DomainException("Only pending, processing or failed source can start processing");
        }
        this.status = ContentSourceStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void complete(String extractedText) {
        requireStatus(ContentSourceStatus.PROCESSING);
        this.extractedText = DomainValidation.requireText(
                extractedText,
                "Extracted text cannot be blank"
        );
        this.status = ContentSourceStatus.COMPLETED;
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        if (status != ContentSourceStatus.PENDING && status != ContentSourceStatus.PROCESSING) {
            throw new DomainException("Only pending or processing source can fail");
        }
        this.status = ContentSourceStatus.FAILED;
        this.errorMessage = DomainValidation.requireText(
                errorMessage,
                "Source error message cannot be blank"
        );
    }

    private void requireStatus(ContentSourceStatus expected) {
        if (status != expected) {
            throw new DomainException("Content source must be " + expected + " but was " + status);
        }
    }

}
