package zw.gov.mohcc.impilo.learning.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningAiStubProviderTest {

    private final LearningAiStubProvider provider = new LearningAiStubProvider();

    @Test
    void generatesDraftCourseOutline() {
        Map<String, Object> out = provider.generateCourseOutline(Map.of("title", "Nursing registration"));
        assertThat(out.get("title")).isEqualTo("Nursing registration");
        assertThat(out.get("draft")).isEqualTo(true);
        assertThat(out.get("modules")).isInstanceOf(List.class);
    }

    @Test
    void generatesDraftQuiz() {
        Map<String, Object> out = provider.generateQuiz(Map.of("topic", "telemedicine"));
        assertThat(out.get("assessmentType")).isEqualTo("QUIZ");
        assertThat(out.get("draft")).isEqualTo(true);
        assertThat(out.get("questions")).isInstanceOf(List.class);
    }

    @Test
    void providerNameIsStable() {
        assertThat(provider.providerName()).isEqualTo("STUB_SAFE_LOCAL");
    }
}
