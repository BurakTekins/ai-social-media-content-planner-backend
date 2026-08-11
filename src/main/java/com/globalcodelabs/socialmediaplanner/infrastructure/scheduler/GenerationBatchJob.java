package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.GeneratedContentFinalizer;
import com.globalcodelabs.socialmediaplanner.common.exception.ConcurrentGenerationException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationBatchJob {

    private static final String JOB_NAME = "batch-generation";
    private final GenerationBatchRepository generationBatchRepository;
    private final ContentRepository contentRepository;
    private final GeneratedContentFinalizer generatedContentFinalizer;
    private final GenerationSourceExtractor sourceExtractor;
    private final GeneratedContentGenerator contentGenerator;

    @Async("generationTaskExecutor")
    public void start(UUID batchId) {
        execute(batchId, null);
    }

    @Async("generationTaskExecutor")
    public void start(UUID batchId, int generationIndex) {
        execute(batchId, generationIndex);
    }

    private void execute(UUID batchId, Integer targetGenerationIndex) {
        long startedAt = System.nanoTime();
        MdcUtil.putCorrelationId("job-" + JOB_NAME + "-" + UUID.randomUUID());
        MdcUtil.putJobName(JOB_NAME);
        MdcUtil.putBatchId(batchId.toString());

        try {
            log.info("Generation batch job started targetGenerationIndex={}", targetGenerationIndex);
            process(batchId, targetGenerationIndex);
            if (targetGenerationIndex != null) {
                markBatchFailedIfTargetedRunLeftMissingSlots(batchId);
            }
            log.info("Generation batch job completed durationMs={}", elapsedMilliseconds(startedAt));
        } catch (ConcurrentGenerationException exception) {
            log.info("Duplicate generation batch job stopped without another provider call");
        } catch (Exception exception) {
            markBatchFailed(batchId, exception);
            log.error("Generation batch job failed durationMs={} errorType={} error={}",
                    elapsedMilliseconds(startedAt), exception.getClass().getSimpleName(),
                    GenerationAttemptSupport.errorMessage(exception), exception);
        } finally {
            MdcUtil.clear();
        }
    }

    private void process(UUID batchId, Integer targetGenerationIndex) {
        Set<Integer> completedIndexes = new HashSet<>(
                contentRepository.findGenerationIndexesByBatchId(batchId)
        );
        generatedContentFinalizer.reconcileCompletedCount(batchId, completedIndexes.size());

        GenerationBatch batch = loadBatch(batchId);
        if (batch.status() == GenerationBatchStatus.COMPLETED) {
            return;
        }
        if (targetGenerationIndex != null
                && (targetGenerationIndex <= 0 || targetGenerationIndex > batch.requestedCount())) {
            throw new IllegalArgumentException("Target generation index is outside the batch range");
        }
        List<ExtractedSource> extractedSources = sourceExtractor.extract(batch);
        if (extractedSources.isEmpty()) {
            throw new IllegalStateException("No source could be extracted for generation batch");
        }
        if (batch.generationStrategy() == GenerationStrategy.SOURCE_BASED
                && extractedSources.size() < batch.requestedCount()) {
            log.warn(
                    "Source-based generation will reuse extracted sources round-robin extractedSourceCount={} requestedCount={}",
                    extractedSources.size(),
                    batch.requestedCount()
            );
        }

        for (int index = 1; index <= batch.requestedCount(); index++) {
            if (targetGenerationIndex != null && index != targetGenerationIndex) {
                continue;
            }
            if (completedIndexes.contains(index)) {
                continue;
            }
            Content content = contentGenerator.generate(batch, extractedSources, index);
            boolean stored = generatedContentFinalizer.storeGeneratedContent(batch.id(), index, content);
            if (!stored) {
                completedIndexes.add(index);
                continue;
            }
            batch = loadBatch(batchId);
            completedIndexes.add(index);
            log.info("Content generated completedCount={} requestedCount={}",
                    batch.completedCount(), batch.requestedCount());
        }
    }

    private GenerationBatch loadBatch(UUID batchId) {
        return generationBatchRepository.findOneById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Generation batch not found: " + batchId));
    }

    private void markBatchFailed(UUID batchId, Exception failure) {
        String failureMessage = GenerationAttemptSupport.errorMessage(failure);
        generationBatchRepository.findOneById(batchId).ifPresent(batch -> {
            if (batch.status() == GenerationBatchStatus.IN_PROGRESS) {
                batch.markFailed(failureMessage);
                generationBatchRepository.save(batch);
            }
        });
    }

    private void markBatchFailedIfTargetedRunLeftMissingSlots(UUID batchId) {
        generationBatchRepository.findOneById(batchId).ifPresent(batch -> {
            if (batch.status() == GenerationBatchStatus.IN_PROGRESS) {
                batch.markFailed("Remaining generation slots require separate retry or consent");
                generationBatchRepository.save(batch);
            }
        });
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
