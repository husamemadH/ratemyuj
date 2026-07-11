package com.ratemyuj.moderation;

import com.ratemyuj.config.OpenRouterProperties;
import com.ratemyuj.moderation.OpenRouterMessages.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the OpenRouter /chat/completions endpoint.
 * Tries the primary model, falls back to the configured fallback model once.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final RestClient client;
    private final OpenRouterProperties props;

    public OpenRouterClient(RestClient openRouterRestClient, OpenRouterProperties props) {
        this.client = openRouterRestClient;
        this.props = props;
    }

    /** @return the raw text content of the first choice, plus the model that produced it */
    public CompletionResult complete(String systemPrompt, String userPrompt) {
        try {
            return callModel(props.model(), systemPrompt, userPrompt);
        } catch (RestClientException | IllegalStateException primaryFailure) {
            log.warn("Primary moderation model '{}' failed: {}", props.model(), primaryFailure.getMessage());
            if (props.fallbackModel() == null || props.fallbackModel().isBlank()) {
                throw primaryFailure;
            }
            return callModel(props.fallbackModel(), systemPrompt, userPrompt);
        }
    }

    private CompletionResult callModel(String model, String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                model,
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)
                ),
                0.0,   // deterministic judging
                500,
                Map.of("type", "json_object")
        );

        ChatResponse response = client.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new IllegalStateException("Empty response from OpenRouter model " + model);
        }
        return new CompletionResult(response.choices().get(0).message().content(), response.model());
    }

    public record CompletionResult(String content, String model) {}
}
