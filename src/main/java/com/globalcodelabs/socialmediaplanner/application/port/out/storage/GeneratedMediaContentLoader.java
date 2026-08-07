package com.globalcodelabs.socialmediaplanner.application.port.out.storage;

import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;

public interface GeneratedMediaContentLoader {

    StoredMediaContent loadGenerated(MediaType mediaType, String sourceUrl);
}
