package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.UploadedMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ContentService {

    Content create(Content content);

    Page<Content> findAll(ContentStatus status, Platform platform, UUID batchId, Pageable pageable);

    Content findById(UUID contentId);

    List<Content> findCalendar(
            OffsetDateTime from,
            OffsetDateTime to,
            ContentStatus status,
            Platform platform
    );

    Content updateDraft(UUID contentId, String text, List<String> hashtags);

    Content replaceDraftMedia(UUID contentId, MediaType mediaType, UploadedMedia uploadedMedia);

    Content removeDraftMedia(UUID contentId, MediaType mediaType);

    StoredMediaContent getMediaFile(UUID contentId, MediaType mediaType);

    Content schedule(UUID contentId, OffsetDateTime scheduledAt);

    Content cancelSchedule(UUID contentId);

    void deleteDraft(UUID contentId);
}
