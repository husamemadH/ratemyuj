package com.ratemyuj.moderation;

import com.ratemyuj.config.OpenRouterProperties;
import com.ratemyuj.moderation.JevMessages.Answer;
import com.ratemyuj.moderation.JevMessages.DecisionsResponse;
import com.ratemyuj.moderation.JevMessages.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Exercises the real HTTP layer against a mock server — request shape and
 * failure propagation for the Jev Decisions endpoint.
 */
class JevClientTest {

    private static final String BASE = "https://openrouter.test";

    private MockRestServiceServer server;
    private JevClient client;

    @BeforeEach
    void setUp() {
        OpenRouterProperties props = new OpenRouterProperties(
                "key", BASE, "/api/alpha/decisions", "typesafe/jev-1.13", 12, 0.6, 0.5);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JevClient(builder.build(), props);
    }

    private Map<String, Question> questions() {
        return Map.of(
                "verdict", new Question("choice", "Judge this", Map.of("APPROVE", "fine", "REJECT", "bad")),
                "profanity", new Question("noul", "Is it profane?", Map.of("true", "yes", "false", "no")));
    }

    @Test
    @DisplayName("posts model, state, and typed questions to the decisions endpoint")
    void happyPathRequestShape() {
        server.expect(requestTo(BASE + "/api/alpha/decisions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("typesafe/jev-1.13"))
                .andExpect(jsonPath("$.state.student_comment").value("Great lectures"))
                .andExpect(jsonPath("$.questions.verdict.type").value("choice"))
                .andExpect(jsonPath("$.questions.profanity.type").value("noul"))
                .andExpect(jsonPath("$.questions.profanity.criteria.true").value("yes"))
                .andRespond(withSuccess("""
                        {"id":"gen-dec-1","model":"typesafe/jev-1.13-20260917","provider":"TypeSafe",
                         "answers":{
                           "verdict":{"type":"choice","choice":"APPROVE","confidence":0.96,
                                      "probabilities":{"APPROVE":0.96,"REJECT":0.04}},
                           "profanity":{"type":"noul","noul":0.02}},
                         "usage":{"input_tokens":120,"output_tokens":24,"cost":0.00002}}
                        """, MediaType.APPLICATION_JSON));

        DecisionsResponse response = client.decide(
                Map.of("student_comment", "Great lectures"), questions());

        Answer verdict = response.answers().get("verdict");
        assertThat(verdict.choice()).isEqualTo("APPROVE");
        assertThat(verdict.confidence()).isEqualTo(0.96);
        assertThat(verdict.probabilities()).containsEntry("REJECT", 0.04);
        assertThat(response.answers().get("profanity").noul()).isEqualTo(0.02);
        assertThat(response.model()).isEqualTo("typesafe/jev-1.13-20260917");
        assertThat(response.usage().input_tokens()).isEqualTo(120);
        server.verify();
    }

    @Test
    @DisplayName("an empty answers object is a contract failure, not an approval")
    void emptyAnswersFails() {
        server.expect(requestTo(BASE + "/api/alpha/decisions"))
                .andRespond(withSuccess("""
                        {"id":"gen-dec-1","model":"typesafe/jev-1.13","answers":{},
                         "usage":{"input_tokens":1,"output_tokens":0,"cost":0}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.decide(Map.of("student_comment", "x"), questions()))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    @DisplayName("a server error propagates (the caller escalates)")
    void serverErrorPropagates() {
        server.expect(requestTo(BASE + "/api/alpha/decisions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.decide(Map.of("student_comment", "x"), questions()))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }
}
