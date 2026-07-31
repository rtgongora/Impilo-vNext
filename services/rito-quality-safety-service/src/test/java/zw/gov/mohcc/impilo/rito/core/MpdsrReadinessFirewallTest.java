package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contaminated-payload boundary test for the MPDSR → facility-readiness firewall.
 * Fails if forbidden review content can leak into the readiness task payload.
 */
class MpdsrReadinessFirewallTest {

    @Test
    void sanitizedPayload_containsOnlyPermittedFields() {
        Map<String, Object> payload = MpdsrReadinessFirewall.buildSanitizedPayload(
                "f1111111-1111-1111-1111-111111111111", "2026-08-15T10:00:00Z");

        assertThat(payload).containsKeys("taskType", "reasonCode", "facilityId", "owningRole", "method");
        assertThat(payload.get("taskType")).isEqualTo("FACILITY_READINESS_REASSESSMENT");
        assertThat(payload.get("reasonCode")).isEqualTo("SCHEDULED_CYCLE");
        assertThat(MpdsrReadinessFirewall.FORBIDDEN_PAYLOAD_KEYS)
                .noneMatch(payload::containsKey);
    }

    @Test
    void contaminatedPayload_failsWhenReviewIdLeaks() {
        Map<String, Object> contaminated = new LinkedHashMap<>(
                MpdsrReadinessFirewall.buildSanitizedPayload(
                        "f1111111-1111-1111-1111-111111111111", null));
        contaminated.put("reviewId", "rev-secret-123");

        assertThatThrownBy(() -> MpdsrReadinessFirewall.assertSanitized(contaminated))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("reviewId");
    }

    @Test
    void contaminatedPayload_failsWhenNarrativeLeaks() {
        Map<String, Object> contaminated = new LinkedHashMap<>(
                MpdsrReadinessFirewall.buildSanitizedPayload(
                        "f1111111-1111-1111-1111-111111111111", null));
        contaminated.put("narrative", "We had no magnesium sulfate that night");

        assertThatThrownBy(() -> MpdsrReadinessFirewall.assertSanitized(contaminated))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("narrative");
    }

    @Test
    void contaminatedPayload_failsWhenDeathDateLeaks() {
        Map<String, Object> contaminated = new LinkedHashMap<>(
                MpdsrReadinessFirewall.buildSanitizedPayload(
                        "f1111111-1111-1111-1111-111111111111", null));
        contaminated.put("deathDate", "2026-07-01");

        assertThatThrownBy(() -> MpdsrReadinessFirewall.assertSanitized(contaminated))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("deathDate");
    }

    @Test
    void contaminatedPayload_failsWhenFindingsNested() {
        Map<String, Object> contaminated = new LinkedHashMap<>(
                MpdsrReadinessFirewall.buildSanitizedPayload(
                        "f1111111-1111-1111-1111-111111111111", null));
        contaminated.put("context", Map.of("findings", "delay in referral"));

        assertThatThrownBy(() -> MpdsrReadinessFirewall.assertSanitized(contaminated))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("findings");
    }
}
