package com.globalcodelabs.socialmediaplanner.infrastructure.ai.mock;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockAiProviderClient implements AiProviderClient {

    private static final String SOURCE_CONTEXT_MARKER = "Source context:";
    private static final String CURRENT_TEXT_MARKER = "Current text:";
    private static final Pattern CONTENT_NUMBER = Pattern.compile("Content number: (\\d+) of \\d+");
    private static final Pattern CREATIVE_ANGLE = Pattern.compile("Creative angle: ([A-Z_]+)");
    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}\\p{N}_-]{3,}");
    private static final Set<String> TOPIC_STOP_WORDS = Set.of(
            "source", "primary", "supporting", "none", "context", "platform", "content",
            "kaynagi", "kaynak", "icerik", "kampanya", "bilgileri", "kurallari"
    );

    private final ObjectMapper objectMapper;
    private final ApiCredentialService apiCredentialService;

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        apiCredentialService.resolveActive(CredentialType.AI_PROVIDER, request.provider());
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(request.provider());

        try {
            log.info("AI generation started mode=mock capability={} model={}",
                    request.capability(), request.model());
            if ("mock-failure".equalsIgnoreCase(request.model())) {
                throw new IllegalStateException("Mock AI provider failure requested");
            }
            String output = createMockOutput(request);
            long durationMs = elapsedMilliseconds(startedAt);
            log.info("AI generation completed mode=mock capability={} model={} durationMs={}",
                    request.capability(), request.model(), durationMs);
            return new AiGenerationResult(
                    request.capability(),
                    request.provider(),
                    request.model(),
                    null,
                    null,
                    output
            );
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private String createMockOutput(AiGenerationRequest request) {
        String fingerprint = Integer.toUnsignedString(request.prompt().hashCode(), 16);
        return switch (request.capability()) {
            case TEXT -> createTextOutput(request.provider(), request.prompt(), fingerprint);
            case IMAGE -> "mock://" + request.provider() + "/image/" + fingerprint + ".png";
            case VIDEO -> "mock://" + request.provider() + "/video/" + fingerprint + ".mp4";
        };
    }

    private String createTextOutput(String provider, String prompt, String fingerprint) {
        try {
            String sourceContext = extractContentContext(prompt);
            return objectMapper.writeValueAsString(new MockTextOutput(
                    mockText(prompt, sourceContext, fingerprint),
                    mockHashtags(prompt, sourceContext, provider)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not create mock AI response", exception);
        }
    }

    private static String mockText(String prompt, String sourceContext, String fingerprint) {
        List<String> sourceSentences = sourceSentences(sourceContext);
        if (sourceSentences.isEmpty()) {
            return fallbackText(prompt, fingerprint);
        }

        String angle = match(CREATIVE_ANGLE, prompt, "BENEFIT_FOCUSED");
        int contentNumber = Integer.parseInt(match(CONTENT_NUMBER, prompt, "1"));
        int startIndex = preferredSentenceIndex(sourceSentences, angle, contentNumber);
        int maximumLength = prompt.contains("Platform: TWITTER")
                ? 190
                : prompt.contains("Platform: INSTAGRAM") ? 240 : 500;
        String prefix = anglePrefix(angle);
        return composeFromSource(sourceSentences, startIndex, prefix, maximumLength);
    }

    private static String extractContentContext(String prompt) {
        int markerIndex = prompt.lastIndexOf(SOURCE_CONTEXT_MARKER);
        if (markerIndex >= 0) {
            return prompt.substring(markerIndex + SOURCE_CONTEXT_MARKER.length()).trim();
        }
        markerIndex = prompt.lastIndexOf(CURRENT_TEXT_MARKER);
        return markerIndex < 0
                ? ""
                : prompt.substring(markerIndex + CURRENT_TEXT_MARKER.length()).trim();
    }

    private static List<String> sourceSentences(String sourceContext) {
        LinkedHashSet<String> sentences = new LinkedHashSet<>();
        sourceContext.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !isContextMetadata(line))
                .filter(line -> !isHashtagOnlyLine(line))
                .flatMap(line -> Arrays.stream(line.split("(?<=[.!?])\\s+")))
                .map(MockAiProviderClient::normalizeSentence)
                .filter(sentence -> sentence.length() >= 12)
                .filter(sentence -> !isHeading(sentence))
                .forEach(sentences::add);
        return List.copyOf(sentences);
    }

    private static String normalizeSentence(String sentence) {
        return sentence.replaceAll("\\s+", " ").trim();
    }

    private static boolean isHeading(String sentence) {
        if (sentence.length() > 80 || sentence.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        String lettersOnly = sentence.replaceAll("[^\\p{L}]", "");
        return !lettersOnly.isBlank() && sentence.equals(sentence.toUpperCase(Locale.ROOT));
    }

    private static boolean isContextMetadata(String line) {
        return line.equals("Primary source:")
                || line.equals("Supporting sources:")
                || line.equals("Current hashtags:")
                || line.equals("None.")
                || line.startsWith("[Source ");
    }

    private static boolean isHashtagOnlyLine(String line) {
        return !line.isBlank() && HASHTAG.matcher(line).replaceAll("").isBlank();
    }

    private static int preferredSentenceIndex(List<String> sentences, String angle, int contentNumber) {
        if ("CTA_FOCUSED".equals(angle)) {
            for (int index = 0; index < sentences.size(); index++) {
                if (sentences.get(index).contains("http://") || sentences.get(index).contains("https://")) {
                    return index;
                }
            }
        }
        if ("FACT_LED".equals(angle)) {
            for (int index = 0; index < sentences.size(); index++) {
                if (sentences.get(index).chars().anyMatch(Character::isDigit)) {
                    return index;
                }
            }
        }
        return Math.floorMod(contentNumber - 1, sentences.size());
    }

    private static String anglePrefix(String angle) {
        return switch (angle) {
            case "QUESTION_LED" -> "Kaynakta öne çıkan fırsat nedir? ";
            case "CTA_FOCUSED" -> "Harekete geçin: ";
            case "PROBLEM_SOLUTION" -> "İhtiyaca odaklanan çözüm: ";
            case "FACT_LED" -> "Kaynağın öne çıkan bilgisi: ";
            default -> "Öne çıkan fayda: ";
        };
    }

    private static String composeFromSource(
            List<String> sentences,
            int startIndex,
            String prefix,
            int maximumLength
    ) {
        List<String> selected = new ArrayList<>();
        int currentLength = prefix.length();
        for (int offset = 0; offset < sentences.size(); offset++) {
            String sentence = sentences.get((startIndex + offset) % sentences.size());
            int projectedLength = currentLength + (selected.isEmpty() ? 0 : 1) + sentence.length();
            if (projectedLength > maximumLength) {
                continue;
            }
            selected.add(sentence);
            currentLength = projectedLength;
            if (selected.size() == 3) {
                break;
            }
        }
        if (selected.isEmpty()) {
            return prefix + truncateAtWord(sentences.get(startIndex), maximumLength - prefix.length());
        }
        return prefix + String.join(" ", selected);
    }

    private static String truncateAtWord(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int boundary = value.lastIndexOf(' ', maximumLength);
        return value.substring(0, boundary > 0 ? boundary : maximumLength).trim();
    }

    private static String[] mockHashtags(String prompt, String sourceContext, String provider) {
        String hashtagContext = sourceContext.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !isContextMetadata(line))
                .collect(Collectors.joining(" "));
        String sourceContent = String.join(" ", sourceSentences(sourceContext));
        LinkedHashSet<String> hashtags = new LinkedHashSet<>();
        Matcher hashtagMatcher = HASHTAG.matcher(hashtagContext);
        int maximumHashtagCount = prompt.contains("Platform: TWITTER") ? 3 : 4;
        while (hashtagMatcher.find() && hashtags.size() < maximumHashtagCount) {
            hashtags.add(hashtagMatcher.group());
        }
        if (hashtags.isEmpty()) {
            Matcher wordMatcher = WORD.matcher(sourceContent);
            while (wordMatcher.find()) {
                String candidate = wordMatcher.group();
                if (!TOPIC_STOP_WORDS.contains(candidate.toLowerCase(Locale.ROOT))) {
                    hashtags.add("#" + candidate);
                    break;
                }
            }
        }
        if (hashtags.isEmpty()) {
            hashtags.add("#" + provider);
        }
        return hashtags.toArray(String[]::new);
    }

    private static String match(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String fallbackText(String prompt, String fingerprint) {
        if (prompt.contains("Platform: TWITTER")) {
            return "AI destekli içerik planlamayla daha tutarlı paylaşımlar üretin. " + fingerprint;
        }
        if (prompt.contains("Content type: REEL")) {
            return "Fikrinizi saniyeler içinde dikkat çekici bir Reele dönüştürün. " + fingerprint;
        }
        if (prompt.contains("Platform: INSTAGRAM")) {
            return "Markanızın hikâyesini yaratıcı ve etkileyici içeriklerle paylaşın. " + fingerprint;
        }
        return "İçerik üretim sürecinizi yapay zekâ ile planlı, tutarlı ve verimli hale getirin. "
                + fingerprint;
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record MockTextOutput(String text, String[] hashtags) {
    }
}
