package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchNotFoundException;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeneratedContentFinalizer {

    private final GenerationBatchRepository generationBatchRepository;
    private final ContentRepository contentRepository;

    @Transactional
    public GenerationBatch reconcileCompletedCount(UUID batchId, int persistedContentCount) {
        GenerationBatch batch = lockBatch(batchId);
        batch.reconcileCompletedCount(persistedContentCount);
        return generationBatchRepository.saveAndFlush(batch);
    }

    @Transactional
    public boolean storeGeneratedContent(UUID batchId, int generationIndex, Content content) {
        GenerationBatch batch = lockBatch(batchId);
        if (batch.status() != GenerationBatchStatus.IN_PROGRESS) {
            throw new DomainException("Generation batch is not in progress");
        }
        if (contentRepository.existsByBatchIdAndGenerationIndex(batchId, generationIndex)) {
            log.warn("Generated content slot already exists; duplicate result ignored batchId={} generationIndex={}",
                    batchId, generationIndex);
            return false;
        }
        if (!batchId.equals(content.batchId()) || !Integer.valueOf(generationIndex).equals(content.generationIndex())) {
            throw new DomainException("Generated content does not match generation slot");
        }

        contentRepository.saveAndFlush(content);
        batch.recordCompletedContent();
        generationBatchRepository.saveAndFlush(batch);
        log.info("Generated content persisted contentId={} batchId={} generationIndex={}",
                content.id(), batchId, generationIndex);
        return true;
    }

    private GenerationBatch lockBatch(UUID batchId) {
        return generationBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
    }
}
