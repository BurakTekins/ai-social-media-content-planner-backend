package com.globalcodelabs.socialmediaplanner.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ai_model_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "ai_model_cache_provider_model_capability_unique",
                columnNames = {"provider_name", "model_id", "capability"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiModelCache {

    @Id
    private UUID id;

    @Column(name = "provider_name", nullable = false, updatable = false)
    private String providerName;

    @Column(name = "model_id", nullable = false, updatable = false)
    private String modelId;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AiCapability capability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_metadata", columnDefinition = "jsonb")
    private JsonNode rawMetadata;

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt;

    private AiModelCache(
            String providerName,
            String modelId,
            String displayName,
            AiCapability capability,
            JsonNode rawMetadata,
            OffsetDateTime lastSyncedAt
    ) {
        this.id = UUID.randomUUID();
        this.providerName = requireProviderName(providerName);
        this.modelId = requireText(modelId, "Model id cannot be blank");
        this.capability = Objects.requireNonNull(capability, "Capability cannot be null");
        applySnapshot(displayName, rawMetadata, lastSyncedAt);
    }

    public static AiModelCache create(
            String providerName,
            String modelId,
            String displayName,
            AiCapability capability,
            JsonNode rawMetadata,
            OffsetDateTime lastSyncedAt
    ) {
        return new AiModelCache(
                providerName,
                modelId,
                displayName,
                capability,
                rawMetadata,
                lastSyncedAt
        );
    }

    public void refresh(
            String displayName,
            JsonNode rawMetadata,
            OffsetDateTime lastSyncedAt
    ) {
        applySnapshot(displayName, rawMetadata, lastSyncedAt);
    }

    public UUID id() {
        return id;
    }

    public String providerName() {
        return providerName;
    }

    public String modelId() {
        return modelId;
    }

    public String displayName() {
        return displayName;
    }

    public AiCapability capability() {
        return capability;
    }

    public JsonNode rawMetadata() {
        return rawMetadata.deepCopy();
    }

    public OffsetDateTime lastSyncedAt() {
        return lastSyncedAt;
    }

    private void applySnapshot(
            String displayName,
            JsonNode rawMetadata,
            OffsetDateTime lastSyncedAt
    ) {
        this.displayName = requireText(displayName, "Display name cannot be blank");
        if (rawMetadata == null || !rawMetadata.isObject()) {
            throw new DomainException("Raw model metadata must be a JSON object");
        }
        this.rawMetadata = rawMetadata.deepCopy();
        this.lastSyncedAt = Objects.requireNonNull(lastSyncedAt, "Last synced time cannot be null");
    }

    private static String requireProviderName(String providerName) {
        return requireText(providerName, "Provider name cannot be blank")
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
        return value.trim();
    }
}
