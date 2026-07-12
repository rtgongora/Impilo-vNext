package zw.gov.mohcc.impilo.guidance.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import zw.gov.mohcc.impilo.guidance.core.GuidanceService;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GatewayEscalationExplainerEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GatewayEscalationExplainerRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicGuidanceControllerTest {

    @Mock
    private GatewayEscalationExplainerRepository explainerRepository;

    @Mock
    private GuidanceService guidanceService;

    @InjectMocks
    private PublicGuidanceController controller;

    private static GatewayEscalationExplainerEntity explainer(String stepKey) {
        GatewayEscalationExplainerEntity e = new GatewayEscalationExplainerEntity();
        e.setStepKey(stepKey);
        e.setPillar("get-care");
        e.setRungFrom("R1");
        e.setRungTo("R2");
        e.setTitle("Sign in to book your visit");
        e.setWhy("Appointments become part of your personal health record.");
        e.setLevelLabel("Sign in with your account or Health ID.");
        e.setWhatNext("Your booking details are saved; you will return to this exact step.");
        e.setHowToGetHelp("Use the recovery options or ask for assisted access.");
        e.setContinueWithoutLabel("Continue without signing in");
        return e;
    }

    @Test
    @SuppressWarnings("unchecked")
    void explainStepReturnsTheFourDoctrinalPartsAndNothingElse() {
        when(explainerRepository.findByTenantIdAndStepKeyAndStatus(
                PublicGuidanceController.PUBLIC_TENANT, "signin-to-book", PublicGuidanceController.ACTIVE))
                .thenReturn(Optional.of(explainer("signin-to-book")));

        var response = controller.explainStep("signin-to-book");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("stepKey")).isEqualTo("signin-to-book");
        assertThat(data.get("why")).asString().contains("personal health record");
        assertThat(data.get("levelLabel")).asString().contains("Health ID");
        assertThat(data.get("whatNext")).asString().contains("exact step");
        assertThat(data.get("howToGetHelp")).asString().contains("assisted access");
        assertThat(data.get("continueWithoutLabel")).isEqualTo("Continue without signing in");
        // Allow-list discipline: exactly the public fields, no entity internals leak.
        assertThat(data.keySet()).containsExactly(
                "stepKey", "pillar", "rungFrom", "rungTo", "title",
                "why", "levelLabel", "whatNext", "howToGetHelp", "continueWithoutLabel");
    }

    @Test
    void explainStepNormalizesTheKeyBeforeLookup() {
        when(explainerRepository.findByTenantIdAndStepKeyAndStatus(
                PublicGuidanceController.PUBLIC_TENANT, "verify-contact", PublicGuidanceController.ACTIVE))
                .thenReturn(Optional.of(explainer("verify-contact")));

        var response = controller.explainStep("  Verify-Contact ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownStepIsAnHonest404NotAFabricatedSurface() {
        when(explainerRepository.findByTenantIdAndStepKeyAndStatus(
                PublicGuidanceController.PUBLIC_TENANT, "no-such-step", PublicGuidanceController.ACTIVE))
                .thenReturn(Optional.empty());

        var response = controller.explainStep("no-such-step");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("EXPLAIN_STEP_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsOnlyActiveExplainers() {
        when(explainerRepository.findByTenantIdAndStatusOrderByStepKeyAsc(
                PublicGuidanceController.PUBLIC_TENANT, PublicGuidanceController.ACTIVE))
                .thenReturn(List.of(explainer("signin-to-book"), explainer("verify-contact")));

        var response = controller.listExplainSteps();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void educationExposesOnlyAllowListedFieldsFromPublishedTopics() {
        // GuidanceService.getEducation already filters status = PUBLISHED (verified V001 path).
        var article = org.mockito.Mockito.mock(
                zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity.class);
        when(article.getId()).thenReturn(java.util.UUID.randomUUID());
        when(article.getTitle()).thenReturn("Handwashing basics");
        when(article.getSummary()).thenReturn("Why and how to wash hands.");
        when(article.getCategory()).thenReturn("prevention");
        when(article.getDomain()).thenReturn("public-health");
        when(guidanceService.getEducation(PublicGuidanceController.PUBLIC_TENANT, "all", 0, 20))
                .thenReturn(new PageImpl<>(List.of(article)));

        var response = controller.education("all", "", 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(1);
        // No source/sourceUrl/content leak on the public lane.
        assertThat(data.get(0).keySet()).containsExactly("id", "title", "summary", "category", "domain");
    }

    @Test
    void educationClampsPageSizeAbuse() {
        when(guidanceService.getEducation(PublicGuidanceController.PUBLIC_TENANT, "all", 0, 50))
                .thenReturn(new PageImpl<>(List.of()));

        var response = controller.education("all", "", -3, 5000);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void educationFiltersByCategoryWithNormalizedSlug() {
        when(guidanceService.getEducationByCategory(
                PublicGuidanceController.PUBLIC_TENANT, "vaccination", 0, 20))
                .thenReturn(new PageImpl<>(List.of()));

        var response = controller.education("all", "  Vaccination ", 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.mockito.Mockito.verify(guidanceService)
                .getEducationByCategory(PublicGuidanceController.PUBLIC_TENANT, "vaccination", 0, 20);
        org.mockito.Mockito.verify(guidanceService, org.mockito.Mockito.never())
                .getEducation(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void articleReadExposesBodyWithAllowListedFieldsOnly() {
        var id = java.util.UUID.randomUUID();
        var article = org.mockito.Mockito.mock(
                zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity.class);
        when(article.getId()).thenReturn(id);
        when(article.getTitle()).thenReturn("Clean hands, safe water, safe food");
        when(article.getSummary()).thenReturn("Everyday habits that prevent diarrhoeal disease.");
        when(article.getContent()).thenReturn("Wash your hands with soap and running water.");
        when(article.getCategory()).thenReturn("prevention-nutrition");
        when(article.getDomain()).thenReturn("public-health");
        when(article.getUpdatedAt()).thenReturn(java.time.OffsetDateTime.parse("2026-07-12T00:00:00Z"));
        when(guidanceService.getPublishedArticle(PublicGuidanceController.PUBLIC_TENANT, id))
                .thenReturn(Optional.of(article));

        var response = controller.educationArticle(id.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("body")).asString().contains("soap and running water");
        // Allow-list discipline: no source/sourceUrl/tenant/relevance leak on the public lane.
        assertThat(data.keySet()).containsExactly(
                "id", "title", "summary", "body", "category", "domain", "updatedAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownOrUnpublishedArticleIsAnHonest404() {
        var id = java.util.UUID.randomUUID();
        when(guidanceService.getPublishedArticle(PublicGuidanceController.PUBLIC_TENANT, id))
                .thenReturn(Optional.empty());

        var response = controller.educationArticle(id.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("EDUCATION_ARTICLE_NOT_FOUND");
    }

    @Test
    void malformedArticleIdIsA404NotAServerError() {
        var response = controller.educationArticle("../not-a-uuid");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(guidanceService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoriesEndpointReturnsPublishedTopicIndex() {
        var vaccination = org.mockito.Mockito.mock(
                zw.gov.mohcc.impilo.guidance.persistence.repository.KnowledgeArticleRepository.CategoryCount.class);
        when(vaccination.getCategory()).thenReturn("vaccination");
        when(vaccination.getCount()).thenReturn(1L);
        var prevention = org.mockito.Mockito.mock(
                zw.gov.mohcc.impilo.guidance.persistence.repository.KnowledgeArticleRepository.CategoryCount.class);
        when(prevention.getCategory()).thenReturn("prevention-nutrition");
        when(prevention.getCount()).thenReturn(2L);
        when(guidanceService.getEducationCategories(PublicGuidanceController.PUBLIC_TENANT))
                .thenReturn(List.of(prevention, vaccination));

        var response = controller.educationCategories();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).keySet()).containsExactly("category", "count");
        assertThat(data.get(1).get("category")).isEqualTo("vaccination");
    }
}
