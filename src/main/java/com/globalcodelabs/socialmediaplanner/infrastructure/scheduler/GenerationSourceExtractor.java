package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSource;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.extraction.DefaultSourceTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class GenerationSourceExtractor {

    private final GenerationBatchRepository generationBatchRepository;
    private final DefaultSourceTextExtractor sourceTextExtractor;

    List<ExtractedSource> extract(GenerationBatch batch) {
        List<ExtractedSource> extractedSources = new ArrayList<>();
        List<ContentSource> orderedSources = batch.sources().stream()
                .sorted(Comparator.comparing(ContentSource::createdAt).thenComparing(ContentSource::id))
                .toList();
        for (int sourceIndex = 0; sourceIndex < orderedSources.size(); sourceIndex++) {
            ContentSource source = orderedSources.get(sourceIndex);
            if (source.status() == ContentSourceStatus.COMPLETED) {
                extractedSources.add(toExtractedSource(source, sourceIndex));
                continue;
            }
            try {
                source.startProcessing();
                generationBatchRepository.save(batch);
                String extractedText = sourceTextExtractor.extract(source.sourceType(), source.sourceValue());
                source.complete(extractedText);
                extractedSources.add(toExtractedSource(source, sourceIndex));
                generationBatchRepository.save(batch);
            } catch (Exception exception) {
                if (source.status() == ContentSourceStatus.PENDING
                        || source.status() == ContentSourceStatus.PROCESSING) {
                    source.fail(GenerationAttemptSupport.errorMessage(exception));
                    generationBatchRepository.save(batch);
                }
                log.warn("Source extraction failed sourceId={} errorType={} error={}",
                        source.id(), exception.getClass().getSimpleName(),
                        GenerationAttemptSupport.errorMessage(exception));
            }
        }
        return List.copyOf(extractedSources);
    }

    private static ExtractedSource toExtractedSource(ContentSource source, int sourceIndex) {
        return new ExtractedSource(
                sourceIndex + 1,
                source.id(),
                source.sourceType().name(),
                source.extractedText()
        );
    }
}
