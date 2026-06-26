package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;

/** A3 — assignment create → submit → marking queue → mark, end-to-end against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoAssignmentIT {

    private static final UUID TENANT = UUID.fromString("a3a3a3a3-0000-4000-8000-000000000001");

    @Autowired private FundoAssignmentService assignments;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void assignmentLifecycle() {
        Map<String, Object> a = assignments.createAssignment(TENANT, "trainer", Map.of(
                "title", "Care plan write-up", "maxScore", 100, "allowLate", true));
        String assignmentId = (String) a.get("id");

        // submit twice → submission_no increments
        assignments.submit(TENANT, assignmentId, Map.of(
                "subjectType", "PROVIDER", "subjectId", "PRV-1", "bodyText", "First attempt"));
        Map<String, Object> sub2 = assignments.submit(TENANT, assignmentId, Map.of(
                "subjectType", "PROVIDER", "subjectId", "PRV-1", "bodyText", "Second attempt"));
        assertThat(sub2.get("submissionNo")).isEqualTo(2);

        List<Map<String, Object>> subs = (List<Map<String, Object>>) assignments.listSubmissions(TENANT, assignmentId).get("items");
        assertThat(subs).hasSize(2);

        // both pending in the marking queue
        List<Map<String, Object>> queue = (List<Map<String, Object>>) assignments.markingQueue(TENANT, null, 100).get("items");
        assertThat(queue).hasSize(2);

        // mark the second submission
        String submissionId = (String) sub2.get("id");
        Map<String, Object> marked = assignments.mark(TENANT, submissionId, "marker-1", Map.of(
                "score", 82, "passed", true, "feedbackText", "Good differential."));
        assertThat(marked.get("markingStatus")).isEqualTo("MARKED");
        assertThat(marked.get("score")).isEqualTo(82);
        assertThat(marked.get("passed")).isEqualTo(true);
        assertThat(marked.get("markerId")).isEqualTo("marker-1");

        // queue now has one
        List<Map<String, Object>> queue2 = (List<Map<String, Object>>) assignments.markingQueue(TENANT, null, 100).get("items");
        assertThat(queue2).hasSize(1);
    }
}
