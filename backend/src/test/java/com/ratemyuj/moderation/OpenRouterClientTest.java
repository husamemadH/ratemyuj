package com.ratemyuj.moderation;

import com.ratemyuj.config.OpenRouterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Exercises the real HTTP layer against a mock server —
 * request shape, fallback ordering, and failure propagation.
 */
class OpenRouterClientTest {

    private static final String BASE = "https://openrouter.test/api/v1";

    private MockRestServiceServer server;
    private OpenRouterClient client;

    private OpenRouterProperties props(String fallback) {
        return new OpenRouterProperties("key", BASE, "primary/model", fallback, 12, 0.6);
    }

    private void build(String fallbackModel) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenRouterClient(builder.build(), props(fallbackModel));
    }

    @BeforeEach
    void setUp() {
        build("fallback/model");
    }

    private String chatResponse(String model, String content) {
        return """
                {"id":"gen-1","model":"%s",
                 "choices":[{"message":{"role":"assistant","content":"%s"}}]}""".formatted(model, content);
    }

    @Test
    @DisplayName("sends the primary model, temperature 0, and both prompt messages")
    void happyPathRequestShape() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("primary/model"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(chatResponse("primary/model", "{}"), MediaType.APPLICATION_JSON));

        OpenRouterClient.CompletionResult result = client.complete("system prompt", "user prompt");

        assertThat(result.content()).isEqualTo("{}");
        assertThat(result.model()).isEqualTo("primary/model");
        server.verify();
    }

    @Test
    @DisplayName("primary 5xx -> retries once with the fallback model")
    void fallbackOnPrimaryFailure() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("primary/model"))
                .andRespond(withServerError());
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("fallback/model"))
                .andRespond(withSuccess(chatResponse("fallback/model", "{}"), MediaType.APPLICATION_JSON));

        OpenRouterClient.CompletionResult result = client.complete("s", "u");

        assertThat(result.model()).isEqualTo("fallback/model");
        server.verify();
    }

    @Test
    @DisplayName("an empty choices array also triggers the fallback")
    void emptyChoicesTriggersFallback() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"id\":\"gen\",\"model\":\"primary/model\",\"choices\":[]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("fallback/model"))
                .andRespond(withSuccess(chatResponse("fallback/model", "{}"), MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u").model()).isEqualTo("fallback/model");
        server.verify();
    }

    @Test
    @DisplayName("both models failing propagates the exception (caller escalates)")
    void bothModelsFailing() {
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    @DisplayName("no fallback configured -> primary failure propagates after one attempt")
    void noFallbackConfigured() {
        build("");
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(RestClientException.class);
        server.verify(); // exactly one request expected — verify fails if a second was sent
    }
}
