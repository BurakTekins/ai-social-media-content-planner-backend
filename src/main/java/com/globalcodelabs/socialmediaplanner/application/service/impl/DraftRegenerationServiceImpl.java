package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderCapabilityResolver;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClientResolver;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.GeneratedMediaContentLoader;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.application.service.ContentService;
import com.globalcodelabs.socialmediaplanner.application.service.DraftRegenerationService;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftRegenerationServiceImpl implements DraftRegenerationService {

    private final ContentService contentService;
    private final AiProviderClientResolver aiProviderClientResolver;
    private final AiProviderCapabilityResolver aiProviderCapabilityResolver;
    private final GeneratedMediaContentLoader generatedMediaContentLoader;
    private final MediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    @Override
    public Content regenerateText(UUID contentId, String provider, String model) {
        Content content = loadRegeneratable(contentId);
        requireCapability(provider, AiCapability.TEXT);
        AiGenerationResult result = generate(new AiGenerationRequest(
                provider,
                AiCapability.TEXT,
                buildTextPrompt(content),
                model
        ));
        GeneratedText generatedText = parseGeneratedText(result);
        return contentService.applyGeneratedText(
                contentId,
                generatedText.text(),
                generatedText.hashtags(),
                result.provider(),
                result.model()
        );
    }

    @Override
    public Content regenerateMedia(
            UUID contentId,
            MediaType mediaType,
            String provider,
            String model
    ) {
        Content content = loadRegeneratable(contentId);
        AiCapability capability = mediaType == MediaType.IMAGE
                ? AiCapability.IMAGE
                : AiCapability.VIDEO;
        requireCapability(provider, capability);
        AiGenerationResult result = generate(new AiGenerationRequest(
                provider,
                capability,
                buildMediaPrompt(content, mediaType),
                model
        ));
        String output = requireOutput(result);
        if (output.startsWith("mock://")) {
            String storageKey = "generated/%s/%s/%s".formatted(
                    normalizeProvider(result.provider()),
                    mediaType.name().toLowerCase(Locale.ROOT),
                    UUID.randomUUID()
            );
            return contentService.applyGeneratedMedia(
                    contentId,
                    mediaType,
                    storageKey,
                    output,
                    result.provider(),
                    result.model()
            );
        }

        StoredMediaContent generatedMedia = generatedMediaContentLoader.loadGenerated(mediaType, output);
        StoredMedia storedMedia = mediaStorage.store(
                mediaType,
                generatedMedia.contentType(),
                generatedMedia.bytes()
        );
        try {
            return contentService.applyGeneratedMedia(
                    contentId,
                    mediaType,
                    storedMedia.storageKey(),
                    null,
                    result.provider(),
                    result.model()
            );
        } catch (RuntimeException exception) {
            deleteNewMedia(storedMedia.storageKey());
            throw exception;
        }
    }

    private Content loadRegeneratable(UUID contentId) {
        Content content = contentService.findById(contentId);
        content.ensureRegeneratable();
        return content;
    }

    private AiGenerationResult generate(AiGenerationRequest request) {
        AiGenerationResult result = aiProviderClientResolver.resolve(request.provider())
                .generate(request);
        if (result == null || result.capability() != request.capability()) {
            throw new AiProviderResponseException(
                    "AI provider returned an invalid generation capability",
                    result == null ? null : result.providerResponseId(),
                    result == null ? null : result.providerRequestId()
            );
        }
        return result;
    }

    private GeneratedText parseGeneratedText(AiGenerationResult result) {
        try {
            GeneratedText generatedText = objectMapper.readValue(
                    requireOutput(result),
                    GeneratedText.class
            );
            if (generatedText.text() == null || generatedText.text().isBlank()) {
                throw new AiProviderResponseException(
                        "AI response text cannot be blank",
                        result.providerResponseId(),
                        result.providerRequestId()
                );
            }
            return new GeneratedText(
                    generatedText.text(),
                    generatedText.hashtags() == null ? List.of() : generatedText.hashtags()
            );
        } catch (JsonProcessingException exception) {
            throw new AiProviderResponseException(
                    "AI response is not valid content JSON",
                    result.providerResponseId(),
                    result.providerRequestId(),
                    exception
            );
        }
    }

    private void requireCapability(String provider, AiCapability capability) {
        if (!aiProviderCapabilityResolver.supports(provider, capability)) {
            throw new DomainException(
                    "AI provider %s does not support %s generation in the configured mode"
                            .formatted(provider, capability)
            );
        }
    }

    private String buildTextPrompt(Content content) {
        return """
                Regenerate the following draft as a distinct alternative while preserving its subject.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Do not wrap the JSON in Markdown or add explanations.
                Platform: %s
                Content type: %s
                Platform requirements:
                %s
                Current text:
                %s
                Current hashtags:
                %s
                """.formatted(
                content.platform(),
                content.contentType(),
                platformRequirements(content.platform(), content.contentType()),
                content.text(),
                String.join(" ", content.hashtags())
        );
    }

    private String buildMediaPrompt(Content content, MediaType mediaType) {
        return """
                Generate a new %s for this social media draft.
                The media must match the subject and tone of the draft.
                Platform: %s
                Content type: %s
                Draft text:
                %s
                Draft version: %s
                """.formatted(
                mediaType,
                content.platform(),
                content.contentType(),
                content.text(),
                content.updatedAt()
        );
    }

    private static String platformRequirements(Platform platform, ContentType contentType) {
        return switch (platform) {
            case LINKEDIN -> "Use a professional, informative tone suitable for a LinkedIn post.";
            case INSTAGRAM -> contentType == ContentType.REEL
                    ? "Write a short, high-impact caption that complements video-first Reel content."
                    : "Write a concise, engaging caption suitable for a visual Instagram feed post.";
            case TWITTER -> "Keep the final text and hashtags together within 280 characters. "
                    + "Use a concise Tweet format.";
        };
    }

    private static String requireOutput(AiGenerationResult result) {
        if (result.output() == null || result.output().isBlank()) {
            throw new AiProviderResponseException(
                    "AI provider returned an empty output",
                    result.providerResponseId(),
                    result.providerRequestId()
            );
        }
        return result.output().trim();
    }

    private static String normalizeProvider(String provider) {
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private void deleteNewMedia(String storageKey) {
        try {
            mediaStorage.delete(storageKey);
        } catch (RuntimeException exception) {
            log.warn("Could not delete unused regenerated media storageKey={}", storageKey, exception);
        }
    }

    private record GeneratedText(String text, List<String> hashtags) {
    }
}
