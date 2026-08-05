package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface GenerationAttemptRepository extends JpaRepository<GenerationAttempt, UUID> {

    Optional<GenerationAttempt> findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
            UUID batchId,
            int generationIndex,
            AiCapability capability,
            String promptHash,
            GenerationAttemptStatus status
    );

    boolean existsByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusIn(
            UUID batchId,
            int generationIndex,
            AiCapability capability,
            String promptHash,
            Collection<GenerationAttemptStatus> statuses
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE GenerationAttempt attempt
            SET attempt.status = :unknownStatus,
                attempt.errorMessage = :errorMessage,
                attempt.updatedAt = :updatedAt
            WHERE attempt.batchId = :batchId
              AND attempt.status = :startedStatus
            """)
    int markStartedUnknownForBatch(
            @Param("batchId") UUID batchId,
            @Param("startedStatus") GenerationAttemptStatus startedStatus,
            @Param("unknownStatus") GenerationAttemptStatus unknownStatus,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
