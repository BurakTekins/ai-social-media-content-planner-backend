package com.globalcodelabs.socialmediaplanner.application.port.out.extraction;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentSourceType;

public interface SourceTextExtractor {

    String extract(ContentSourceType sourceType, String sourceValue);
}
