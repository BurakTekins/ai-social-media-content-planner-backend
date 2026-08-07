package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderCapabilityResolver;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClientResolver;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.GeneratedMediaContentLoader;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.service.ContentService;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentOperationNotAllowedException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
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
class DraftRegenerationServiceImplTest {

    @Mock
    private ContentService contentService;

    @Mock
    private AiProviderClientResolver aiProviderClientResolver;

    @Mock
    private AiProviderCapabilityResolver aiProviderCapabilityResolver;

    @Mock
    private GeneratedMediaContentLoader generatedMediaContentLoader;

    @Mock
    private MediaStorage mediaStorage;

    @Mock
    private AiProviderClient aiProviderClient;

    private DraftRegenerationServiceImpl draftRegenerationService;

    @BeforeEach
    void setUp() {
        draftRegenerationService = new DraftRegenerationServiceImpl(
                contentService,
                aiProviderClientResolver,
                aiProviderCapabilityResolver,
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
        when(aiProviderCapabilityResolver.supports("openai", AiCapability.TEXT)).thenReturn(true);
        when(aiProviderClientResolver.resolve("openai")).thenReturn(aiProviderClient);
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
        when(aiProviderCapabilityResolver.supports("gemini", AiCapability.IMAGE)).thenReturn(true);
        when(aiProviderClientResolver.resolve("gemini")).thenReturn(aiProviderClient);
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
                aiProviderClientResolver,
                aiProviderCapabilityResolver,
                aiProviderClient,
                generatedMediaContentLoader,
                mediaStorage
        );
    }

    private static Content draft() {
        return Content.create(
                Platform.LINKEDIN,
                ContentType.POST,
                "Current draft",
                List.of("current"),
                null
        );
    }
}
