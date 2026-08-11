package com.globalcodelabs.socialmediaplanner.infrastructure.oauth;

import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import org.slf4j.Logger;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

public final class OAuthClientSupport {

    private OAuthClientSupport() {
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    public static <T> T execute(
            String provider,
            String operation,
            Supplier<T> call,
            Logger log
    ) {
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(provider);
        try {
            log.info("OAuth provider call started operation={}", operation);
            T result = call.get();
            log.info("OAuth provider call completed operation={} durationMs={}",
                    operation, elapsedMilliseconds(startedAt));
            return result;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                log.warn("OAuth provider call rejected operation={} httpStatus={} durationMs={}",
                        operation, exception.getStatusCode().value(), elapsedMilliseconds(startedAt));
            } else {
                log.error("OAuth provider call failed operation={} httpStatus={} durationMs={}",
                        operation, exception.getStatusCode().value(),
                        elapsedMilliseconds(startedAt), exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            log.error("OAuth provider call failed operation={} errorType={} durationMs={}",
                    operation, exception.getClass().getSimpleName(),
                    elapsedMilliseconds(startedAt), exception);
            throw exception;
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
