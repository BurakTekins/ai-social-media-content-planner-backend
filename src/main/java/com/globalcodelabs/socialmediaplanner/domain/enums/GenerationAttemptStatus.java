package com.globalcodelabs.socialmediaplanner.domain.enums;

public enum GenerationAttemptStatus {
    STARTED,
    SUBMITTED,
    PROCESSING,
    PROVIDER_SUCCEEDED,
    DOWNLOAD_FAILED,
    DOWNLOADED,
    AWAITING_REGENERATION_CONSENT,
    REGENERATION_APPROVED,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    INVALID
}
