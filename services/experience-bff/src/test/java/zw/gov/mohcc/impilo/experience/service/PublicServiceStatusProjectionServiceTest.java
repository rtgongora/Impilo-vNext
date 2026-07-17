package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Projection honesty tests for the citizen service-status board. The doctrine crux: the board
 * must NEVER fabricate OPERATIONAL for an unmonitored or absent group, and an unreachable
 * heartbeat source must degrade to {@code live:false} / all-UNKNOWN rather than green.
 */
class PublicServiceStatusProjectionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PublicServiceStatusProjectionService projection =
            new PublicServiceStatusProjectionService();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> groupNamed(Map<String, Object> result, String label) {
        for (Object g : (List<Object>) result.get("groups")) {
            Map<String, Object> group = (Map<String, Object>) g;
            if (label.equals(group.get("group"))) {
                return group;
            }
        }
        throw new AssertionError("group not found: " + label);
    }

    private JsonNode feed(String generatedAt, Map<String, String> serviceStatuses) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("generatedAt", generatedAt);
        root.put("staleThresholdMinutes", 5);
        ArrayNode services = root.putArray("services");
        serviceStatuses.forEach((name, status) -> {
            ObjectNode s = services.addObject();
            s.put("serviceName", name);
            s.put("status", status);
            s.put("lastHeartbeat", generatedAt);
        });
        return root;
    }

    @Test
    void observabilityDown_livesFalse_allGroupsUnknownNotOperational() {
        Map<String, Object> result = projection.project(null);

        assertThat(result.get("live")).isEqualTo(false);
        assertThat(result.get("updatedAt")).asString().isNotBlank();
        for (Object g : (List<?>) result.get("groups")) {
            Map<?, ?> group = (Map<?, ?>) g;
            assertThat(group.get("state")).isEqualTo("UNKNOWN");
            assertThat(group.get("monitored")).isEqualTo(false);
            // Honesty: never operational when we cannot prove it.
            assertThat(group.get("state")).isNotEqualTo("OPERATIONAL");
        }
    }

    @Test
    void groupWithNoMonitoredMembers_isUnknownNotOperational() {
        // Feed carries only daidzai (and NOT experience-bff, which is a member of every group),
        // so "Health records & results" (butano + experience-bff) has zero present members.
        JsonNode feed = feed("2026-07-17T10:00:00Z",
                Map.of("daidzai-service", "UP"));

        Map<String, Object> result = projection.project(feed);
        assertThat(result.get("live")).isEqualTo(true);

        Map<String, Object> records = groupNamed(result, "Health records & results");
        assertThat(records.get("monitored")).isEqualTo(false);
        assertThat(records.get("state")).isEqualTo("UNKNOWN");
    }

    @Test
    void allMembersPresentAndUp_isOperational() {
        JsonNode feed = feed("2026-07-17T10:00:00Z",
                Map.of("daidzai-service", "UP", "experience-bff", "UP"));

        Map<String, Object> emergency = groupNamed(projection.project(feed), "Emergency help");
        assertThat(emergency.get("monitored")).isEqualTo(true);
        assertThat(emergency.get("state")).isEqualTo("OPERATIONAL");
    }

    @Test
    void staleMember_degradesGroup() {
        JsonNode feed = feed("2026-07-17T10:00:00Z",
                Map.of("daidzai-service", "STALE", "experience-bff", "UP"));

        Map<String, Object> emergency = groupNamed(projection.project(feed), "Emergency help");
        assertThat(emergency.get("state")).isEqualTo("DEGRADED");
        assertThat(emergency.get("monitored")).isEqualTo(true);
    }

    @Test
    void someMembersUpButOthersAbsent_isPartialNotOperational() {
        // "Sign in & accounts" = vito + tshepo + experience-bff; only two are present and up.
        JsonNode feed = feed("2026-07-17T10:00:00Z",
                Map.of("vito-service", "UP", "experience-bff", "UP"));

        Map<String, Object> signIn = groupNamed(projection.project(feed), "Sign in & accounts");
        assertThat(signIn.get("monitored")).isEqualTo(true);
        assertThat(signIn.get("state")).isEqualTo("PARTIAL");
        assertThat(signIn.get("state")).isNotEqualTo("OPERATIONAL");
    }

    @Test
    void response_carriesNoInternalServiceNames() {
        JsonNode feed = feed("2026-07-17T10:00:00Z",
                Map.of("vito-service", "UP", "tuso-service", "UP"));
        String serialized = String.valueOf(projection.project(feed));
        for (String internal : List.of("tuso", "vito", "tshepo", "butano", "daidzai",
                "varapi", "ndila", "costa", "mushe", "rito", "participation")) {
            assertThat(serialized.toLowerCase()).doesNotContain(internal);
        }
    }
}
