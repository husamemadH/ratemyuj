package com.ratemyuj.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Request/response records for the OpenRouter chat completions API. */
public final class OpenRouterMessages {

    private OpenRouterMessages() {}

    public record ChatMessage(String role, String content) {}

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            int max_tokens,
            Map<String, Object> response_format  // {"type":"json_object"} where supported
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(String id, String model, List<Choice> choices) {}
}
