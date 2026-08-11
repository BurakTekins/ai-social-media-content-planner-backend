package com.globalcodelabs.socialmediaplanner.infrastructure.aimodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelData;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ModelsDevClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retriesWhenCatalogResponseBodyIsInterrupted() {
        URI apiUri = URI.create("https://models.dev/api.json");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(apiUri))
                .andRespond(withException(new IOException("closed")));
        server.expect(requestTo(apiUri))
                .andRespond(withSuccess(validCatalog(), MediaType.APPLICATION_JSON));
        ModelsDevClient client = new ModelsDevClient(
                restClientBuilder.build(),
                apiUri,
                3,
                Duration.ZERO
        );

        List<ModelData> models = client.fetchAll();

        assertThat(models).hasSize(9);
        server.verify();
    }

    @Test
    void parsesEverySupportedOutputCapabilityForEachModel() throws Exception {
        JsonNode response = objectMapper.readTree(validCatalog());

        List<ModelData> models = ModelsDevClient.parseCatalog(response);

        assertThat(models).hasSize(9);
        assertThat(models)
                .extracting(ModelData::providerName)
                .contains(
                        "openai",
                        "anthropic",
                        "gemini",
                        "deepseek",
                        "qwen"
                );
        assertThat(models)
                .filteredOn(model -> model.providerName().equals("gemini"))
                .hasSize(5);
        assertThat(find(models, "gemini", "gemini-image", AiCapability.TEXT))
                .isNotNull();
        assertThat(find(models, "gemini", "gemini-image", AiCapability.IMAGE))
                .isNotNull();
        assertThat(find(models, "gemini", "veo-video", AiCapability.TEXT))
                .isNotNull();
        assertThat(find(models, "gemini", "veo-video", AiCapability.IMAGE))
                .isNotNull();
        assertThat(find(models, "gemini", "veo-video", AiCapability.VIDEO))
                .isNotNull();
        assertThat(models)
                .noneMatch(model -> model.modelId().equals("gemini-audio"));
        assertThat(find(models, "openai", "gpt-text", AiCapability.TEXT)
                .rawMetadata().path("family").asText())
                .isEqualTo("gpt");
    }

    @Test
    void rejectsCatalogWhenARequiredProviderIsMissing() throws Exception {
        JsonNode response = objectMapper.readTree(validCatalog());
        ((com.fasterxml.jackson.databind.node.ObjectNode) response).remove("anthropic");

        assertThatThrownBy(() -> ModelsDevClient.parseCatalog(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anthropic");
    }

    @Test
    void rejectsCatalogWhenOutputModalitiesChangeShape() throws Exception {
        JsonNode response = objectMapper.readTree(validCatalog());
        ((com.fasterxml.jackson.databind.node.ObjectNode) response
                .path("openai")
                .path("models")
                .path("gpt-text")
                .path("modalities"))
                .put("output", "text");

        assertThatThrownBy(() -> ModelsDevClient.parseCatalog(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output modalities");
    }

    private static ModelData find(
            List<ModelData> models,
            String provider,
            String modelId,
            AiCapability capability
    ) {
        return models.stream()
                .filter(model -> model.providerName().equals(provider)
                        && model.modelId().equals(modelId)
                        && model.capability() == capability)
                .findFirst()
                .orElseThrow();
    }

    private static String validCatalog() {
        return """
                {
                  "openai": {
                    "models": {
                      "gpt-text": {
                        "id": "gpt-text",
                        "name": "GPT Text",
                        "family": "gpt",
                        "modalities": {"input": ["text", "image"], "output": ["text"]}
                      }
                    }
                  },
                  "anthropic": {
                    "models": {
                      "claude-text": {
                        "id": "claude-text",
                        "name": "Claude Text",
                        "modalities": {"input": ["text"], "output": ["text"]}
                      }
                    }
                  },
                  "google": {
                    "models": {
                      "gemini-image": {
                        "id": "gemini-image",
                        "name": "Gemini Image",
                        "modalities": {"input": ["text"], "output": ["text", "image", "image"]}
                      },
                      "veo-video": {
                        "id": "veo-video",
                        "name": "Veo Video",
                        "modalities": {"input": ["text"], "output": ["text", "image", "video"]}
                      },
                      "gemini-audio": {
                        "id": "gemini-audio",
                        "name": "Gemini Audio",
                        "modalities": {"input": ["text"], "output": ["audio"]}
                      }
                    }
                  },
                  "deepseek": {
                    "models": {
                      "deepseek-text": {
                        "id": "deepseek-text",
                        "name": "DeepSeek Text",
                        "modalities": {"input": ["text"], "output": ["text"]}
                      }
                    }
                  },
                  "alibaba": {
                    "models": {
                      "qwen-text": {
                        "id": "qwen-text",
                        "name": "Qwen Text",
                        "modalities": {"input": ["text"], "output": ["text"]}
                      }
                    }
                  }
                }
                """;
    }
}
