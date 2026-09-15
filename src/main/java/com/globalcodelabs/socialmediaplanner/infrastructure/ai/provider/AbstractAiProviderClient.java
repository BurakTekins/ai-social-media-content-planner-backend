package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

abstract class AbstractAiProviderClient implements AiProviderClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String providerName;
    private final String operation;
    private final String failureMessage;
    private final ApiCredentialService apiCredentialService;
    protected final AiProviderProperties properties;
    protected final AiRestClientFactory restClientFactory;
    protected final AiProviderResponseDecoder responseDecoder;

    protected AbstractAiProviderClient(
            String providerName,
            String operation,
            String failureMessage,
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder
    ) {
        this.providerName = providerName;
        this.operation = operation;
        this.failureMessage = failureMessage;
        this.apiCredentialService = apiCredentialService;
        this.properties = properties;
        this.restClientFactory = restClientFactory;
        this.responseDecoder = responseDecoder;
    }

    @Override
    public final String providerName() {
        return providerName;
    }

    @Override
    public final AiGenerationResult generate(AiGenerationRequest request) {
        if (!providerName.equals(request.provider())) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot handle " + request.provider());
        }
        validateRequest(request);

        ResolvedApiCredential credential = apiCredentialService.resolveActive(
                CredentialType.AI_PROVIDER, providerName
        );
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(providerName);
        try {
            log.info("AI provider call started operation={} capability={} model={}",
                    operation, request.capability(), request.model());
            ProviderOutput generated = execute(request, credential.accessToken());
            log.info("AI provider call completed operation={} capability={} model={} durationMs={}",
                    operation, request.capability(), request.model(), elapsedMilliseconds(startedAt));
            return new AiGenerationResult(
                    request.capability(),
                    providerName,
                    request.model(),
                    generated.providerResponseId(),
                    generated.providerRequestId(),
                    generated.output(),
                    generated.generatedMedia()
            );
        } catch (RestClientException exception) {
            if (exception instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().is4xxClientError()) {
                log.warn("AI provider call rejected operation={} capability={} model={} durationMs={} errorType={} httpStatus={}",
                        operation, request.capability(), request.model(), elapsedMilliseconds(startedAt),
                        AiProviderResponseDecoder.errorType(exception),
                        AiProviderResponseDecoder.httpStatus(exception));
            } else {
                log.error("AI provider call failed operation={} capability={} model={} durationMs={} errorType={} httpStatus={}",
                        operation, request.capability(), request.model(), elapsedMilliseconds(startedAt),
                        AiProviderResponseDecoder.errorType(exception),
                        AiProviderResponseDecoder.httpStatus(exception), exception);
            }
            throw new IllegalStateException(failureMessage, exception);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    protected void validateRequest(AiGenerationRequest request) {
    }

    protected abstract ProviderOutput execute(AiGenerationRequest request, String accessToken);

    protected final String resolveAccessToken() {
        return apiCredentialService.resolveActive(
                CredentialType.AI_PROVIDER,
                providerName
        ).accessToken();
    }

    protected static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    protected static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    protected record ProviderOutput(
            String output,
            String providerResponseId,
            String providerRequestId,
            AiGenerationResult.GeneratedMedia generatedMedia
    ) {
        protected ProviderOutput(
                String output,
                String providerResponseId,
                String providerRequestId
        ) {
            this(output, providerResponseId, providerRequestId, null);
        }
    }
}
