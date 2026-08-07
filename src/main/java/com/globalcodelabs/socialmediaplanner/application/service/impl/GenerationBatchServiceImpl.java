package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.command.CreateGenerationBatchCommand;
import com.globalcodelabs.socialmediaplanner.application.command.UploadedDocument;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderCapabilityResolver;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.DocumentStorage;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredDocument;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBatchService;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchRetryConflictException;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import lombok.RequiredArgsConstructor;
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
public class GenerationBatchServiceImpl implements GenerationBatchService {

    private final GenerationBatchRepository generationBatchRepository;
    private final GenerationAttemptRepository generationAttemptRepository;
    private final DocumentStorage documentStorage;
    private final AiProviderCapabilityResolver aiProviderCapabilityResolver;
    private final GenerationBudgetPolicy generationBudgetPolicy;

    @Override
    @Transactional
    public GenerationBatch create(CreateGenerationBatchCommand command) {
        List<String> links = command.links() == null ? List.of() : command.links();
        List<UploadedDocument> documents = command.documents() == null ? List.of() : command.documents();
        if (links.isEmpty() && documents.isEmpty()) {
            throw new DomainException("At least one link or document is required");
        }
        generationBudgetPolicy.validate(new GenerationBudgetPolicy.Request(
                command.requestedCount(),
                command.includeImage(),
                command.includeVideo(),
                command.textProvider(),
                command.textModel()
        ));
        requireCapability(command.textProvider(), AiCapability.TEXT);
        if (command.includeImage()) {
            requireCapability(command.imageProvider(), AiCapability.IMAGE);
        }
        if (command.includeVideo()) {
            requireCapability(command.videoProvider(), AiCapability.VIDEO);
        }

        GenerationBatch batch = GenerationBatch.create(
                command.platform(), command.contentType(), command.requestedCount(),
                command.includeImage(), command.includeVideo(),
                command.textProvider(), command.textModel(),
                command.imageProvider(), command.imageModel(),
                command.videoProvider(), command.videoModel()
        );
        links.forEach(batch::addLinkSource);

        List<String> storedKeys = new ArrayList<>();
        try {
            documents.forEach(document -> {
                StoredDocument stored = documentStorage.store(document.originalFilename(), document.content());
                storedKeys.add(stored.storageKey());
                batch.addDocumentSource(stored.storageKey());
            });
            return generationBatchRepository.saveAndFlush(batch);
        } catch (RuntimeException exception) {
            storedKeys.forEach(documentStorage::delete);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationBatch get(UUID batchId) {
        return generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
    }

    @Override
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
        return generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
    }

    @Override
    @Transactional
    public void markDispatchFailed(UUID batchId) {
        GenerationBatch batch = generationBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new GenerationBatchNotFoundException(batchId));
        if (batch.status() == GenerationBatchStatus.IN_PROGRESS) {
            batch.markFailed("Generation job could not be queued");
            generationBatchRepository.saveAndFlush(batch);
        }
    }

    @Override
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
        if (!aiProviderCapabilityResolver.supports(providerName, capability)) {
            throw new DomainException(
                    "AI provider %s does not support %s generation in the configured mode"
                            .formatted(providerName, capability)
            );
        }
    }
}
