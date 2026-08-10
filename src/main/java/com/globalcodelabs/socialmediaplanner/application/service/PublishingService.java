package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;

import java.util.List;
import java.util.UUID;

public interface PublishingService {

    boolean publishNextDueContent();

    boolean confirmNextPublishingContent();

    boolean reviewNextTimedOutPublishingContent();

    List<PublishAttempt> findAttempts(UUID contentId);
}
