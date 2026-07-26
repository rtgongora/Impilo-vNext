package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structured history — social, family, functional, procedures and advance directives.
 *
 * <p>This vertical carried a {@code GOLDEN_PATH_DEMO_PATIENT} fixture on a production clinical
 * path. It was <em>unreachable</em>: the PCT endpoints it calls do not exist, so the 404 throws and
 * the honest 502 fires before the fallback is reached. That is exactly why it was dangerous — it
 * would have armed itself the moment those endpoints were built and one of them returned an empty
 * list, injecting demo tobacco history into a patient who genuinely had none recorded. Removed
 * before the wave that would have armed it.</p>
 *
 * <p>"No advance directive" is among the most consequential affirmative claims this system can
 * make, and it must never come from a fixture or from a failed read.</p>
 */
class StructuredHistoryHonestyTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PATIENT_ID = "a1000000-0000-4000-8000-000000000001";
    private static final Path SOURCE = Path.of(
            "src/main/java/zw/gov/mohcc/impilo/experience/controller/StructuredHistoryController.java");

    @Test
    void anUnreachableRecordIsReportedAsUnavailableNotAsAnAbsentHistory() {
        StructuredHistoryController controller = new StructuredHistoryController(
                mapper, new DownPctClient(), new InpatientServiceClient(
                        new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper));

        for (ResponseEntity<Map<String, Object>> response : List.of(
                controller.socialHistory("t", "req", "corr", PATIENT_ID),
                controller.familyHistory("t", "req", "corr", PATIENT_ID),
                controller.functionalAssessments("t", "req", "corr", PATIENT_ID),
                controller.advanceDirectives("t", "req", "corr", PATIENT_ID))) {
            assertEquals(502, response.getStatusCode().value());
            assertTrue(String.valueOf(response.getBody().get("message")).toLowerCase()
                            .contains("do not treat this as an absence"),
                    "a failed read must say so rather than imply an empty history");
        }
    }

    /**
     * The transitional in-development notice is GONE, because the capability is complete: PCT now
     * serves /v1/ehr/** from pct_structured_history (V106). Under the product-owner rule the notice
     * was only ever acceptable while the backing wave was named and outstanding — leaving it in
     * place after completion would advertise incompleteness that no longer exists.
     */
    @Test
    void noLongerCarriesAnInDevelopmentNoticeBecauseTheCapabilityIsComplete() {
        StructuredHistoryController controller = new StructuredHistoryController(
                mapper, new DownPctClient(), new InpatientServiceClient(
                        new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper));
        assertTrue(!controller.socialHistory("t", "req", "corr", PATIENT_ID)
                .getBody().containsKey("in_development"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retiredInDevelopmentNoticeCheck() {
        StructuredHistoryController controller = new StructuredHistoryController(
                mapper, new DownPctClient(), new InpatientServiceClient(
                        new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper));

        // Superseded by the test above; the capability is complete.
    }

    /**
     * The guard that matters going forward. W2 builds the PCT endpoints this controller calls, at
     * which point a genuine empty answer becomes reachable for the first time — and any fixture
     * left in the fallback would start firing against real patients.
     */
    @Test
    void carriesNoDemoFixtureOnTheProductionPath() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(!source.contains("GOLDEN_PATH_DEMO_PATIENT"),
                "demo data must not sit on a clinical path — it arms itself when the real "
                + "endpoint lands and returns an empty list");
        assertTrue(!source.contains("demoSocialHistory") && !source.contains("demoAdvanceDirectives"),
                "demo fixtures must not be reachable from a clinical response");
    }

    private static final class DownPctClient extends PctServiceClient {
        private static final RuntimeException DOWN = new IllegalStateException("pct unreachable");

        DownPctClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        @Override public JsonNode getSocialHistory(String cpid) { throw DOWN; }
        @Override public JsonNode getFamilyHistory(String cpid) { throw DOWN; }
        @Override public JsonNode getFunctionalAssessments(String cpid) { throw DOWN; }
        @Override public JsonNode getAdvanceDirectives(String cpid) { throw DOWN; }
    }
}
