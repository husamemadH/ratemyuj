package com.ratemyuj.moderation;

import com.ratemyuj.config.OpenRouterProperties;
import com.ratemyuj.domain.ModerationVerdict;
import com.ratemyuj.moderation.JevMessages.Answer;
import com.ratemyuj.moderation.JevMessages.DecisionsResponse;
import com.ratemyuj.moderation.JevMessages.Question;
import com.ratemyuj.moderation.JevMessages.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    private static final double ESCALATE_BELOW = 0.6;
    private static final double CATEGORY_THRESHOLD = 0.5;
    private static final String MODEL = "typesafe/jev-1.13-20260917";

    private static final String[] CATEGORY_KEYS = {
            "profanity", "personal_attack", "discrimination", "unsubstantiated_accusation",
            "private_info", "not_constructive", "off_topic"
    };

    @Mock
    private JevClient client;

    private ModerationService service;

    @BeforeEach
    void setUp() {
        OpenRouterProperties props = new OpenRouterProperties(
                "test-key", "https://openrouter.test", "/api/alpha/decisions",
                "typesafe/jev-1.13", 12, ESCALATE_BELOW, CATEGORY_THRESHOLD);
        service = new ModerationService(client, props);
    }

    private Answer choice(String value, Double confidence) {
        return new Answer("choice", value, null, null, confidence, Map.of(), Map.of());
    }

    private Answer noul(Double probability) {
        return new Answer("noul", null, probability, null, null, Map.of(), Map.of());
    }

    private Map<String, Answer> allCategories(double probability) {
        Map<String, Answer> categories = new LinkedHashMap<>();
        for (String key : CATEGORY_KEYS) {
            categories.put(key, noul(probability));
        }
        return categories;
    }

    private void jevReturns(String verdict, Double confidence, Map<String, Answer> categories) {
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put("verdict", choice(verdict, confidence));
        answers.putAll(categories);
        when(client.decide(anyMap(), anyMap())).thenReturn(
                new DecisionsResponse("gen-dec-1", MODEL, "TypeSafe", answers,
                        new Usage(120, 24, 0.00002)));
    }

    @Nested
    @DisplayName("verdict mapping")
    class VerdictMapping {

        @Test
        @DisplayName("APPROVE with high confidence and no flags publishes")
        void approve() {
            jevReturns("APPROVE", 0.97, allCategories(0.03));

            var result = service.moderate("Great lectures, fair exams", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.APPROVE);
            assertThat(result.getFlaggedCategories()).isEmpty();
            assertThat(result.getStudentFeedback()).isNull();
            assertThat(result.getModel()).isEqualTo(MODEL);
            assertThat(result.getConfidence()).isEqualTo(0.97);
            assertThat(result.getInternalReason()).contains("APPROVE").contains("personal_attack=0.03");
        }

        @Test
        @DisplayName("REJECT carries the flagged category and a same-language template")
        void reject() {
            jevReturns("REJECT", 0.92,
                    Map.of("personal_attack", noul(0.93), "profanity", noul(0.11)));

            var result = service.moderate("he is an idiot", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.REJECT);
            assertThat(result.getFlaggedCategories()).containsExactly("PERSONAL_ATTACK");
            assertThat(result.getStudentFeedback()).contains("teaching");
            assertThat(result.getStudentFeedback()).doesNotContain("idiot");
        }

        @Test
        @DisplayName("an Arabic comment gets Arabic student feedback")
        void arabicFeedback() {
            jevReturns("REJECT", 0.92, Map.of("personal_attack", noul(0.9)));

            var result = service.moderate("هذا الدكتور أسلوبه سيء جداً", "د. خالد", "CS101");

            assertThat(result.getStudentFeedback()).contains("الرجاء");
        }

        @Test
        @DisplayName("multiple flags are ordered by probability, highest first")
        void flagOrdering() {
            jevReturns("REJECT", 0.95,
                    Map.of("personal_attack", noul(0.9), "profanity", noul(0.96)));

            var result = service.moderate("blah", "Dr. X", "CS101");

            assertThat(result.getFlaggedCategories()).containsExactly("PROFANITY", "PERSONAL_ATTACK");
        }

        @Test
        @DisplayName("explicit ESCALATE is preserved")
        void escalate() {
            jevReturns("ESCALATE", 0.5, allCategories(0.2));

            var result = service.moderate("sure, 'best' teacher ever", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getStudentFeedback()).contains("checked");
        }

        @Test
        @DisplayName("an unknown choice string becomes ESCALATE")
        void unknownVerdict() {
            jevReturns("MAYBE", 0.9, allCategories(0.1));

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }
    }

    @Nested
    @DisplayName("fail-safe behavior — a review is never published on failure or contradiction")
    class FailSafe {

        @Test
        @DisplayName("client failure (network/timeout) escalates with MODERATION_UNAVAILABLE")
        void clientFailure() {
            when(client.decide(anyMap(), anyMap()))
                    .thenThrow(new RestClientException("connection timed out"));

            var result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getFlaggedCategories()).containsExactly("MODERATION_UNAVAILABLE");
            assertThat(result.getInternalReason()).contains("timed out");
        }

        @Test
        @DisplayName("APPROVE contradicted by a category above the threshold escalates")
        void approveWithFlagEscalates() {
            jevReturns("APPROVE", 0.95,
                    Map.of("profanity", noul(0.9), "personal_attack", noul(0.1)));

            var result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getFlaggedCategories()).containsExactly("PROFANITY");
        }

        @Test
        @DisplayName("REJECT with no category above the threshold escalates")
        void rejectWithoutFlagEscalates() {
            jevReturns("REJECT", 0.95, allCategories(0.49));

            var result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getFlaggedCategories()).isEmpty();
        }

        @Test
        @DisplayName("low-confidence APPROVE escalates for a human look")
        void lowConfidenceApprove() {
            jevReturns("APPROVE", 0.4, allCategories(0.05));

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }

        @Test
        @DisplayName("confidence exactly at the threshold is NOT escalated")
        void confidenceAtThreshold() {
            jevReturns("APPROVE", ESCALATE_BELOW, allCategories(0.05));

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.APPROVE);
        }

        @Test
        @DisplayName("a missing confidence value escalates")
        void missingConfidence() {
            jevReturns("APPROVE", null, allCategories(0.05));

            assertThat(service.moderate("text", "Dr. X", "CS101").getVerdict())
                    .isEqualTo(ModerationVerdict.ESCALATE);
        }

        @Test
        @DisplayName("a missing verdict answer escalates instead of approving")
        void missingVerdictAnswer() {
            when(client.decide(anyMap(), anyMap())).thenReturn(
                    new DecisionsResponse("gen-dec-1", MODEL, "TypeSafe",
                            Map.of("profanity", noul(0.01)), new Usage(10, 1, 0.0)));

            var result = service.moderate("text", "Dr. X", "CS101");

            assertThat(result.getVerdict()).isEqualTo(ModerationVerdict.ESCALATE);
            assertThat(result.getFlaggedCategories()).containsExactly("MODERATION_UNAVAILABLE");
        }
    }

    @Test
    @DisplayName("sends one choice question plus one noul per policy category, with the comment as state")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void requestShape() {
        jevReturns("APPROVE", 0.99, allCategories(0.01));

        service.moderate("Lectures are clear", "Dr. X", "CS101");

        ArgumentCaptor<Map> questions = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> state = ArgumentCaptor.forClass(Map.class);
        verify(client).decide(state.capture(), questions.capture());

        assertThat(questions.getValue()).hasSize(8);
        assertThat(((Question) questions.getValue().get("verdict")).type()).isEqualTo("choice");
        assertThat(((Question) questions.getValue().get("profanity")).type()).isEqualTo("noul");
        assertThat(state.getValue().get("student_comment")).isEqualTo("Lectures are clear");
        assertThat(state.getValue().get("professor_name")).isEqualTo("Dr. X");
        assertThat(state.getValue().get("course_code")).isEqualTo("CS101");
    }
}
