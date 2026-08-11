package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftRegenerationService {

    private final ContentService contentService;
    private final AiProviderFactory aiProviderFactory;
    private final MediaContentLoader generatedMediaContentLoader;
    private final LocalMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

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
        AiGenerationResult result = aiProviderFactory.resolve(request.provider())
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
            if (generatedText.hashtags() == null || generatedText.hashtags().isEmpty()) {
                throw new AiProviderResponseException(
                        "AI response must include at least one hashtag",
                        result.providerResponseId(),
                        result.providerRequestId()
                );
            }
            return new GeneratedText(
                    generatedText.text(),
                    generatedText.hashtags()
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
        if (!aiProviderFactory.supports(provider, capability)) {
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
                Preserve the natural language used by the current draft.
                Generate at least one hashtag relevant to both the draft subject and the selected platform.
                Do not add unrelated or generic trending hashtags. Return hashtags in descending order of relevance.
                Preserve every condition attached to an offer, discount, benefit, eligibility rule, prerequisite,
                limitation, or duration in the current draft. Never present a conditional claim as unconditional;
                if its complete conditions cannot be preserved, omit the whole claim.
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
                The media must directly represent the subject, message, and tone of the draft.
                Do not introduce unrelated products, claims, people, brands, or events.
                Platform: %s
                Content type: %s
                Platform and format requirements: %s
                Draft text:
                %s
                Draft hashtags:
                %s
                Draft version: %s
                """.formatted(
                mediaType,
                content.platform(),
                content.contentType(),
                mediaRequirements(content.platform(), content.contentType()),
                content.text(),
                String.join(" ", content.hashtags()),
                content.updatedAt()
        );
    }

    private static String platformRequirements(Platform platform, ContentType contentType) {
        return switch (platform) {
            case LINKEDIN -> "Use a professional, informative tone and a more detailed text format suitable for a "
                    + "LinkedIn post. Keep the structure readable and use professional, topic-relevant hashtags.";
            case INSTAGRAM -> contentType == ContentType.REEL
                    ? "Write a short, high-impact caption that complements visual and video-first Reel content. "
                            + "Use concise, topic-relevant Instagram hashtags."
                    : "Write a short, engaging caption suitable for a visual Instagram feed post. "
                            + "Use topic-relevant Instagram hashtags.";
            case TWITTER -> "Keep the final text and hashtags together within the X weighted limit of 280. "
                    + "Use a concise Tweet format and at most 3 short, topic-relevant hashtags.";
        };
    }

    private static String mediaRequirements(Platform platform, ContentType contentType) {
        return switch (platform) {
            case LINKEDIN -> "Use a professional visual style appropriate for a LinkedIn post.";
            case INSTAGRAM -> contentType == ContentType.REEL
                    ? "Use a striking, short-form social media style appropriate for an Instagram Reel."
                    : "Use an engaging, feed-ready visual style appropriate for an Instagram post.";
            case TWITTER -> "Use a concise supporting visual style appropriate for an X post.";
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
            log.warn("Could not delete unused regenerated media errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private record GeneratedText(String text, List<String> hashtags) {
    }
}
