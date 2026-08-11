package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationBatchTest {

    @Test
    void startsInProgressWithZeroCompletedContent() {
        GenerationBatch batch = createBatch(2);

        assertThat(batch.id()).isNotNull();
        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.IN_PROGRESS);
        assertThat(batch.requestedCount()).isEqualTo(2);
        assertThat(batch.completedCount()).isZero();
        assertThat(batch.platform()).isEqualTo(Platform.LINKEDIN);
        assertThat(batch.contentType()).isEqualTo(ContentType.POST);
        assertThat(batch.textProvider()).isEqualTo("openai");
        assertThat(batch.textModel()).isEqualTo("text-model");
        assertThat(batch.includeImage()).isFalse();
        assertThat(batch.includeVideo()).isFalse();
    }

    @Test
    void completesWhenRequestedContentCountIsReached() {
        GenerationBatch batch = createBatch(2);

        batch.recordCompletedContent();

        assertThat(batch.completedCount()).isEqualTo(1);
        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.IN_PROGRESS);

        batch.recordCompletedContent();

        assertThat(batch.completedCount()).isEqualTo(2);
        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.COMPLETED);
    }

    @Test
    void rejectsAdditionalContentAfterCompletion() {
        GenerationBatch batch = createBatch(1);
        batch.recordCompletedContent();

        assertThatThrownBy(batch::recordCompletedContent)
                .isInstanceOf(DomainException.class);
        assertThat(batch.completedCount()).isEqualTo(1);
        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.COMPLETED);
    }

    @Test
    void failedBatchRejectsFurtherChanges() {
        GenerationBatch batch = createBatch(2);

        batch.markFailed();

        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.FAILED);
        assertThatThrownBy(batch::recordCompletedContent)
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(batch::markFailed)
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> batch.addLinkSource("https://example.com"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> batch.addDocumentSource("documents/source.txt"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void completedBatchCannotBeMarkedFailed() {
        GenerationBatch batch = createBatch(1);
        batch.recordCompletedContent();

        assertThatThrownBy(batch::markFailed)
                .isInstanceOf(DomainException.class);
        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.COMPLETED);
    }

    @Test
    void retriesFailedBatchWithoutResettingCompletedContent() {
        GenerationBatch batch = createBatch(2);
        batch.recordCompletedContent();
        batch.markFailed("  provider unavailable  ");

        batch.retry();

        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.IN_PROGRESS);
        assertThat(batch.completedCount()).isEqualTo(1);
        assertThat(batch.retryCount()).isEqualTo(1);
        assertThat(batch.lastError()).isEqualTo("provider unavailable");
        assertThat(batch.lastRetryAt()).isNotNull();
        assertThat(batch.updatedAt()).isEqualTo(batch.lastRetryAt());
    }

    @Test
    void rejectsRetryUnlessBatchIsFailed() {
        GenerationBatch inProgressBatch = createBatch(1);
        GenerationBatch completedBatch = createBatch(1);
        completedBatch.recordCompletedContent();

        assertThatThrownBy(inProgressBatch::retry)
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(completedBatch::retry)
                .isInstanceOf(DomainException.class);
    }

    @Test
    void reconcilesCompletedCountFromPersistedContent() {
        GenerationBatch partiallyCompletedBatch = createBatch(2);
        partiallyCompletedBatch.reconcileCompletedCount(1);

        assertThat(partiallyCompletedBatch.completedCount()).isEqualTo(1);
        assertThat(partiallyCompletedBatch.status()).isEqualTo(GenerationBatchStatus.IN_PROGRESS);

        partiallyCompletedBatch.reconcileCompletedCount(2);

        assertThat(partiallyCompletedBatch.completedCount()).isEqualTo(2);
        assertThat(partiallyCompletedBatch.status()).isEqualTo(GenerationBatchStatus.COMPLETED);
    }

    @Test
    void rejectsInvalidPersistedContentCount() {
        GenerationBatch batch = createBatch(2);

        assertThatThrownBy(() -> batch.reconcileCompletedCount(-1))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> batch.reconcileCompletedCount(3))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void failedSourceCanRestartWhileCompletedSourceRemainsReusable() {
        GenerationBatch batch = createBatch(1);
        batch.addLinkSource("https://failed.example.com");
        batch.addLinkSource("https://completed.example.com");
        ContentSource failedSource = batch.sources().get(0);
        ContentSource completedSource = batch.sources().get(1);

        failedSource.fail("temporary extraction failure");
        failedSource.startProcessing();
        failedSource.startProcessing();
        completedSource.startProcessing();
        completedSource.complete("persisted source value");

        assertThat(failedSource.status()).isEqualTo(ContentSourceStatus.PROCESSING);
        assertThat(failedSource.errorMessage()).isNull();
        assertThat(completedSource.extractedText()).isEqualTo("persisted source value");
        assertThatThrownBy(completedSource::startProcessing)
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsNonPositiveRequestedCount() {
        assertThatThrownBy(() -> createBatch(0))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(-1))
                .isInstanceOf(DomainException.class);
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
    void enforcesInstagramPostMediaCombinations() {
        GenerationBatch imagePost = createMediaBatch(Platform.INSTAGRAM, ContentType.POST, true, false);

        assertThat(imagePost.includeImage()).isTrue();
        assertThat(imagePost.includeVideo()).isFalse();
        assertThatThrownBy(() -> createMediaBatch(Platform.INSTAGRAM, ContentType.POST, false, false))
                .isInstanceOf(DomainException.class)
                .hasMessage("Instagram POST requires an image");
        assertThatThrownBy(() -> createMediaBatch(Platform.INSTAGRAM, ContentType.POST, true, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("use Instagram REEL");
        assertThatThrownBy(() -> createMediaBatch(Platform.INSTAGRAM, ContentType.POST, false, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("use Instagram REEL");
    }

    @Test
    void enforcesInstagramReelMediaCombinations() {
        GenerationBatch videoReel = createMediaBatch(Platform.INSTAGRAM, ContentType.REEL, false, true);
        GenerationBatch coverAndVideoReel = createMediaBatch(Platform.INSTAGRAM, ContentType.REEL, true, true);

        assertThat(videoReel.includeVideo()).isTrue();
        assertThat(coverAndVideoReel.includeImage()).isTrue();
        assertThat(coverAndVideoReel.includeVideo()).isTrue();
        assertThatThrownBy(() -> createMediaBatch(Platform.INSTAGRAM, ContentType.REEL, false, false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("requires a video");
        assertThatThrownBy(() -> createMediaBatch(Platform.INSTAGRAM, ContentType.REEL, true, false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("requires a video");
    }

    @Test
    void enforcesLinkedInSingleMediaTypeRule() {
        assertThat(createMediaBatch(Platform.LINKEDIN, ContentType.POST, false, false)).isNotNull();
        assertThat(createMediaBatch(Platform.LINKEDIN, ContentType.POST, true, false)).isNotNull();
        assertThat(createMediaBatch(Platform.LINKEDIN, ContentType.POST, false, true)).isNotNull();
        assertThatThrownBy(() -> createMediaBatch(Platform.LINKEDIN, ContentType.POST, true, true))
                .isInstanceOf(DomainException.class)
                .hasMessage("LinkedIn content cannot include both an image and a video; choose only one media type");
    }

    @Test
    void enforcesTwitterSingleMediaTypeRule() {
        assertThat(createMediaBatch(Platform.TWITTER, ContentType.TWEET, false, false)).isNotNull();
        assertThat(createMediaBatch(Platform.TWITTER, ContentType.TWEET, true, false)).isNotNull();
        assertThat(createMediaBatch(Platform.TWITTER, ContentType.TWEET, false, true)).isNotNull();
        assertThatThrownBy(() -> createMediaBatch(Platform.TWITTER, ContentType.TWEET, true, true))
                .isInstanceOf(DomainException.class)
                .hasMessage("X (Twitter) content cannot include both an image and a video; choose only one media type");
    }

    @Test
    void rejectsBlankTextProviderOrModel() {
        assertThatThrownBy(() -> createBatch(
                Platform.LINKEDIN, ContentType.POST, 1,
                false, false, " ", "text-model", null, null, null, null
        )).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(
                Platform.LINKEDIN, ContentType.POST, 1,
                false, false, "openai", " ", null, null, null, null
        )).isInstanceOf(DomainException.class);
    }

    @Test
    void requiresImageProviderAndModelOnlyWhenImageIsEnabled() {
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.POST, 1,
                true, false, "openai", "text-model", null, "image-model", null, null
        )).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.POST, 1,
                true, false, "openai", "text-model", "openai", null, null, null
        )).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.POST, 1,
                false, false, "openai", "text-model", "openai", "image-model", null, null
        )).isInstanceOf(DomainException.class);
    }

    @Test
    void requiresVideoProviderAndModelOnlyWhenVideoIsEnabled() {
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.REEL, 1,
                false, true, "openai", "text-model", null, null, null, "video-model"
        )).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.REEL, 1,
                false, true, "openai", "text-model", null, null, "gemini", null
        )).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> createBatch(
                Platform.INSTAGRAM, ContentType.REEL, 1,
                false, false, "openai", "text-model", null, null, "gemini", "video-model"
        )).isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsConfiguredImageAndVideoModels() {
        GenerationBatch batch = createBatch(
                Platform.INSTAGRAM, ContentType.REEL, 1,
                true, true, " OpenAI ", " text-model ",
                " Gemini ", " image-model ", " Qwen ", " video-model "
        );

        assertThat(batch.includeImage()).isTrue();
        assertThat(batch.includeVideo()).isTrue();
        assertThat(batch.textProvider()).isEqualTo("openai");
        assertThat(batch.textModel()).isEqualTo("text-model");
        assertThat(batch.imageProvider()).isEqualTo("gemini");
        assertThat(batch.imageModel()).isEqualTo("image-model");
        assertThat(batch.videoProvider()).isEqualTo("qwen");
        assertThat(batch.videoModel()).isEqualTo("video-model");
        assertThat(batch.videoDurationSeconds()).isEqualTo(8);
    }

    @Test
    void requiresVideoDurationOnlyWhenVideoGenerationIsEnabled() {
        assertThatThrownBy(() -> GenerationBatch.create(
                "Video batch",
                Platform.LINKEDIN,
                ContentType.POST,
                1,
                AiModelSelection.required("openai", "text-model", "Text"),
                null,
                AiModelSelection.required("gemini", "veo-3.1", "video"),
                null,
                null,
                1
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("Video duration is required");

        assertThatThrownBy(() -> GenerationBatch.create(
                "Text batch",
                Platform.LINKEDIN,
                ContentType.POST,
                1,
                AiModelSelection.required("openai", "text-model", "Text"),
                null,
                null,
                8,
                null,
                1
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("Video duration must be empty");
    }

    @Test
    void defaultsToSourceBasedWhenSourceAndContentCountsMatch() {
        GenerationBatch batch = createBatchWithStrategy(3, 3, null);

        assertThat(batch.generationStrategy()).isEqualTo(GenerationStrategy.SOURCE_BASED);
        assertThat(batch.strategySelectionReason()).contains("source count (3) equals requested content count (3)");
        assertThat(batch.strategyWarning()).isNull();
    }

    @Test
    void defaultsToCombinedWhenSourceAndContentCountsDiffer() {
        GenerationBatch batch = createBatchWithStrategy(3, 1, null);

        assertThat(batch.generationStrategy()).isEqualTo(GenerationStrategy.COMBINED);
        assertThat(batch.strategySelectionReason()).contains("source count (1) differs from requested content count (3)");
        assertThat(batch.strategyWarning()).isNull();
    }

    @Test
    void warnsWhenSourceBasedIsExplicitlySelectedWithFewerSources() {
        GenerationBatch batch = createBatchWithStrategy(3, 1, GenerationStrategy.SOURCE_BASED);

        assertThat(batch.generationStrategy()).isEqualTo(GenerationStrategy.SOURCE_BASED);
        assertThat(batch.strategySelectionReason()).isEqualTo("User selected SOURCE_BASED strategy.");
        assertThat(batch.strategyWarning()).contains("sources will be reused round-robin");
    }

    private static GenerationBatch createBatch(int requestedCount) {
        return createBatch(
                Platform.LINKEDIN, ContentType.POST, requestedCount,
                false, false, " OpenAI ", "text-model", null, null, null, null
        );
    }

    private static GenerationBatch createBatch(
            Platform platform,
            ContentType contentType,
            int requestedCount,
            boolean includeImage,
            boolean includeVideo,
            String textProvider,
            String textModel,
            String imageProvider,
            String imageModel,
            String videoProvider,
            String videoModel
    ) {
        return GenerationBatch.create(
                "Test batch",
                platform,
                contentType,
                requestedCount,
                AiModelSelection.required(textProvider, textModel, "Text"),
                AiModelSelection.optional(includeImage, imageProvider, imageModel, "image"),
                AiModelSelection.optional(includeVideo, videoProvider, videoModel, "video"),
                includeVideo ? 8 : null,
                null,
                1
        );
    }

    private static GenerationBatch createBatchWithStrategy(
            int requestedCount,
            int sourceCount,
            GenerationStrategy strategy
    ) {
        return GenerationBatch.create(
                "Test batch", Platform.LINKEDIN, ContentType.POST, requestedCount,
                AiModelSelection.required("openai", "text-model", "Text"),
                null,
                null,
                null,
                strategy, sourceCount
        );
    }

    private static GenerationBatch createMediaBatch(
            Platform platform,
            ContentType contentType,
            boolean includeImage,
            boolean includeVideo
    ) {
        return createBatch(
                platform,
                contentType,
                1,
                includeImage,
                includeVideo,
                "openai",
                "text-model",
                includeImage ? "gemini" : null,
                includeImage ? "image-model" : null,
                includeVideo ? "qwen" : null,
                includeVideo ? "video-model" : null
        );
    }

    private static void assertUnsupportedCombination(Platform platform, ContentType contentType) {
        assertThatThrownBy(() -> createBatch(
                platform, contentType, 1,
                false, false, "openai", "text-model", null, null, null, null
        )).isInstanceOf(DomainException.class);
    }
}
