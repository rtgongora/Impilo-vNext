package zw.gov.mohcc.impilo.learning.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningPathEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningPathRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SubjectPathAssignmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningOrchestrationControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearningPathRepository pathRepository;

    @Autowired
    private SubjectPathAssignmentRepository assignmentRepository;

    private UUID pathId;

    @BeforeEach
    void seedPath() {
        assignmentRepository.deleteAll();
        pathRepository.deleteAll();
        LearningPathEntity p = new LearningPathEntity();
        p.setTenantId(TENANT);
        p.setPathCode("ORCH_TEST_PATH");
        p.setTitle("Orchestration test path");
        p.setPathType("ROLE");
        p.setActive(true);
        pathId = pathRepository.save(p).getId();
    }

    @Test
    void assignPath_rejectsBadKey() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/learning/orchestration/assign-path")
                                .header(LearningOrchestrationController.ORCHESTRATION_KEY_HEADER, "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignPath_recordsRow() throws Exception {
        String body =
                """
                {
                  "tenantId": "%s",
                  "subjectType": "PROVIDER_PUBLIC_ID",
                  "subjectId": "prov-orch-1",
                  "pathId": "%s",
                  "assignedByActorId": "reviewer-1",
                  "correlationId": "corr-orch-1",
                  "metadata": {"source": "test"}
                }
                """
                        .formatted(TENANT, pathId);

        mockMvc.perform(
                        post("/internal/v1/learning/orchestration/assign-path")
                                .header(LearningOrchestrationController.ORCHESTRATION_KEY_HEADER, "test-orch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("assigned"));

        boolean exists =
                assignmentRepository.existsByTenantIdAndSubjectTypeAndSubjectIdAndPathId(
                        TENANT, "PROVIDER_PUBLIC_ID", "prov-orch-1", pathId);
        org.assertj.core.api.Assertions.assertThat(exists).isTrue();
    }

    @Test
    void moodleSiteInfo_returns501WhenTokenUnset() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/learning/orchestration/moodle/site-info")
                                .header(LearningOrchestrationController.ORCHESTRATION_KEY_HEADER, "test-orch-key"))
                .andExpect(status().isNotImplemented());
    }
}
