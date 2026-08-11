package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiCredentialValidationResult;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCredentialValidationClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AiProviderProperties properties;
    private final AiRestClientFactory restClientFactory;

    public AiCredentialValidationResult validate(String providerName, String accessToken) {
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(providerName);
        try {
            log.info("AI credential validation started");
            switch (providerName) {
                case "openai", "deepseek", "qwen" -> validateBearerProvider(providerName, accessToken);
                case "anthropic" -> validateAnthropic(accessToken);
                case "gemini" -> validateGemini(accessToken);
                default -> throw new IllegalArgumentException("Unsupported AI provider: " + providerName);
            }
            log.info("AI credential validation completed durationMs={}", elapsedMilliseconds(startedAt));
            return AiCredentialValidationResult.succeeded();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 402 || status == 403) {
                log.warn("AI credential rejected httpStatus={} durationMs={}",
                        status, elapsedMilliseconds(startedAt));
                return AiCredentialValidationResult.rejected(
                        "Provider rejected the API credential (HTTP " + status + ")"
                );
            }
            log.error("AI credential validation unavailable httpStatus={} durationMs={}",
                    status, elapsedMilliseconds(startedAt), exception);
            throw new IllegalStateException(
                    "AI credential validation is temporarily unavailable (HTTP " + status + ")",
                    exception
            );
        } catch (RestClientException exception) {
            log.error("AI credential validation unavailable durationMs={}",
                    elapsedMilliseconds(startedAt), exception);
            throw new IllegalStateException("AI credential validation is temporarily unavailable", exception);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private void validateBearerProvider(String providerName, String accessToken) {
        String baseUrl = properties.requireBaseUrl(providerName);
        restClientFactory.forBaseUrl(baseUrl)
                .get()
                .uri(restClientFactory.endpoint(baseUrl, "models"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    private void validateAnthropic(String accessToken) {
        String baseUrl = properties.requireBaseUrl("anthropic");
        restClientFactory.forBaseUrl(baseUrl)
                .get()
                .uri(restClientFactory.endpoint(baseUrl, "v1/models?limit=1"))
                .header("x-api-key", accessToken)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .retrieve()
                .toBodilessEntity();
    }

    private void validateGemini(String accessToken) {
        String baseUrl = properties.requireBaseUrl("gemini");
        restClientFactory.forBaseUrl(baseUrl)
                .get()
                .uri(restClientFactory.endpoint(baseUrl, "models?pageSize=1"))
                .header("x-goog-api-key", accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
