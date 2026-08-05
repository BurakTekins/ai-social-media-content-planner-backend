package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.command.UploadedMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.MediaStorage;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.application.service.ContentService;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentMediaNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.InvalidContentStateTransitionException;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentServiceImpl implements ContentService {

    private final ContentRepository contentRepository;
    private final MediaStorage mediaStorage;

    @Override
    @Transactional
    public Content create(Content content) {
        return contentRepository.save(content);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Content> findAll(ContentStatus status, Platform platform, UUID batchId, Pageable pageable) {
        Page<Content> contents = contentRepository.findAllByFilters(status, platform, batchId, pageable);
        contents.forEach(Content::media);
        return contents;
    }

    @Override
    @Transactional(readOnly = true)
    public Content findById(UUID contentId) {
        return contentRepository.findWithMediaById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Content> findCalendar(
            OffsetDateTime from,
            OffsetDateTime to,
            ContentStatus status,
            Platform platform
    ) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new DomainException("Calendar start time must be before end time");
        }
        return contentRepository.findCalendarEntries(from, to, status, platform);
    }

    @Override
    @Transactional
    public Content updateDraft(UUID contentId, String text, List<String> hashtags) {
        Content content = findRequired(contentId);
        content.updateDraft(text, hashtags);
        initializeMedia(content);
        log.debug("Draft content updated contentId={}", contentId);
        return content;
    }

    @Override
    @Transactional
    public Content replaceDraftMedia(UUID contentId, MediaType mediaType, UploadedMedia uploadedMedia) {
        Content content = findRequiredWithMedia(contentId);
        StoredMedia storedMedia = mediaStorage.store(
                mediaType,
                uploadedMedia.contentType(),
                uploadedMedia.content()
        );
        try {
            String replacedStorageKey = content.replaceMedia(
                    mediaType,
                    storedMedia.storageKey(),
                    null,
                    null,
                    null
            );
            contentRepository.saveAndFlush(content);
            deleteOnRollback(storedMedia.storageKey());
            deleteAfterCommit(replacedStorageKey);
            log.info("Draft content media replaced contentId={} mediaType={}", contentId, mediaType);
            return content;
        } catch (RuntimeException exception) {
            deleteSafely(storedMedia.storageKey());
            throw exception;
        }
    }

    @Override
    @Transactional
    public Content removeDraftMedia(UUID contentId, MediaType mediaType) {
        Content content = findRequiredWithMedia(contentId);
        content.findMedia(mediaType)
                .orElseThrow(() -> new ContentMediaNotFoundException(contentId, mediaType));
        String removedStorageKey = content.removeMedia(mediaType);
        contentRepository.saveAndFlush(content);
        deleteAfterCommit(removedStorageKey);
        log.info("Draft content media removed contentId={} mediaType={}", contentId, mediaType);
        return content;
    }

    @Override
    @Transactional(readOnly = true)
    public StoredMediaContent getMediaFile(UUID contentId, MediaType mediaType) {
        Content content = findRequiredWithMedia(contentId);
        String storageKey = content.findMedia(mediaType)
                .orElseThrow(() -> new ContentMediaNotFoundException(contentId, mediaType))
                .storageKey();
        return mediaStorage.read(storageKey);
    }

    @Override
    @Transactional
    public Content schedule(UUID contentId, OffsetDateTime scheduledAt) {
        Content content = findRequired(contentId);
        switch (content.status()) {
            case DRAFT -> content.schedule(scheduledAt);
            case SCHEDULED -> content.reschedule(scheduledAt);
            case FAILED -> content.retryPublishing(scheduledAt);
            case PUBLISHED -> throw new InvalidContentStateTransitionException(
                    ContentStatus.PUBLISHED,
                    ContentStatus.SCHEDULED
            );
        }
        initializeMedia(content);
        log.info("Content scheduled contentId={} scheduledAt={}", contentId, scheduledAt);
        return content;
    }

    @Override
    @Transactional
    public Content cancelSchedule(UUID contentId) {
        Content content = findRequired(contentId);
        content.cancelSchedule();
        initializeMedia(content);
        log.info("Content schedule cancelled contentId={}", contentId);
        return content;
    }

    @Override
    @Transactional
    public void deleteDraft(UUID contentId) {
        Content content = findRequiredWithMedia(contentId);
        content.ensureDeletable();
        List<String> mediaStorageKeys = content.media().stream()
                .map(media -> media.storageKey())
                .toList();
        contentRepository.delete(content);
        mediaStorageKeys.forEach(this::deleteAfterCommit);
        log.info("Draft content deleted contentId={}", contentId);
    }

    private Content findRequired(UUID contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    private Content findRequiredWithMedia(UUID contentId) {
        return contentRepository.findWithMediaById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    private void deleteAfterCommit(String storageKey) {
        if (storageKey == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteSafely(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteSafely(storageKey);
            }
        });
    }

    private void deleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteSafely(storageKey);
                }
            }
        });
    }

    private void deleteSafely(String storageKey) {
        try {
            mediaStorage.delete(storageKey);
        } catch (RuntimeException exception) {
            log.warn("Could not delete local media storageKey={}", storageKey, exception);
        }
    }

    private static void initializeMedia(Content content) {
        content.media();
    }
}
