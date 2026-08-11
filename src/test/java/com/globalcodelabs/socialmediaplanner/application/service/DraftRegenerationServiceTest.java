package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import com.globalcodelabs.socialmediaplanner.application.service.ContentService;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentOperationNotAllowedException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftRegenerationServiceTest {

    @Mock
    private ContentService contentService;

    @Mock
    private AiProviderFactory aiProviderFactory;

    @Mock
    private MediaContentLoader generatedMediaContentLoader;

    @Mock
    private LocalMediaStorage mediaStorage;

    @Mock
    private AiProviderClient aiProviderClient;

    private DraftRegenerationService draftRegenerationService;

    @BeforeEach
    void setUp() {
        draftRegenerationService = new DraftRegenerationService(
                contentService,
                aiProviderFactory,
                generatedMediaContentLoader,
                mediaStorage,
                new ObjectMapper()
        );
    }

    @Test
    void regeneratesDraftTextAndHashtagsThroughSelectedProvider() {
        Content content = draft();
        UUID contentId = content.id();
        when(contentService.findById(contentId)).thenReturn(content);
        when(aiProviderFactory.supports("openai", AiCapability.TEXT)).thenReturn(true);
        when(aiProviderFactory.resolve("openai")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(argThat(request ->
                request.capability() == AiCapability.TEXT
                        && request.prompt().contains("Platform: LINKEDIN")
        ))).thenReturn(new AiGenerationResult(
                AiCapability.TEXT,
                "openai",
                "text-model",
                null,
                null,
                "{\"text\":\"New draft\",\"hashtags\":[\"ai\",\"content\"]}"
        ));
        when(contentService.applyGeneratedText(
                contentId,
                "New draft",
                List.of("ai", "content"),
                "openai",
                "text-model"
        )).thenReturn(content);

        Content result = draftRegenerationService.regenerateText(
                contentId,
                "openai",
                "text-model"
        );

        assertThat(result).isSameAs(content);
        verify(contentService).applyGeneratedText(
                contentId,
                "New draft",
                List.of("ai", "content"),
                "openai",
                "text-model"
        );
    }

    @Test
    void replacesDraftMediaWithoutDownloadingMockOutput() {
        Content content = draft();
        UUID contentId = content.id();
        when(contentService.findById(contentId)).thenReturn(content);
        when(aiProviderFactory.supports("gemini", AiCapability.IMAGE)).thenReturn(true);
        when(aiProviderFactory.resolve("gemini")).thenReturn(aiProviderClient);
        when(aiProviderClient.generate(argThat(request -> request.capability() == AiCapability.IMAGE)))
                .thenReturn(new AiGenerationResult(
                        AiCapability.IMAGE,
                        "gemini",
                        "image-model",
                        null,
                        null,
                        "mock://gemini/image/example.png"
                ));
        when(contentService.applyGeneratedMedia(
                eq(contentId),
                eq(MediaType.IMAGE),
                argThat(storageKey -> storageKey.startsWith("generated/gemini/image/")),
                eq("mock://gemini/image/example.png"),
                eq("gemini"),
                eq("image-model")
        )).thenReturn(content);

        Content result = draftRegenerationService.regenerateMedia(
                contentId,
                MediaType.IMAGE,
                "gemini",
                "image-model"
        );

        assertThat(result).isSameAs(content);
        verifyNoInteractions(generatedMediaContentLoader, mediaStorage);
    }

    @Test
    void rejectsScheduledContentBeforeResolvingProvider() {
        Content content = draft();
        content.schedule(OffsetDateTime.now().plusHours(1));
        when(contentService.findById(content.id())).thenReturn(content);

        assertThatThrownBy(() -> draftRegenerationService.regenerateText(
                content.id(),
                "openai",
                "text-model"
        )).isInstanceOf(ContentOperationNotAllowedException.class);

        verifyNoInteractions(
                aiProviderFactory,
                aiProviderClient,
                generatedMediaContentLoader,
                mediaStorage
        );
    }

    private static Content draft() {
        return Content.create(
                "Current draft title",
                Platform.LINKEDIN,
                ContentType.POST,
                "Current draft",
                List.of("current"),
                null
        );
    }
}
