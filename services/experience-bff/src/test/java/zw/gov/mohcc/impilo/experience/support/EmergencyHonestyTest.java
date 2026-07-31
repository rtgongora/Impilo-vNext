package zw.gov.mohcc.impilo.experience.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergencyHonestyTest {

    /**
     * The property the whole class exists for: a failed read carries no {@code data} key, so there is
     * no empty list for a panel to render as "no alerts".
     */
    @Test
    void aFailedReadCarriesNoDataKeyAtAll() {
        var response = EmergencyHonesty.unavailable(
                "emergency_alerts_unavailable", "emergency alerts", new RuntimeException("connection refused"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).doesNotContainKey("data");
        assertThat(response.getBody()).containsKeys("error", "message", "meta");
        assertThat(response.getBody().get("error")).isEqualTo("emergency_alerts_unavailable");
    }

    @Test
    void everyMessageNamesWhatTheAbsenceIsNot() {
        assertThat(EmergencyHonesty.message("emergency alerts"))
                .isEqualTo("Emergency alerts could not be retrieved. "
                        + "Do not treat this as an absence of emergency alerts.");
    }

    @Test
    void aSubjectlessFailureIsRefused_becauseTheClinicianCouldNotTellWhatWasMissing() {
        assertThatThrownBy(() -> EmergencyHonesty.message("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name its subject");
    }

    @Test
    void theEnvelopeIsFlat_notNestedUnderAnErrorObject() {
        var body = EmergencyHonesty.unavailable("x_unavailable", "x", null).getBody();
        assertThat(body.get("message")).isInstanceOf(String.class);
        assertThat(body.get("error")).isInstanceOf(String.class);
    }

    @Test
    void aCompositeBoardDegrades_itDoesNot502() {
        var response = EmergencyHonesty.composite()
                .put("episodes", List.of(Map.of("episode_id", "E-1")))
                .failed("alerts", "emergency_alerts_unavailable", "emergency alerts",
                        new RuntimeException("connection refused"))
                .build();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsKeys("items", "failures", "meta");

        @SuppressWarnings("unchecked")
        var failures = (List<Map<String, Object>>) response.getBody().get("failures");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("source")).isEqualTo("alerts");
        assertThat((String) failures.get(0).get("message")).contains("Do not treat this as an absence of");

        @SuppressWarnings("unchecked")
        var meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("degraded")).isEqualTo(true);
    }

    @Test
    void aFullyReadableBoardIsNotMarkedDegraded() {
        var composite = EmergencyHonesty.composite().put("episodes", List.of());
        assertThat(composite.isDegraded()).isFalse();

        @SuppressWarnings("unchecked")
        var meta = (Map<String, Object>) composite.build().getBody().get("meta");
        assertThat(meta.get("degraded")).isEqualTo(false);
    }
}
