package de.marinic.promptlib.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real adapter for an OpenAI-compatible chat-completions API. Only active on the "real"
 * profile - never in tests or default dev runs, so nothing here needs a live API key to
 * exist. Structurally correct, but this project has never actually called a live endpoint
 * with it; treat it as a starting point, not a battle-tested client.
 */
@Component
@Profile("real")
public class RealLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;

    public RealLlmClient(RestClient.Builder restClientBuilder, LlmProperties properties) {
        this.properties = properties;
        this.restClient =
                restClientBuilder
                        .baseUrl(properties.baseUrl())
                        .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                        .build();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        long start = System.currentTimeMillis();
        try {
            String model = request.model() != null ? request.model() : properties.defaultModel();
            ChatCompletionResponse response =
                    restClient
                            .post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ChatCompletionRequest(model, List.of(new ChatMessage("user", request.prompt()))))
                            .retrieve()
                            .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new LlmException("LLM API returned no choices");
            }

            long latency = System.currentTimeMillis() - start;
            String output = response.choices().getFirst().message().content();
            return new LlmResult(
                    output,
                    response.usage() != null ? response.usage().promptTokens() : 0,
                    response.usage() != null ? response.usage().completionTokens() : 0,
                    latency);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM API call failed: " + e.getMessage(), e);
        }
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages) {}

    private record ChatMessage(String role, String content) {}

    private record ChatCompletionResponse(List<Choice> choices, Usage usage) {}

    private record Choice(ChatMessage message) {}

    private record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens) {}
}
