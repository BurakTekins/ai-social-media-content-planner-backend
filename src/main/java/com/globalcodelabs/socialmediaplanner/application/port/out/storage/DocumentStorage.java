package com.globalcodelabs.socialmediaplanner.application.port.out.storage;

public interface DocumentStorage {

    StoredDocument store(String originalFilename, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
