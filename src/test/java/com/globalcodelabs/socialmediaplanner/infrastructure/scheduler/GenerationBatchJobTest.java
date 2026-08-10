package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.port.out.extraction.SourceTextExtractor;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.service.impl.GeneratedContentFinalizer;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSource;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class GenerationBatchJobTest {

    @Mock
    private GenerationBatchRepository generationBatchRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private GenerationAttemptRepository generationAttemptRepository;

    @Mock
    private GeneratedContentFinalizer generatedContentFinalizer;

    @Mock
    private SourceTextExtractor sourceTextExtractor;

    @Mock
    private AiProviderFactory aiProviderFactory;

    @Mock
    private MediaContentLoader mediaContentLoader;

    @Mock
    private MediaStorage mediaStorage;

    @Mock
    private AiProviderClient aiProviderClient;

    private GenerationBatchJob job;

    @BeforeEach
    void setUp() {
        job = new GenerationBatchJob(
                generationBatchRepository,
                contentRepository,
                generationAttemptRepository,
                generatedContentFinalizer,
                sourceTextExtractor,
                aiProviderFactory,
                mediaContentLoader,
                mediaStorage,
                new ObjectMapper()
        );
    }

    @Test
    void generatesOnlyMissingSlotAndReusesCompletedSource() {
        GenerationBatch batch = batchWithCompletedSource(2);
        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of(1));
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 1)).thenAnswer(invocation -> {
            batch.reconcileCompletedCount(1);
            return batch;
        });
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(2), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.empty());
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(aiProviderFactory.resolve("mock")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(any())).thenReturn(textResult());
        when(generatedContentFinalizer.storeGeneratedContent(eq(batch.id()), eq(2), any()))
                .thenAnswer(invocation -> {
                    batch.recordCompletedContent();
                    return true;
                });

        job.start(batch.id());

        ArgumentCaptor<AiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(aiProviderClient).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().prompt()).contains("Content number: 2 of 2");
        verify(sourceTextExtractor, never()).extract(any(), anyString());

        InOrder persistenceOrder = inOrder(generationAttemptRepository, aiProviderClient, generatedContentFinalizer);
        persistenceOrder.verify(generationAttemptRepository).saveAndFlush(any(GenerationAttempt.class));
        persistenceOrder.verify(aiProviderClient).generate(any());
        persistenceOrder.verify(generationAttemptRepository).saveAndFlush(any(GenerationAttempt.class));
        persistenceOrder.verify(generatedContentFinalizer).storeGeneratedContent(eq(batch.id()), eq(2), any());
    }

    @Test
    void reusesPersistedSuccessfulAttemptWithoutCallingProviderAgain() {
        GenerationBatch batch = batchWithCompletedSource(1);
        batch.markFailed("content persistence failed");
        batch.retry();
        GenerationAttempt reusable = GenerationAttempt.start(
                batch.id(), 1, AiCapability.TEXT, "mock", "text-model", "a".repeat(64)
        );
        AiGenerationResult result = textResult();
        reusable.succeed(
                result.capability(), result.provider(), result.model(),
                result.providerResponseId(), result.providerRequestId(), result.output()
        );

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.of(reusable));
        when(generatedContentFinalizer.storeGeneratedContent(eq(batch.id()), eq(1), any()))
                .thenAnswer(invocation -> {
                    batch.recordCompletedContent();
                    return true;
                });

        job.start(batch.id());

        verifyNoInteractions(aiProviderFactory, aiProviderClient);
        verify(generationAttemptRepository, never()).saveAndFlush(any());
        verify(sourceTextExtractor, never()).extract(any(), anyString());
        verify(generatedContentFinalizer).storeGeneratedContent(eq(batch.id()), eq(1), any());
    }

    @Test
    void ordersSourcesBeforeBuildingPrompt() {
        GenerationBatch batch = GenerationBatch.create(
                Platform.LINKEDIN,
                ContentType.POST,
                1,
                false,
                false,
                "mock",
                "text-model",
                null,
                null,
                null,
                null,
                null,
                2
        );
        batch.addLinkSource("https://example.com/first");
        batch.addLinkSource("https://example.com/second");
        ContentSource first = batch.sources().get(0);
        setField(first, "createdAt", OffsetDateTime.parse("2026-08-05T10:00:00Z"));
        first.startProcessing();
        first.complete("First source text");
        ContentSource second = batch.sources().get(1);
        setField(second, "createdAt", OffsetDateTime.parse("2026-08-05T10:00:01Z"));
        second.startProcessing();
        second.complete("Second source text");

        GenerationBatch batchWithReorderedSources = spy(batch);
        when(batchWithReorderedSources.sources()).thenReturn(List.of(second, first));
        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batchWithReorderedSources);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batchWithReorderedSources));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.empty());
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(aiProviderFactory.resolve("mock")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(any())).thenReturn(textResult());
        when(generatedContentFinalizer.storeGeneratedContent(eq(batch.id()), eq(1), any())).thenReturn(true);

        job.start(batch.id());

        ArgumentCaptor<AiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(aiProviderClient).generate(requestCaptor.capture());
        String prompt = requestCaptor.getValue().prompt();
        assertThat(prompt.indexOf("First source text")).isLessThan(prompt.indexOf("Second source text"));
    }

    @Test
    void recordsAmbiguousNetworkFailureAndNeverRetriesAutomatically() {
        GenerationBatch batch = batchWithCompletedSource(1);
        AtomicReference<GenerationAttempt> savedAttempt = new AtomicReference<>();

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.empty());
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenAnswer(invocation -> {
                    GenerationAttempt attempt = invocation.getArgument(0);
                    savedAttempt.set(attempt);
                    return attempt;
                });
        when(aiProviderFactory.resolve("mock")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(any())).thenThrow(new IllegalStateException(
                "Provider request failed at https://example.com/result?token=secret Bearer topsecret",
                new ResourceAccessException("Connection timed out")
        ));

        job.start(batch.id());

        assertThat(savedAttempt.get().status().name()).isEqualTo("UNKNOWN");
        assertThat(batch.status().name()).isEqualTo("FAILED");
        assertThat(batch.lastError()).doesNotContain("secret", "topsecret");
        assertThat(batch.lastError()).contains("[REDACTED]");
        verify(aiProviderClient, times(1)).generate(any());
        verify(generatedContentFinalizer, never()).storeGeneratedContent(any(), anyInt(), any());
    }

    @Test
    void recordsKnownRejectedProviderResponseWithBothIds() {
        GenerationBatch batch = batchWithCompletedSource(1);
        AtomicReference<GenerationAttempt> savedAttempt = new AtomicReference<>();

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.empty());
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenAnswer(invocation -> {
                    GenerationAttempt attempt = invocation.getArgument(0);
                    savedAttempt.set(attempt);
                    return attempt;
                });
        when(aiProviderFactory.resolve("mock")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(any())).thenThrow(new AiProviderResponseException(
                "Provider returned an empty text response",
                "response-123",
                "request-456"
        ));

        job.start(batch.id());

        assertThat(savedAttempt.get().status()).isEqualTo(GenerationAttemptStatus.FAILED);
        assertThat(savedAttempt.get().providerResponseId()).isEqualTo("response-123");
        assertThat(savedAttempt.get().providerRequestId()).isEqualTo("request-456");
        assertThat(batch.status().name()).isEqualTo("FAILED");
        verify(aiProviderClient, times(1)).generate(any());
        verify(generatedContentFinalizer, never()).storeGeneratedContent(any(), anyInt(), any());
    }

    @Test
    void duplicateWorkerStopsBeforeASecondProviderCall() {
        GenerationBatch batch = batchWithCompletedSource(1);

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(Optional.empty());
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active attempt"));
        when(generationAttemptRepository
                .existsByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusIn(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(), any()
                )).thenReturn(true);

        job.start(batch.id());

        verifyNoInteractions(aiProviderFactory, aiProviderClient);
        verify(generatedContentFinalizer, never()).storeGeneratedContent(any(), anyInt(), any());
        assertThat(batch.status().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void concurrentSuccessfulAttemptFromSameRetryStopsSecondWorker() {
        GenerationBatch batch = batchWithCompletedSource(1);
        GenerationAttempt concurrentSuccess = succeededAttempt(
                batch, AiCapability.TEXT, "text-model", textResult().output()
        );

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), eq(AiCapability.TEXT), anyString(),
                        eq(GenerationAttemptStatus.SUCCEEDED)
                )).thenReturn(Optional.empty(), Optional.of(concurrentSuccess));
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active attempt"));
        job.start(batch.id());

        verifyNoInteractions(aiProviderFactory, aiProviderClient);
        verify(generationAttemptRepository, never())
                .existsByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusIn(
                        any(), anyInt(), any(), anyString(), any()
                );
        verify(generatedContentFinalizer, never()).storeGeneratedContent(any(), anyInt(), any());
        assertThat(batch.status().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void invalidatesUnusableMediaAfterManualRetryFailsAgain() {
        GenerationBatch batch = batchWithCompletedSourceAndImage();
        GenerationAttempt textAttempt = succeededAttempt(
                batch, AiCapability.TEXT, "text-model", textResult().output()
        );
        GenerationAttempt imageAttempt = succeededAttempt(
                batch, AiCapability.IMAGE, "image-model", "https://example.com/expired-image.png"
        );

        when(contentRepository.findGenerationIndexesByBatchId(batch.id())).thenReturn(List.of());
        when(generatedContentFinalizer.reconcileCompletedCount(batch.id(), 0)).thenReturn(batch);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        when(generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        eq(batch.id()), eq(1), any(), anyString(), any()
                )).thenAnswer(invocation -> invocation.<AiCapability>getArgument(2) == AiCapability.TEXT
                        ? Optional.of(textAttempt)
                        : Optional.of(imageAttempt));
        when(generationAttemptRepository.saveAndFlush(any(GenerationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaContentLoader.load(MediaType.IMAGE, imageAttempt.output()))
                .thenThrow(new IllegalStateException("Media URL returned HTTP status 403"));

        job.start(batch.id());

        assertThat(imageAttempt.status()).isEqualTo(GenerationAttemptStatus.INVALID);
        assertThat(batch.status().name()).isEqualTo("FAILED");
        verifyNoInteractions(aiProviderFactory, aiProviderClient, mediaStorage);
        verify(generatedContentFinalizer, never()).storeGeneratedContent(any(), anyInt(), any());
    }

    private static GenerationBatch batchWithCompletedSource(int requestedCount) {
        GenerationBatch batch = GenerationBatch.create(
                Platform.LINKEDIN,
                ContentType.POST,
                requestedCount,
                false,
                false,
                "mock",
                "text-model",
                null,
                null,
                null,
                null,
                null,
                1
        );
        batch.addLinkSource("https://example.com/source");
        ContentSource source = batch.sources().getFirst();
        source.startProcessing();
        source.complete("Previously extracted source text");
        return batch;
    }

    private static GenerationBatch batchWithCompletedSourceAndImage() {
        GenerationBatch batch = GenerationBatch.create(
                Platform.INSTAGRAM,
                ContentType.POST,
                1,
                true,
                false,
                "mock",
                "text-model",
                "mock",
                "image-model",
                null,
                null,
                null,
                1
        );
        batch.addLinkSource("https://example.com/source");
        ContentSource source = batch.sources().getFirst();
        source.startProcessing();
        source.complete("Previously extracted source text");
        batch.markFailed("first media download failed");
        batch.retry();
        return batch;
    }

    private static GenerationAttempt succeededAttempt(
            GenerationBatch batch,
            AiCapability capability,
            String model,
            String output
    ) {
        GenerationAttempt attempt = GenerationAttempt.start(
                batch.id(), 1, capability, "mock", model, "a".repeat(64)
        );
        attempt.succeed(capability, "mock", model, null, null, output);
        return attempt;
    }

    private static AiGenerationResult textResult() {
        return new AiGenerationResult(
                AiCapability.TEXT,
                "mock",
                "text-model",
                "response-123",
                "request-456",
                "{\"text\":\"Generated text\",\"hashtags\":[\"#test\"]}"
        );
    }
}
