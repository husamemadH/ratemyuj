package com.ratemyuj.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Request/response records for the OpenRouter Jev (TypeSafe) Decisions API. */
public final class JevMessages {

    private JevMessages() {}

    public record DecisionsRequest(String model, Map<String, Object> state, Map<String, Question> questions) {}

    public record Question(String type, Object instructions, Object criteria) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionsResponse(
            String id,
            String model,
            String provider,
            Map<String, Answer> answers,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
            String type,
            String choice,                  // choice answers
            Double noul,                    // noul answers: probability of yes
            Double score,                   // score answers
            Double confidence,              // choice/score answers, optional
            Map<String, Double> probabilities,
            Map<String, String> legend
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(int input_tokens, int output_tokens, Double cost) {}
}
