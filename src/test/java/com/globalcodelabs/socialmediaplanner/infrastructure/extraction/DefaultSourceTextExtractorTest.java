package com.globalcodelabs.socialmediaplanner.infrastructure.extraction;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceType;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalDocumentStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DefaultSourceTextExtractorTest {

    @Test
    void shouldRejectLiteralPrivateAddressBeforeSendingRequest() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setLinkTimeout(Duration.ofSeconds(1));
        properties.setMaxBodySizeBytes(1024);
        DefaultSourceTextExtractor extractor = new DefaultSourceTextExtractor(
                mock(LocalDocumentStorage.class), properties
        );

        assertThatThrownBy(() -> extractor.extract(
                ContentSourceType.LINK,
                "http://127.0.0.1:8080/actuator/health"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Private network links are not allowed");
    }
}
