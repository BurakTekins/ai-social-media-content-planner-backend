package com.globalcodelabs.socialmediaplanner.infrastructure.ai.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockAiProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockAiProviderClient client;

    @BeforeEach
    void setUp() {
        ApiCredentialService credentialService = mock(ApiCredentialService.class);
        when(credentialService.resolveActive(any(), any())).thenReturn(null);
        client = new MockAiProviderClient(objectMapper, credentialService);
    }

    @Test
    void shouldBuildTextAndHashtagsFromSourceContext() throws Exception {
        JsonNode output = generateText(prompt("LINKEDIN", 1, "BENEFIT_FOCUSED"));

        assertThat(output.path("text").asText())
                .contains("NovaDesk")
                .contains("15-19 Eylul 2026")
                .doesNotContain("AI destekli içerik planlamayla");
        assertThat(output.path("hashtags")).extracting(JsonNode::asText)
                .containsExactly("#NovaDesk", "#OdakHaftasi", "#VerimliCalisma", "#HibritCalisma");
    }

    @Test
    void shouldRotateSourceSentenceAndAngleForDistinctContent() throws Exception {
        JsonNode first = generateText(prompt("LINKEDIN", 1, "BENEFIT_FOCUSED"));
        JsonNode second = generateText(prompt("LINKEDIN", 2, "QUESTION_LED"));

        assertThat(first.path("text").asText()).isNotEqualTo(second.path("text").asText());
        assertThat(first.path("text").asText()).contains("NovaDesk");
        assertThat(second.path("text").asText())
                .contains("NovaDesk")
                .contains("25 dakikalik");
    }

    @Test
    void shouldLimitTwitterHashtagsAndTextLength() throws Exception {
        JsonNode output = generateText(prompt("TWITTER", 1, "FACT_LED"));

        assertThat(output.path("text").asText()).hasSizeLessThanOrEqualTo(190);
        assertThat(output.path("hashtags")).hasSize(3);
    }

    @Test
    void shouldPreserveCurrentDraftSubjectDuringTextRegeneration() throws Exception {
        JsonNode output = generateText("""
                Regenerate the following draft as a distinct alternative while preserving its subject.
                Platform: LINKEDIN
                Content type: POST
                Current text:
                Example Domain is intended for documentation examples.
                Current hashtags:
                #ExampleDomain #Documentation
                """);

        assertThat(output.path("text").asText())
                .contains("Example Domain")
                .doesNotContain("#ExampleDomain", "#Documentation");
        assertThat(output.path("hashtags")).extracting(JsonNode::asText)
                .containsExactly("#ExampleDomain", "#Documentation");
    }

    @Test
    void shouldKeepMediaOutputsDeterministicAndProviderSpecific() {
        AiGenerationResult image = client.generate(new AiGenerationRequest(
                "gemini", AiCapability.IMAGE, "Create an image", "mock-image"
        ));
        AiGenerationResult video = client.generate(new AiGenerationRequest(
                "gemini", AiCapability.VIDEO, "Create a video", "mock-video", 8
        ));

        assertThat(image.output()).startsWith("mock://gemini/image/").endsWith(".png");
        assertThat(video.output()).startsWith("mock://gemini/video/").endsWith(".mp4");
    }

    private JsonNode generateText(String prompt) throws Exception {
        AiGenerationResult result = client.generate(new AiGenerationRequest(
                "gemini", AiCapability.TEXT, prompt, "mock-text"
        ));
        return objectMapper.readTree(result.output());
    }

    private static String prompt(String platform, int contentNumber, String angle) {
        return """
                Generate one distinct social media content item as valid JSON.
                Platform: %s
                Content type: POST
                Content number: %d of 2
                Creative angle: %s
                Source context:
                Primary source:
                [Source 1 | type=DOCUMENT | id=test]
                NovaDesk Odak Haftasi kampanyasi 15-19 Eylul 2026 tarihleri arasinda duzenlenir.
                Program her gun 25 dakikalik bir odak oturumu sunar.
                Programi tamamlayan katilimcilar NovaDesk Pro yillik planinda yuzde 20 indirim kazanir.
                Kayit adresi: https://novadesk.example/odak-haftasi
                #NovaDesk #OdakHaftasi #VerimliCalisma #HibritCalisma
                """.formatted(platform, contentNumber, angle);
    }
}
