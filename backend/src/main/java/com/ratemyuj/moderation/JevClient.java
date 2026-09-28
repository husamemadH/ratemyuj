package com.ratemyuj.moderation;

import com.ratemyuj.config.OpenRouterProperties;
import com.ratemyuj.moderation.JevMessages.DecisionsRequest;
import com.ratemyuj.moderation.JevMessages.DecisionsResponse;
import com.ratemyuj.moderation.JevMessages.Question;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin wrapper over the OpenRouter Decisions API, served by Jev.
 *
 * Jev is a System One decision model, not a chat model: it answers typed
 * questions and returns probabilities instead of generated text.
 */
@Component
public class JevClient {

    private final RestClient client;
    private final OpenRouterProperties props;

    public JevClient(RestClient openRouterRestClient, OpenRouterProperties props) {
        this.client = openRouterRestClient;
        this.props = props;
    }

    public DecisionsResponse decide(Map<String, Object> state, Map<String, Question> questions) {
        DecisionsRequest request = new DecisionsRequest(props.model(), state, questions);
        DecisionsResponse response = client.post()
                .uri(props.decisionsPath())
                .body(request)
                .retrieve()
                .body(DecisionsResponse.class);

        if (response == null || response.answers() == null || response.answers().isEmpty()) {
            throw new IllegalStateException("Empty decisions response from Jev model " + props.model());
        }
        return response;
    }
}
