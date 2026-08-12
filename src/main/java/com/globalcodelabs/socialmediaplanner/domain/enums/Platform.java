package com.globalcodelabs.socialmediaplanner.domain.enums;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Platform {
    LINKEDIN("linkedin", "LinkedIn", List.of(ContentType.POST)),
    INSTAGRAM("instagram", "Instagram", List.of(ContentType.POST, ContentType.REEL)),
    TWITTER("twitter", "X", List.of(ContentType.TWEET));

    private final String providerName;
    private final String displayName;
    private final List<ContentType> supportedContentTypes;

    Platform(String providerName, String displayName, List<ContentType> supportedContentTypes) {
        this.providerName = providerName;
        this.displayName = displayName;
        this.supportedContentTypes = supportedContentTypes;
    }

    public String providerName() {
        return providerName;
    }

    public String displayName() {
        return displayName;
    }

    public List<ContentType> supportedContentTypes() {
        return supportedContentTypes;
    }

    public boolean supports(ContentType contentType) {
        return supportedContentTypes.contains(contentType);
    }

    public static Optional<Platform> findByProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        String normalized = providerName.trim().toLowerCase(Locale.ROOT);
        for (Platform platform : values()) {
            if (platform.providerName.equals(normalized)) {
                return Optional.of(platform);
            }
        }
        return Optional.empty();
    }
}
