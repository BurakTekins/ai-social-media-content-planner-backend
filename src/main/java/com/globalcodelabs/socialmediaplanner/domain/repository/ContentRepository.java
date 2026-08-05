package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    boolean existsByBatchIdAndGenerationIndex(UUID batchId, Integer generationIndex);

    @Query("""
            SELECT content.generationIndex
            FROM Content content
            WHERE content.batchId = :batchId
              AND content.generationIndex IS NOT NULL
            ORDER BY content.generationIndex
            """)
    List<Integer> findGenerationIndexesByBatchId(@Param("batchId") UUID batchId);

    @Query("""
            SELECT content
            FROM Content content
            WHERE (:status IS NULL OR content.status = :status)
              AND (:platform IS NULL OR content.platform = :platform)
              AND (:batchId IS NULL OR content.batchId = :batchId)
            """)
    Page<Content> findAllByFilters(
            @Param("status") ContentStatus status,
            @Param("platform") Platform platform,
            @Param("batchId") UUID batchId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "media")
    @Query("SELECT content FROM Content content WHERE content.id = :contentId")
    Optional<Content> findWithMediaById(@Param("contentId") UUID contentId);

    @EntityGraph(attributePaths = "media")
    @Query("""
            SELECT DISTINCT content
            FROM Content content
            WHERE content.scheduledAt >= :from
              AND content.scheduledAt < :to
              AND (:status IS NULL OR content.status = :status)
              AND (:platform IS NULL OR content.platform = :platform)
            ORDER BY content.scheduledAt, content.id
            """)
    List<Content> findCalendarEntries(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("status") ContentStatus status,
            @Param("platform") Platform platform
    );

    @Query(value = """
            SELECT content.id
            FROM content
            WHERE content.status = 'SCHEDULED'
              AND content.scheduled_at <= :now
            ORDER BY content.scheduled_at, content.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextDueContentId(@Param("now") OffsetDateTime now);
}
