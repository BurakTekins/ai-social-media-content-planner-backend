package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.ContentOperationNotAllowedException;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.InvalidContentStateTransitionException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentTest {

    @Test
    void createsContentForSupportedPlatformAndContentTypeCombinations() {
        assertThat(create(Platform.LINKEDIN, ContentType.POST).status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(create(Platform.INSTAGRAM, ContentType.POST).status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(create(Platform.INSTAGRAM, ContentType.REEL).status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(create(Platform.TWITTER, ContentType.TWEET).status()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void createsGeneratedContentWithStableBatchSlot() {
        UUID batchId = UUID.randomUUID();

        Content content = Content.createGenerated(
                "Campaign 2",
                Platform.LINKEDIN,
                ContentType.POST,
                "Generated content",
                List.of("generated"),
                batchId,
                2
        );

        assertThat(content.batchId()).isEqualTo(batchId);
        assertThat(content.generationIndex()).isEqualTo(2);
        assertThat(content.title()).isEqualTo("Campaign 2");
    }

    @Test
    void rejectsUnsupportedPlatformAndContentTypeCombinations() {
        assertUnsupportedCombination(Platform.LINKEDIN, ContentType.REEL);
        assertUnsupportedCombination(Platform.LINKEDIN, ContentType.TWEET);
        assertUnsupportedCombination(Platform.INSTAGRAM, ContentType.TWEET);
        assertUnsupportedCombination(Platform.TWITTER, ContentType.POST);
        assertUnsupportedCombination(Platform.TWITTER, ContentType.REEL);
    }

    @Test
    void schedulesReschedulesAndCancelsContent() {
        Content content = create(Platform.LINKEDIN, ContentType.POST);
        OffsetDateTime firstSchedule = OffsetDateTime.now().plusHours(1);
        OffsetDateTime secondSchedule = firstSchedule.plusHours(1);

        content.schedule(firstSchedule);

        assertThat(content.status()).isEqualTo(ContentStatus.SCHEDULED);
        assertThat(content.scheduledAt()).isEqualTo(firstSchedule);

        content.reschedule(secondSchedule);

        assertThat(content.status()).isEqualTo(ContentStatus.SCHEDULED);
        assertThat(content.scheduledAt()).isEqualTo(secondSchedule);

        content.cancelSchedule();

        assertThat(content.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.scheduledAt()).isNull();
        assertThat(content.publishedAt()).isNull();
        assertThat(content.failureReason()).isNull();
    }

    @Test
    void marksPlatformConfirmedContentAsPublished() {
        Content content = scheduledContent();
        OffsetDateTime beforePublication = OffsetDateTime.now();

        content.startPublishing(UUID.randomUUID());
        content.recordExternalPostId("external-post-1");
        content.markPublished();

        assertThat(content.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(content.publishedAt()).isAfterOrEqualTo(beforePublication);
        assertThat(content.failureReason()).isNull();
    }

    @Test
    void marksScheduledContentAsFailed() {
        Content content = scheduledContent();

        content.markFailed("  provider unavailable  ");

        assertThat(content.status()).isEqualTo(ContentStatus.FAILED);
        assertThat(content.publishedAt()).isNull();
        assertThat(content.failureReason()).isEqualTo("provider unavailable");
    }

    @Test
    void rejectsInvalidStateTransitions() {
        Content draft = create(Platform.LINKEDIN, ContentType.POST);

        assertThatThrownBy(draft::markPublished)
                .isInstanceOf(InvalidContentStateTransitionException.class);
        assertThatThrownBy(() -> draft.markFailed("failure"))
                .isInstanceOf(InvalidContentStateTransitionException.class);
        assertThatThrownBy(draft::cancelSchedule)
                .isInstanceOf(InvalidContentStateTransitionException.class);
        assertThatThrownBy(() -> draft.reschedule(OffsetDateTime.now().plusHours(1)))
                .isInstanceOf(InvalidContentStateTransitionException.class);

        Content published = scheduledContent();
        published.startPublishing(UUID.randomUUID());
        published.recordExternalPostId("external-post-2");
        published.markPublished();

        assertThatThrownBy(() -> published.schedule(OffsetDateTime.now().plusHours(2)))
                .isInstanceOf(InvalidContentStateTransitionException.class);
        assertThatThrownBy(() -> published.markFailed("failure"))
                .isInstanceOf(InvalidContentStateTransitionException.class);
    }

    @Test
    void rejectsInvalidScheduledTimesAndBlankFailureReason() {
        Content draft = create(Platform.LINKEDIN, ContentType.POST);

        assertThatThrownBy(() -> draft.schedule(null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> draft.schedule(OffsetDateTime.now().minusMinutes(1)))
                .isInstanceOf(DomainException.class);

        Content scheduled = scheduledContent();

        assertThatThrownBy(() -> scheduled.markFailed(" "))
                .isInstanceOf(DomainException.class);
        assertThat(scheduled.status()).isEqualTo(ContentStatus.SCHEDULED);
    }

    @Test
    void updatesOnlyDraftContent() {
        Content content = create(Platform.LINKEDIN, ContentType.POST);

        content.updateDraft(" Updated title ", " Updated content ", List.of(" updated ", "java"));

        assertThat(content.title()).isEqualTo("Updated title");
        assertThat(content.text()).isEqualTo("Updated content");
        assertThat(content.hashtags()).containsExactly("updated", "java");

        content.schedule(OffsetDateTime.now().plusHours(1));

        assertThatThrownBy(() -> content.updateDraft(null, "Another text", null))
                .isInstanceOf(ContentOperationNotAllowedException.class);
    }

    @Test
    void requiresAtLeastOneDraftFieldToUpdate() {
        Content content = create(Platform.LINKEDIN, ContentType.POST);

        assertThatThrownBy(() -> content.updateDraft(null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsTwitterContentWhenTextAndHashtagsExceedCharacterLimit() {
        String text = "x".repeat(271);

        assertThatThrownBy(() -> Content.create(
                "Twitter title",
                Platform.TWITTER,
                ContentType.TWEET,
                text,
                List.of("toolong"),
                null
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("280");
    }

    @Test
    void rejectsDraftUpdateThatExceedsTwitterCharacterLimit() {
        Content content = create(Platform.TWITTER, ContentType.TWEET);

        assertThatThrownBy(() -> content.updateDraft(null, "x".repeat(281), List.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("280");
    }

    @Test
    void allowsOneMediaItemPerMediaType() {
        Content content = create(Platform.INSTAGRAM, ContentType.REEL);
        content.addMedia(
                MediaType.IMAGE,
                "generated/image/content-id",
                "https://cdn.example.com/image.png",
                "openai",
                "image-model"
        );
        content.addMedia(
                MediaType.VIDEO,
                "generated/video/content-id",
                "https://cdn.example.com/video.mp4",
                "gemini",
                "video-model"
        );

        assertThat(content.media())
                .extracting(ContentMedia::mediaType)
                .containsExactly(MediaType.IMAGE, MediaType.VIDEO);
        assertThat(content.media())
                .extracting(ContentMedia::createdAt)
                .doesNotContainNull();

        assertThatThrownBy(() -> content.addMedia(
                MediaType.IMAGE,
                "generated/image/duplicate",
                "https://cdn.example.com/duplicate.png",
                "openai",
                "another-model"
        )).isInstanceOf(DomainException.class);
        assertThat(content.media()).hasSize(2);
    }

    private static Content scheduledContent() {
        Content content = create(Platform.LINKEDIN, ContentType.POST);
        content.schedule(OffsetDateTime.now().plusHours(1));
        return content;
    }

    private static Content create(Platform platform, ContentType contentType) {
        return Content.create("Test content", platform, contentType, "Content text", List.of("social"), null);
    }

    private static void assertUnsupportedCombination(Platform platform, ContentType contentType) {
        assertThatThrownBy(() -> create(platform, contentType))
                .isInstanceOf(DomainException.class);
    }
}
