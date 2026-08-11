package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;

import java.util.List;

final class GenerationPromptFactory {

    private static final List<ContentAngle> CONTENT_ANGLES = List.of(
            new ContentAngle(
                    "BENEFIT_FOCUSED",
                    "Lead with the most concrete source-backed benefit and explain why it matters to the audience."
            ),
            new ContentAngle(
                    "QUESTION_LED",
                    "Open with a relevant question, then answer it using only facts supported by the sources."
            ),
            new ContentAngle(
                    "CTA_FOCUSED",
                    "Build the content toward one clear, source-supported call to action."
            ),
            new ContentAngle(
                    "PROBLEM_SOLUTION",
                    "Frame a problem described or implied by the sources, then present the source-backed solution."
            ),
            new ContentAngle(
                    "FACT_LED",
                    "Lead with a concrete fact or detail from the sources and turn it into a concise takeaway."
            )
    );

    private GenerationPromptFactory() {
    }

    static String textPrompt(GenerationBatch batch, List<ExtractedSource> sources, int index) {
        ContentAngle angle = CONTENT_ANGLES.get((index - 1) % CONTENT_ANGLES.size());
        SourcePromptContext sourceContext = sourcePromptContext(batch.generationStrategy(), sources, index);
        return """
                Generate one distinct social media content item using the supplied source context.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Do not wrap the JSON in Markdown or add explanations.
                Write in the natural language used by the sources unless they explicitly request another language.
                Generate at least one hashtag relevant to both the source topic and the selected platform.
                Do not add unrelated or generic trending hashtags. Return hashtags in descending order of relevance.
                Treat every conditional offer, discount, benefit, eligibility rule, prerequisite, limitation, and duration
                in the sources as one indivisible claim-condition unit. If you mention the claim, include all of its
                conditions accurately; otherwise omit the entire claim. Never present a conditional claim as unconditional.
                Platform: %s
                Content type: %s
                Content number: %d of %d
                Generation strategy: %s
                Strategy requirements:
                %s
                Creative angle: %s
                Angle requirements: %s
                Platform requirements:
                %s
                Source context:
                %s
                """.formatted(
                batch.platform(), batch.contentType(), index, batch.requestedCount(),
                batch.generationStrategy(), sourceContext.instructions(),
                angle.name(), angle.instructions(),
                platformRequirements(batch.platform(), batch.contentType()), sourceContext.content()
        );
    }

    static String mediaPrompt(Content content, MediaType mediaType) {
        String formatRequirements = switch (content.platform()) {
            case LINKEDIN -> "Use a professional visual style appropriate for a LinkedIn post.";
            case INSTAGRAM -> content.contentType() == ContentType.REEL
                    ? "Use a striking, short-form social media style appropriate for an Instagram Reel."
                    : "Use an engaging, feed-ready visual style appropriate for an Instagram post.";
            case TWITTER -> "Use a concise supporting visual style appropriate for an X post.";
        };
        return """
                Generate one %s for the social media content below.
                The media must directly represent the subject, message, and tone of the text.
                Do not introduce unrelated products, claims, people, brands, or events.
                Platform: %s
                Content type: %s
                Platform and format requirements: %s
                Content text:
                %s
                Content hashtags:
                %s
                """.formatted(
                mediaType,
                content.platform(),
                content.contentType(),
                formatRequirements,
                content.text(),
                String.join(" ", content.hashtags())
        );
    }

    static String correctionPrompt(String originalPrompt, String invalidOutput, String validationError) {
        return """
                Correct the previous response so it satisfies every original requirement.
                Return only valid JSON with exactly these fields: text (string) and hashtags (array of strings).
                Preserve the main message, but shorten the text and hashtags as needed.
                Preserve every condition attached to an offer, discount, benefit, eligibility rule, prerequisite,
                limitation, or duration. Never shorten a claim by removing its conditions; omit the whole claim instead.
                For Twitter, target at most 250 X weighted characters after formatting and use at most 3 short hashtags.
                Return hashtags in descending order of importance, with the most important hashtag first.
                Do not wrap the JSON in Markdown or add explanations.
                Validation error:
                %s
                Previous invalid response:
                %s
                Original requirements:
                %s
                """.formatted(validationError, invalidOutput, originalPrompt);
    }

    private static SourcePromptContext sourcePromptContext(
            GenerationStrategy strategy,
            List<ExtractedSource> sources,
            int generationIndex
    ) {
        if (strategy == GenerationStrategy.COMBINED) {
            return new SourcePromptContext(
                    "Treat all sources as one combined information pool. Use the assigned creative angle to make this "
                            + "content materially different from the other requested items.",
                    sources.stream().map(GenerationPromptFactory::formatSource).reduce(
                            (left, right) -> left + System.lineSeparator() + System.lineSeparator() + right
                    ).orElseThrow()
            );
        }

        int primarySourceIndex = (generationIndex - 1) % sources.size();
        ExtractedSource primarySource = sources.get(primarySourceIndex);
        String supportingSources = sources.stream()
                .filter(source -> source != primarySource)
                .map(GenerationPromptFactory::formatSource)
                .reduce((left, right) -> left + System.lineSeparator() + System.lineSeparator() + right)
                .orElse("None.");
        return new SourcePromptContext(
                "The primary source must determine the central message. Supporting sources may add context, but must "
                        + "not displace or contradict the primary source.",
                "Primary source:\n%s\n\nSupporting sources:\n%s"
                        .formatted(formatSource(primarySource), supportingSources)
        );
    }

    private static String formatSource(ExtractedSource source) {
        return "[Source %d | type=%s | id=%s]\n%s"
                .formatted(source.number(), source.type(), source.id(), source.text());
    }

    private static String platformRequirements(Platform platform, ContentType contentType) {
        return switch (platform) {
            case LINKEDIN -> "Use a professional, informative tone and a more detailed text format suitable for a "
                    + "LinkedIn post. Keep the structure readable and use professional, topic-relevant hashtags.";
            case INSTAGRAM -> contentType == ContentType.REEL
                    ? "Write a short, high-impact caption that complements visual and video-first Reel content. "
                            + "Use concise, topic-relevant Instagram hashtags."
                    : "Write a short, engaging caption suitable for a visual Instagram feed post. "
                            + "Use topic-relevant Instagram hashtags.";
            case TWITTER -> "The published value is formatted as text, two newline characters, then "
                    + "space-separated hashtags prefixed with #. Its X weighted length must not exceed 280. "
                    + "Target at most 250 weighted characters, use at most 3 short hashtags, and use a concise Tweet format. "
                    + "Return hashtags in descending order of importance, with the most important hashtag first.";
        };
    }

    private record SourcePromptContext(String instructions, String content) {
    }

    private record ContentAngle(String name, String instructions) {
    }
}
