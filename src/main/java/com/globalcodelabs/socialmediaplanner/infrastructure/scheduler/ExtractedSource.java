package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import java.util.UUID;

record ExtractedSource(int number, UUID id, String type, String text) {
}
