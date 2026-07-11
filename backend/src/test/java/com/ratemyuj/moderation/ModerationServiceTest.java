package com.ratemyuj.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratemyuj.config.OpenRouterProperties;
import com.ratemyuj.domain.ModerationResult;
import com.ratemyuj.domain.ModerationVerdict;
import com.ratemyuj.moderation.OpenRouterClient.CompletionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    private static final double ESCALATE_BELOW = 0.6;

    @Mock
    private OpenRouterClient client;

    private ModerationService service;

    @BeforeEach
    void setUp() {
        OpenRouterProperties props = new OpenRouterProperties(
                "test-key", "https://openrouter.test/api/v1",
                "primary/model", "fallback/model", 12, ESCALATE_BELOW);
        service = new ModerationService(client, props, new ObjectMapper());
    }

    private void modelReturns(String json) {
        when(client.complete(anyString(), anyString()))
                .thenReturn(new CompletionResult(json, "primary/model"));
    }

    @Nested
    @DisplayName("verdict parsing")
    class VerdictParsing {

        @Test
        @DisplayName("APPROVE with high confidence publishes")
        void approve() {
            modelReturns("""
                    {"verdict":"APPROVE","categories":[],
                     "student_feedback":null,"internal_reason":"constructive","confidence":0.97}""");

            ModerationResult result = service.moderate("Great lectures, fair exams", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.APPROVE);
            assertThat(result.getFlaggedCategories()).isEmpty();
            assertThat(result.getModel()).isEqualTo("primary/model");
            assertThat(result.getConfidence()).isEqualTo(0.97);
        }

        @Test
        @DisplayName("REJECT carries categories and student feedback")
        void reject() {
            modelReturns("""
                    {"verdict":"REJECT","categories":["PERSONAL_ATTACK"],
                     "student_feedback":"Focus on the teaching, not the person.",
                     "internal_reason":"insults the professor","confidence":0.92}""");

            ModerationResult result = service.moderate("he is an idiot", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.REJECT);
            assertThat(result.getFlaggedCategories()).containsExactly("PERSONAL_ATTACK");
            assertThat(result.getStudentFeedback()).contains("teaching");
        }

        @Test
        @DisplayName("explicit ESCALATE is preserved")
        void escalate() {
            modelReturns("""
                    {"verdict":"ESCALATE","categories":[],
                     "student_feedback":"Held for review.","internal_reason":"ambiguous sarcasm","confidence":0.5}""");

            assertThat(service.moderate("sure, 'best' teacher ever", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }

        @Test
        @DisplayName("markdown code fences around the JSON are stripped")
        void stripsFences() {
            modelReturns("""
                    ```json
                    {"verdict":"APPROVE","categories":[],"student_feedback":null,
                     "internal_reason":"fine","confidence":0.9}
                    ```""");

            assertThat(service.moderate("solid course", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.APPROVE);
        }

        @Test
        @DisplayName("unknown verdict string becomes ESCALATE")
        void unknownVerdict() {
            modelReturns("""
                    {"verdict":"MAYBE","categories":[],"student_feedback":null,
                     "internal_reason":"?","confidence":0.9}""");

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }

        @Test
        @DisplayName("null categories are normalized to an empty list")
        void nullCategories() {
            modelReturns("""
                    {"verdict":"APPROVE","student_feedback":null,
                     "internal_reason":"fine","confidence":0.9}""");

            assertThat(service.moderate("text", "Dr. X", "CS101").getFlaggedCategories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("fail-safe behavior — a review is never published on failure")
    class FailSafe {

        @Test
        @DisplayName("unparseable model output escalates instead of approving")
        void garbageOutput() {
            modelReturns("I think this comment is fine to publish!");

            ModerationResult result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getFlaggedCategories()).containsExactly("MODERATION_UNAVAILABLE");
        }

        @Test
        @DisplayName("client failure (network/timeout) escalates")
        void clientFailure() {
            when(client.complete(anyString(), anyString()))
                    .thenThrow(new RestClientException("connection timed out"));

            ModerationResult result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getStudentFeedback()).isNotBlank();
            assertThat(result.getInternalReason()).contains("timed out");
        }

        @Test
        @DisplayName("low-confidence APPROVE is escalated for a human look")
        void lowConfidenceApprove() {
            modelReturns("""
                    {"verdict":"APPROVE","categories":[],"student_feedback":null,
                     "internal_reason":"probably fine","confidence":0.4}""");

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }

        @Test
        @DisplayName("confidence exactly at the threshold is NOT escalated")
        void confidenceAtThreshold() {
            modelReturns("""
                    {"verdict":"APPROVE","categories":[],"student_feedback":null,
                     "internal_reason":"fine","confidence":%s}""".formatted(ESCALATE_BELOW));

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.APPROVE);
        }
    }
}
