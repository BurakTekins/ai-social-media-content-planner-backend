package com.globalcodelabs.socialmediaplanner.application.port.out.storage;

import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;

public interface MediaStorage {

    StoredMedia store(MediaType mediaType, String declaredContentType, byte[] content);

    StoredMediaContent read(String storageKey);

    void delete(String storageKey);
}
