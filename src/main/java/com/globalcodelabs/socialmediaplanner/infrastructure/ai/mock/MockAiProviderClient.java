package com.globalcodelabs.socialmediaplanner.infrastructure.ai.mock;

import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockAiProviderClient implements AiProviderClient {

    private final ObjectMapper objectMapper;
    private final ApiCredentialResolver apiCredentialResolver;

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        apiCredentialResolver.resolveActive(CredentialType.AI_PROVIDER, request.provider());
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(request.provider());

        try {
            log.info("AI generation started mode=mock capability={} model={}",
                    request.capability(), request.model());
            if ("mock-failure".equalsIgnoreCase(request.model())) {
                throw new IllegalStateException("Mock AI provider failure requested");
            }
            String output = createMockOutput(request);
            long durationMs = elapsedMilliseconds(startedAt);
            log.info("AI generation completed mode=mock capability={} model={} durationMs={}",
                    request.capability(), request.model(), durationMs);
            return new AiGenerationResult(
                    request.capability(),
                    request.provider(),
                    request.model(),
                    null,
                    null,
                    output
            );
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private String createMockOutput(AiGenerationRequest request) {
        String fingerprint = Integer.toUnsignedString(request.prompt().hashCode(), 16);
        return switch (request.capability()) {
            case TEXT -> createTextOutput(request.provider(), fingerprint);
            case IMAGE -> "mock://" + request.provider() + "/image/" + fingerprint + ".png";
            case VIDEO -> "mock://" + request.provider() + "/video/" + fingerprint + ".mp4";
        };
    }

    private String createTextOutput(String provider, String fingerprint) {
        try {
            return objectMapper.writeValueAsString(new MockTextOutput(
                    "Mock " + provider + " generated content " + fingerprint,
                    new String[]{"#mock", "#ai", "#" + provider}
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not create mock AI response", exception);
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record MockTextOutput(String text, String[] hashtags) {
    }
}
