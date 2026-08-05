package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderResponseDecoderTest {

    private final AiProviderResponseDecoder decoder = new AiProviderResponseDecoder(new ObjectMapper());

    @Test
    void decodesRawJsonAndReturnsRequestIdFromHeaders() {
        ResponseEntity<String> response = ResponseEntity.ok()
                .header("x-request-id", "request-123")
                .body("{\"value\":\"generated output\"}");

        AiProviderResponseDecoder.DecodedResponse<TestResponse> decoded = decoder.decode(
                response,
                TestResponse.class,
                "Test provider",
                "x-request-id"
        );

        assertThat(decoded.body()).isEqualTo(new TestResponse("generated output"));
        assertThat(decoded.providerRequestId()).isEqualTo("request-123");
    }

    @Test
    void malformedJsonKeepsRequestIdWithoutExposingResponseBody() {
        ResponseEntity<String> response = ResponseEntity.ok()
                .header("request-id", "request-456")
                .body("{\"value\":\"sensitive-provider-output\"");

        assertThatThrownBy(() -> decoder.decode(
                response,
                TestResponse.class,
                "Test provider",
                "request-id"
        ))
                .isInstanceOfSatisfying(AiProviderResponseException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("Test provider returned an invalid JSON response");
                    assertThat(exception.providerResponseId()).isNull();
                    assertThat(exception.providerRequestId()).isEqualTo("request-456");
                    assertThat(exception).hasNoCause();
                })
                .hasMessageNotContaining("sensitive-provider-output");
    }

    private record TestResponse(String value) {
    }
}
