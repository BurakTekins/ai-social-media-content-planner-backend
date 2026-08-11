package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.mock.MockSocialPlatformClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class SocialPlatformClientFactory {

    private final PublishingProperties properties;
    private final MockSocialPlatformClient mockClient;
    private final Map<Platform, SocialPlatformClient> realClients;

    public SocialPlatformClientFactory(
            PublishingProperties properties,
            MockSocialPlatformClient mockClient,
            List<SocialPlatformClient> clients
    ) {
        this.properties = properties;
        this.mockClient = mockClient;
        this.realClients = indexRealClients(clients, mockClient);
    }

    public SocialPlatformClient resolve(Platform platform) {
        Platform requiredPlatform = Objects.requireNonNull(platform, "Platform cannot be null");
        if (properties.mockModeEnabled()) {
            return mockClient;
        }

        SocialPlatformClient client = realClients.get(requiredPlatform);
        if (client == null) {
            throw new IllegalArgumentException(
                    "No real social platform client is configured for " + requiredPlatform
            );
        }
        return client;
    }

    private static Map<Platform, SocialPlatformClient> indexRealClients(
            List<SocialPlatformClient> clients,
            MockSocialPlatformClient mockClient
    ) {
        EnumMap<Platform, SocialPlatformClient> indexedClients = new EnumMap<>(Platform.class);
        clients.stream()
                .filter(client -> client != mockClient)
                .forEach(client -> registerSupportedPlatforms(indexedClients, client));
        return Map.copyOf(indexedClients);
    }

    private static void registerSupportedPlatforms(
            EnumMap<Platform, SocialPlatformClient> indexedClients,
            SocialPlatformClient client
    ) {
        for (Platform platform : Platform.values()) {
            if (!client.supports(platform)) {
                continue;
            }
            SocialPlatformClient existing = indexedClients.putIfAbsent(platform, client);
            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple real social platform clients support " + platform
                );
            }
        }
    }
}
