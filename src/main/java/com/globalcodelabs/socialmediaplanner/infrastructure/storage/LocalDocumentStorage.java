package com.globalcodelabs.socialmediaplanner.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LocalDocumentStorage {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final StorageProperties properties;

    private Path rootDirectory;

    @PostConstruct
    void initialize() {
        rootDirectory = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize document storage", exception);
        }
    }

    public StoredDocument store(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Document cannot be empty");
        }
        String extension = extractSupportedExtension(originalFilename);
        String storageKey = UUID.randomUUID() + "." + extension;
        Path target = resolveStorageKey(storageKey);
        try {
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return new StoredDocument(storageKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store document", exception);
        }
    }

    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolveStorageKey(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read stored document", exception);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveStorageKey(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete stored document", exception);
        }
    }

    private String extractSupportedExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Document filename cannot be blank");
        }
        int extensionSeparator = originalFilename.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == originalFilename.length() - 1) {
            throw new IllegalArgumentException("Document extension is missing");
        }
        String extension = originalFilename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported document extension: " + extension);
        }
        return extension;
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key cannot be blank");
        }
        Path resolved = rootDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
