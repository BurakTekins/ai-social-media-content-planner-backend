package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateGenerationBatchCommand;
import com.globalcodelabs.socialmediaplanner.application.command.UploadedDocument;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalDocumentStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredDocument;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchRetryConflictException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelSelection;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import com.globalcodelabs.socialmediaplanner.domain.policy.ContentPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationBatchService {

    private final GenerationBatchRepository generationBatchRepository;
    private final GenerationAttemptRepository generationAttemptRepository;
    private final LocalDocumentStorage documentStorage;
    private final AiProviderFactory aiProviderFactory;
    private final GenerationBudgetPolicy generationBudgetPolicy;

    @Transactional
    public GenerationBatch create(CreateGenerationBatchCommand command) {
        ContentPolicy.validatePlatformMediaSelection(
                command.platform(),
                command.contentType(),
                command.includeImage(),
                command.includeVideo()
        );
        List<String> links = command.links() == null ? List.of() : command.links();
        List<UploadedDocument> documents = command.documents() == null ? List.of() : command.documents();
        if (links.isEmpty() && documents.isEmpty()) {
            throw new DomainException("At least one link or document is required");
        }
        int sourceCount = links.size() + documents.size();
        generationBudgetPolicy.validate(new GenerationBudgetPolicy.Request(
                command.requestedCount(),
                command.includeImage(),
                command.includeVideo(),
                command.textProvider(),
                command.textModel()
        ));
        AiModelSelection textModel = AiModelSelection.required(
                command.textProvider(), command.textModel(), "Text"
        );
        AiModelSelection imageModel = AiModelSelection.optional(
                command.includeImage(), command.imageProvider(), command.imageModel(), "image"
        );
        AiModelSelection videoModel = AiModelSelection.optional(
                command.includeVideo(), command.videoProvider(), command.videoModel(), "video"
        );

        requireCapability(textModel.provider(), AiCapability.TEXT);
        if (imageModel != null) {
            requireCapability(imageModel.provider(), AiCapability.IMAGE);
        }
        if (videoModel != null) {
            requireCapability(videoModel.provider(), AiCapability.VIDEO);
        }

        GenerationBatch batch = GenerationBatch.create(
                command.title(), command.platform(), command.contentType(), command.requestedCount(),
                textModel, imageModel, videoModel,
                command.generationStrategy(), sourceCount
        );
        links.forEach(batch::addLinkSource);

        List<String> storedKeys = new ArrayList<>();
        try {
            documents.forEach(document -> {
                StoredDocument stored = documentStorage.store(document.originalFilename(), document.content());
                storedKeys.add(stored.storageKey());
                batch.addDocumentSource(stored.storageKey());
            });
            GenerationBatch savedBatch = generationBatchRepository.saveAndFlush(batch);
            log.info(
                    "Generation batch created batchId={} platform={} contentType={} requestedCount={} sourceCount={} strategy={} textProvider={} textModel={}",
                    savedBatch.id(),
                    savedBatch.platform(),
                    savedBatch.contentType(),
                    savedBatch.requestedCount(),
                    sourceCount,
                    savedBatch.generationStrategy(),
                    savedBatch.textProvider(),
                    savedBatch.textModel()
            );
            return savedBatch;
        } catch (RuntimeException exception) {
            storedKeys.forEach(documentStorage::delete);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public GenerationBatch get(UUID batchId) {
        return generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
    }

    @Transactional
    public GenerationBatch retry(UUID batchId) {
        OffsetDateTime retriedAt = OffsetDateTime.now();
        int claimed = generationBatchRepository.claimFailedForRetry(
                batchId,
                GenerationBatchStatus.FAILED,
                GenerationBatchStatus.IN_PROGRESS,
                retriedAt
        );
        if (claimed == 0) {
            GenerationBatch currentBatch = generationBatchRepository.findOneById(batchId)
                    .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
            throw new GenerationBatchRetryConflictException(batchId, currentBatch.status());
        }
        generationAttemptRepository.markStartedUnknownForBatch(
                batchId,
                GenerationAttemptStatus.STARTED,
                GenerationAttemptStatus.UNKNOWN,
                "Previous generation process ended before the provider result was persisted",
                retriedAt
        );
        GenerationBatch batch = generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
        log.info("Generation batch retry claimed batchId={} retryCount={}", batchId, batch.retryCount());
        return batch;
    }

    @Transactional
    public void markDispatchFailed(UUID batchId) {
        GenerationBatch batch = generationBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
        if (batch.status() == GenerationBatchStatus.IN_PROGRESS) {
            batch.markFailed("Generation job could not be queued");
            generationBatchRepository.saveAndFlush(batch);
            log.warn("Generation batch dispatch failed batchId={}", batchId);
        }
    }

    @Transactional(readOnly = true)
    public Page<GenerationBatch> list(
            GenerationBatchStatus status,
            Platform platform,
            ContentType contentType,
            Pageable pageable
    ) {
        return generationBatchRepository.findAllByFilters(status, platform, contentType, pageable);
    }

    private void requireCapability(String providerName, AiCapability capability) {
        if (!aiProviderFactory.supports(providerName, capability)) {
            throw new DomainException(
                    "AI provider %s does not support %s generation in the configured mode"
                            .formatted(providerName, capability)
            );
        }
    }
}
