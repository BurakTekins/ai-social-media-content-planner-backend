package com.globalcodelabs.socialmediaplanner.infrastructure.storage;

import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LocalMediaStorage implements MediaStorage {

    private static final String MEDIA_DIRECTORY_NAME = "media";
    private static final String MEDIA_KEY_PREFIX = MEDIA_DIRECTORY_NAME + "/";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "gif", "webp", "mp4");
    private static final Set<String> MP4_BRANDS = Set.of(
            "avc1", "dash", "iso2", "iso3", "iso4", "iso5", "iso6", "isom",
            "m4v ", "mp41", "mp42", "msnv"
    );

    private final StorageProperties properties;

    private Path uploadDirectory;
    private Path mediaDirectory;

    @PostConstruct
    void initialize() {
        uploadDirectory = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        mediaDirectory = uploadDirectory.resolve(MEDIA_DIRECTORY_NAME).normalize();
        if (!mediaDirectory.startsWith(uploadDirectory)) {
            throw new IllegalStateException("Invalid media storage directory");
        }
        try {
            Files.createDirectories(mediaDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize media storage", exception);
        }
    }

    @Override
    public StoredMedia store(MediaType mediaType, String declaredContentType, byte[] content) {
        Objects.requireNonNull(mediaType, "Media type cannot be null");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Media content cannot be empty");
        }

        DetectedMedia detectedMedia = detectMedia(content);
        validateMediaType(mediaType, detectedMedia);
        validateDeclaredContentType(declaredContentType, detectedMedia.contentType());

        String storageKey = MEDIA_KEY_PREFIX + UUID.randomUUID() + "." + detectedMedia.extension();
        Path target = resolveOwnedStorageKey(storageKey);
        try {
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return new StoredMedia(storageKey, detectedMedia.mediaType(), detectedMedia.contentType());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store media", exception);
        }
    }

    @Override
    public StoredMediaContent read(String storageKey) {
        Path target = resolveOwnedStorageKey(storageKey);
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Invalid media storage key");
        }
        try {
            byte[] content = Files.readAllBytes(target);
            DetectedMedia detectedMedia = detectMedia(content);
            return new StoredMediaContent(detectedMedia.contentType(), content);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read stored media", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveOwnedStorageKeyOrNull(storageKey);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete stored media", exception);
        }
    }

    private DetectedMedia detectMedia(byte[] content) {
        if (hasPrefix(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new DetectedMedia(MediaType.IMAGE, "image/png", "png");
        }
        if (hasPrefix(content, 0xFF, 0xD8, 0xFF)) {
            return new DetectedMedia(MediaType.IMAGE, "image/jpeg", "jpg");
        }
        if (hasAsciiPrefix(content, "GIF87a") || hasAsciiPrefix(content, "GIF89a")) {
            return new DetectedMedia(MediaType.IMAGE, "image/gif", "gif");
        }
        if (content.length >= 12
                && hasAsciiAt(content, 0, "RIFF")
                && hasAsciiAt(content, 8, "WEBP")) {
            return new DetectedMedia(MediaType.IMAGE, "image/webp", "webp");
        }
        if (isMp4(content)) {
            return new DetectedMedia(MediaType.VIDEO, "video/mp4", "mp4");
        }
        throw new IllegalArgumentException("Unsupported or invalid media content");
    }

    private boolean isMp4(byte[] content) {
        if (content.length < 16 || !hasAsciiAt(content, 4, "ftyp")) {
            return false;
        }

        long boxSize = ((long) Byte.toUnsignedInt(content[0]) << 24)
                | ((long) Byte.toUnsignedInt(content[1]) << 16)
                | ((long) Byte.toUnsignedInt(content[2]) << 8)
                | Byte.toUnsignedInt(content[3]);
        if (boxSize < 16 || boxSize > content.length) {
            return false;
        }

        int boxEnd = (int) boxSize;
        for (int offset = 8; offset + 4 <= boxEnd; offset += 4) {
            String brand = new String(content, offset, 4, StandardCharsets.US_ASCII)
                    .toLowerCase(Locale.ROOT);
            if (MP4_BRANDS.contains(brand)) {
                return true;
            }
        }
        return false;
    }

    private void validateMediaType(MediaType requestedMediaType, DetectedMedia detectedMedia) {
        if (requestedMediaType != detectedMedia.mediaType()) {
            throw new IllegalArgumentException(
                    "Declared media type does not match detected media content"
            );
        }
    }

    private void validateDeclaredContentType(String declaredContentType, String detectedContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return;
        }
        String normalizedContentType = declaredContentType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!detectedContentType.equals(normalizedContentType)) {
            throw new IllegalArgumentException(
                    "Declared content type does not match detected media content"
            );
        }
    }

    private Path resolveOwnedStorageKey(String storageKey) {
        Path resolved = resolveOwnedStorageKeyOrNull(storageKey);
        if (resolved == null) {
            throw new IllegalArgumentException("Invalid media storage key");
        }
        return resolved;
    }

    private Path resolveOwnedStorageKeyOrNull(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\\') >= 0) {
            return null;
        }
        String normalizedKey = storageKey.trim();
        if (!normalizedKey.startsWith(MEDIA_KEY_PREFIX)) {
            return null;
        }

        String filename = normalizedKey.substring(MEDIA_KEY_PREFIX.length());
        if (filename.isBlank() || filename.indexOf('/') >= 0 || !hasSupportedExtension(filename)) {
            return null;
        }
        int extensionSeparator = filename.lastIndexOf('.');
        try {
            UUID.fromString(filename.substring(0, extensionSeparator));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Path resolved = uploadDirectory.resolve(normalizedKey).normalize();
        if (!resolved.startsWith(mediaDirectory) || !resolved.getParent().equals(mediaDirectory)) {
            return null;
        }
        return resolved;
    }

    private boolean hasSupportedExtension(String filename) {
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == filename.length() - 1) {
            return false;
        }
        String extension = filename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private boolean hasPrefix(byte[] content, int... expectedBytes) {
        if (content.length < expectedBytes.length) {
            return false;
        }
        for (int index = 0; index < expectedBytes.length; index++) {
            if (Byte.toUnsignedInt(content[index]) != expectedBytes[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAsciiPrefix(byte[] content, String expected) {
        return hasAsciiAt(content, 0, expected);
    }

    private boolean hasAsciiAt(byte[] content, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || content.length - offset < expectedBytes.length) {
            return false;
        }
        for (int index = 0; index < expectedBytes.length; index++) {
            if (content[offset + index] != expectedBytes[index]) {
                return false;
            }
        }
        return true;
    }

    private record DetectedMedia(MediaType mediaType, String contentType, String extension) {
    }
}
