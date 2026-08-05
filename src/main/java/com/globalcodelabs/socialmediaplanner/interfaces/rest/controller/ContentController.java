package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.command.UploadedMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.application.service.ContentService;
import com.globalcodelabs.socialmediaplanner.application.service.PublishingService;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.CreateContentRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.ScheduleContentRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.UpdateDraftContentRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.ContentPageResponse;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.ContentResponse;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.PublishAttemptResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
@Validated
public class ContentController {

    private final ContentService contentService;
    private final PublishingService publishingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentResponse create(@Valid @RequestBody CreateContentRequest request) {
        Content content = Content.create(
                request.platform(), request.contentType(), request.text(), request.hashtags(), request.batchId()
        );
        if (request.media() != null) {
            request.media().forEach(media -> content.addMedia(
                    media.mediaType(), media.storageKey(), media.publicUrl(),
                    media.modelProvider(), media.modelId()
            ));
        }
        return ContentResponse.from(contentService.create(content));
    }

    @GetMapping
    public ContentPageResponse findAll(
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Platform platform,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ContentPageResponse.from(contentService.findAll(status, platform, batchId, pageRequest));
    }

    @GetMapping("/calendar")
    public List<ContentResponse> findCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Platform platform
    ) {
        return contentService.findCalendar(from, to, status, platform)
                .stream()
                .map(ContentResponse::from)
                .toList();
    }

    @GetMapping("/{contentId}")
    public ContentResponse findById(@PathVariable UUID contentId) {
        return ContentResponse.from(contentService.findById(contentId));
    }

    @GetMapping("/{contentId}/publish-attempts")
    public List<PublishAttemptResponse> findPublishAttempts(@PathVariable UUID contentId) {
        return publishingService.findAttempts(contentId)
                .stream()
                .map(attempt -> PublishAttemptResponse.from(attempt, contentId))
                .toList();
    }

    @PatchMapping("/{contentId}")
    public ContentResponse updateDraft(
            @PathVariable UUID contentId,
            @Valid @RequestBody UpdateDraftContentRequest request
    ) {
        return ContentResponse.from(
                contentService.updateDraft(contentId, request.text(), request.hashtags())
        );
    }

    @PutMapping(
            value = "/{contentId}/media/{mediaType}",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ContentResponse replaceDraftMedia(
            @PathVariable UUID contentId,
            @PathVariable MediaType mediaType,
            @RequestPart("file") MultipartFile file
    ) {
        return ContentResponse.from(
                contentService.replaceDraftMedia(contentId, mediaType, toUploadedMedia(file))
        );
    }

    @GetMapping("/{contentId}/media/{mediaType}/file")
    public ResponseEntity<byte[]> getMediaFile(
            @PathVariable UUID contentId,
            @PathVariable MediaType mediaType
    ) {
        StoredMediaContent media = contentService.getMediaFile(contentId, mediaType);
        String filename = "%s-%s.%s".formatted(
                contentId,
                mediaType.name().toLowerCase(Locale.ROOT),
                extension(media.contentType())
        );
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(media.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString()
                )
                .body(media.bytes());
    }

    @DeleteMapping("/{contentId}/media/{mediaType}")
    public ContentResponse removeDraftMedia(
            @PathVariable UUID contentId,
            @PathVariable MediaType mediaType
    ) {
        return ContentResponse.from(contentService.removeDraftMedia(contentId, mediaType));
    }

    @PutMapping("/{contentId}/schedule")
    public ContentResponse schedule(
            @PathVariable UUID contentId,
            @Valid @RequestBody ScheduleContentRequest request
    ) {
        return ContentResponse.from(contentService.schedule(contentId, request.scheduledAt()));
    }

    @DeleteMapping("/{contentId}/schedule")
    public ContentResponse cancelSchedule(@PathVariable UUID contentId) {
        return ContentResponse.from(contentService.cancelSchedule(contentId));
    }

    @DeleteMapping("/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID contentId) {
        contentService.deleteDraft(contentId);
    }

    private static UploadedMedia toUploadedMedia(MultipartFile file) {
        try {
            return new UploadedMedia(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read uploaded media", exception);
        }
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            default -> throw new IllegalArgumentException("Unsupported media content type: " + contentType);
        };
    }
}
