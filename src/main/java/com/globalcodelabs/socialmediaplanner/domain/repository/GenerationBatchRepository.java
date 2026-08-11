package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GenerationBatchRepository extends JpaRepository<GenerationBatch, UUID> {

    @EntityGraph(attributePaths = "sources")
    Optional<GenerationBatch> findOneById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT generationBatch FROM GenerationBatch generationBatch WHERE generationBatch.id = :batchId")
    Optional<GenerationBatch> findByIdForUpdate(@Param("batchId") UUID batchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE GenerationBatch generationBatch
            SET generationBatch.status = :inProgressStatus,
                generationBatch.retryCount = generationBatch.retryCount + 1,
                generationBatch.lastRetryAt = :retriedAt,
                generationBatch.updatedAt = :retriedAt
            WHERE generationBatch.id = :batchId
              AND generationBatch.status = :failedStatus
            """)
    int claimFailedForRetry(
            @Param("batchId") UUID batchId,
            @Param("failedStatus") GenerationBatchStatus failedStatus,
            @Param("inProgressStatus") GenerationBatchStatus inProgressStatus,
            @Param("retriedAt") OffsetDateTime retriedAt
    );

    @Query("""
            SELECT generationBatch
            FROM GenerationBatch generationBatch
            WHERE (:status IS NULL OR generationBatch.status = :status)
              AND (:platform IS NULL OR generationBatch.platform = :platform)
              AND (:contentType IS NULL OR generationBatch.contentType = :contentType)
            """)
    Page<GenerationBatch> findAllByFilters(
            @Param("status") GenerationBatchStatus status,
            @Param("platform") Platform platform,
            @Param("contentType") ContentType contentType,
            Pageable pageable
    );
}
