package com.globalcodelabs.socialmediaplanner.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
@Getter
@Accessors(fluent = true)
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
    @Getter(AccessLevel.NONE)
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
        this.modelId = DomainValidation.requireText(modelId, "Model id cannot be blank");
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

    public JsonNode rawMetadata() {
        return rawMetadata.deepCopy();
    }

    private void applySnapshot(
            String displayName,
            JsonNode rawMetadata,
            OffsetDateTime lastSyncedAt
    ) {
        this.displayName = DomainValidation.requireText(displayName, "Display name cannot be blank");
        if (rawMetadata == null || !rawMetadata.isObject()) {
            throw new DomainException("Raw model metadata must be a JSON object");
        }
        this.rawMetadata = rawMetadata.deepCopy();
        this.lastSyncedAt = Objects.requireNonNull(lastSyncedAt, "Last synced time cannot be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String requireProviderName(String providerName) {
        return DomainValidation.requireText(providerName, "Provider name cannot be blank")
                .toLowerCase(Locale.ROOT);
    }
}
