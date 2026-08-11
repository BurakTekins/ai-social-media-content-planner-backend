package com.globalcodelabs.socialmediaplanner.domain.policy;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.twitter.twittertext.TwitterTextParseResults;
import com.twitter.twittertext.TwitterTextParser;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ContentPolicy {

    private static final int TWITTER_MAX_CHARACTERS = 280;
    private static final int MAX_TITLE_LENGTH = 255;

    private ContentPolicy() {
    }

    public static void validatePlatformContentType(Platform platform, ContentType contentType) {
        Objects.requireNonNull(platform, "Platform cannot be null");
        Objects.requireNonNull(contentType, "Content type cannot be null");
        if (!platform.supports(contentType)) {
            throw new DomainException(
                    "Content type %s is not supported for platform %s".formatted(contentType, platform)
            );
        }
    }

    public static void validatePlatformMediaSelection(
            Platform platform,
            ContentType contentType,
            boolean includeImage,
            boolean includeVideo
    ) {
        validatePlatformContentType(platform, contentType);
        switch (platform) {
            case INSTAGRAM -> validateInstagramMediaSelection(contentType, includeImage, includeVideo);
            case LINKEDIN -> validateSingleMediaTypeSelection("LinkedIn", includeImage, includeVideo);
            case TWITTER -> validateSingleMediaTypeSelection("X (Twitter)", includeImage, includeVideo);
        }
    }

    private static void validateInstagramMediaSelection(
            ContentType contentType,
            boolean includeImage,
            boolean includeVideo
    ) {
        if (contentType == ContentType.POST && includeVideo) {
            throw new DomainException("Instagram POST does not support video; use Instagram REEL for video content");
        }
        if (contentType == ContentType.POST && !includeImage) {
            throw new DomainException("Instagram POST requires an image");
        }
        if (contentType == ContentType.REEL && !includeVideo) {
            throw new DomainException("Instagram REEL requires a video; an image may be included as its cover");
        }
    }

    private static void validateSingleMediaTypeSelection(
            String platformName,
            boolean includeImage,
            boolean includeVideo
    ) {
        if (includeImage && includeVideo) {
            throw new DomainException(
                    platformName + " content cannot include both an image and a video; choose only one media type"
            );
        }
    }

    public static String normalizeTitle(String title) {
        String normalized = DomainValidation.requireText(title, "Content title cannot be blank");
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new DomainException("Content title cannot exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return normalized;
    }

    public static String[] sanitizeHashtags(List<String> hashtags) {
        if (hashtags == null) {
            return new String[0];
        }
        if (hashtags.stream().anyMatch(hashtag -> hashtag == null || hashtag.isBlank())) {
            throw new DomainException("Hashtags cannot contain blank values");
        }
        return hashtags.stream().map(String::trim).toArray(String[]::new);
    }

    public static void validateContent(Platform platform, String text, String[] hashtags) {
        validatePublicationText(platform, text, hashtags);
    }

    public static void validateScheduledAt(OffsetDateTime scheduledAt) {
        if (scheduledAt == null || !scheduledAt.isAfter(OffsetDateTime.now())) {
            throw new DomainException("Scheduled time must be in the future");
        }
    }

    private static void validatePublicationText(Platform platform, String text, String[] hashtags) {
        if (platform != Platform.TWITTER) {
            return;
        }
        String formattedText = formatPublicationText(text, hashtags);
        TwitterTextParseResults parseResults = TwitterTextParser.parseTweet(formattedText);
        if (parseResults.weightedLength > TWITTER_MAX_CHARACTERS) {
            throw new DomainException(
                    "Twitter content including hashtags has weighted length "
                            + parseResults.weightedLength + " but cannot exceed "
                            + TWITTER_MAX_CHARACTERS
            );
        }
        if (!parseResults.isValid) {
            throw new DomainException("Twitter content contains characters that X does not accept");
        }
    }

    private static String formatPublicationText(String text, String[] hashtags) {
        if (hashtags.length == 0) {
            return text;
        }
        String formattedHashtags = Arrays.stream(hashtags)
                .map(hashtag -> hashtag.startsWith("#") ? hashtag : "#" + hashtag)
                .collect(Collectors.joining(" "));
        return text + "\n\n" + formattedHashtags;
    }
}
