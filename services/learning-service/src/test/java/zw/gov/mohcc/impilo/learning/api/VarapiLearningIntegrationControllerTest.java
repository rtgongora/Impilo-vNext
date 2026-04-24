package zw.gov.mohcc.impilo.learning.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCompletionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VarapiLearningIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearningCompletionRepository completionRepository;

    @Test
    void ingest_records_completion_when_key_ok() throws Exception {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String body =
                """
                {
                  "tenantId": "%s",
                  "providerPublicId": "prov-test-1",
                  "councilId": 9,
                  "courseId": "course-x",
                  "courseName": "Test course",
                  "completedAt": "%s",
                  "creditsSuggested": 2,
                  "externalRef": "ext-ref-integration-1",
                  "varapiFundoCandidateId": 4242
                }
                """
                        .formatted(tenant, Instant.parse("2026-01-15T10:00:00Z"));

        mockMvc.perform(
                        post("/internal/v1/learning/integrations/varapi-fundo-completion")
                                .header(VarapiLearningIntegrationController.INTERNAL_KEY_HEADER, "test-learning-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recorded"));

        boolean exists =
                completionRepository.existsByTenantIdAndSourceTypeAndSourceRef(tenant, "FUNDO_WEBHOOK", "ext-ref-integration-1");
        org.assertj.core.api.Assertions.assertThat(exists).isTrue();
    }

    @Test
    void ingest_rejects_bad_key() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/learning/integrations/varapi-fundo-completion")
                                .header(VarapiLearningIntegrationController.INTERNAL_KEY_HEADER, "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
