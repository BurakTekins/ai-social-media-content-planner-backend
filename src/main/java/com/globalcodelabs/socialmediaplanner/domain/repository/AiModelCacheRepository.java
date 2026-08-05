package com.globalcodelabs.socialmediaplanner.domain.repository;

import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiModelCacheRepository extends JpaRepository<AiModelCache, UUID> {

    List<AiModelCache> findAllByProviderNameIn(Collection<String> providerNames);

    @Query("""
            SELECT model
            FROM AiModelCache model
            WHERE (:capability IS NULL OR model.capability = :capability)
              AND (:providerName IS NULL OR model.providerName = :providerName)
            ORDER BY model.providerName, model.displayName, model.modelId
            """)
    List<AiModelCache> findAllByFilters(
            @Param("capability") AiCapability capability,
            @Param("providerName") String providerName
    );
}
