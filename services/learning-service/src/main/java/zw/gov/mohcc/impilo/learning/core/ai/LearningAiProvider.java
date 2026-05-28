package zw.gov.mohcc.impilo.learning.core.ai;

import java.util.Map;

/**
 * Provider-agnostic AI interface for native Fundo authoring.
 *
 * <p>Default deployments use the local stub implementation. Real providers
 * can be plugged in later without changing endpoint contracts.</p>
 */
public interface LearningAiProvider {

    String providerName();

    Map<String, Object> generateCourseOutline(Map<String, Object> request);

    Map<String, Object> generateLessonContent(Map<String, Object> request);

    Map<String, Object> generateQuiz(Map<String, Object> request);

    Map<String, Object> generateSummary(Map<String, Object> request);

    Map<String, Object> generateSurvey(Map<String, Object> request);

    Map<String, Object> generateFacilitatorGuide(Map<String, Object> request);

    Map<String, Object> generateVoiceoverScript(Map<String, Object> request);
}
