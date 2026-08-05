package com.globalcodelabs.socialmediaplanner.application.port.out.publishing;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public record PublishContentRequest(
        UUID contentId,
        Platform platform,
        ContentType contentType,
        String text,
        List<String> hashtags,
        List<PublishMedia> media,
        PlatformCredential credential
) {
    public PublishContentRequest {
        Objects.requireNonNull(contentId, "Content id cannot be null");
        Objects.requireNonNull(platform, "Platform cannot be null");
        Objects.requireNonNull(contentType, "Content type cannot be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Content text cannot be blank");
        }
        text = text.trim();
        if (hashtags == null) {
            hashtags = List.of();
        } else {
            if (hashtags.stream().anyMatch(hashtag -> hashtag == null || hashtag.isBlank())) {
                throw new IllegalArgumentException("Hashtags cannot contain blank values");
            }
            hashtags = hashtags.stream().map(String::trim).toList();
        }
        media = media == null ? List.of() : List.copyOf(media);
        Objects.requireNonNull(credential, "Platform credential cannot be null");
    }

    public String formattedText() {
        if (hashtags.isEmpty()) {
            return text;
        }
        String formattedHashtags = hashtags.stream()
                .map(hashtag -> hashtag.startsWith("#") ? hashtag : "#" + hashtag)
                .collect(Collectors.joining(" "));
        return text + System.lineSeparator() + System.lineSeparator() + formattedHashtags;
    }
}
