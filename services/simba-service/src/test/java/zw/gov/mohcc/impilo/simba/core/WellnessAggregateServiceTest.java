package zw.gov.mohcc.impilo.simba.core;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Aggregate dashboard must expose ONLY counts/rates — never a person_cpid or client-level row. */
class WellnessAggregateServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void dashboard_returnsAggregatesOnly_noCpidLeak() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Every count query returns a scalar long — no row objects with identifiers.
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object.class))).thenReturn(5L);

        WellnessAggregateService svc = new WellnessAggregateService(jdbc);
        Map<String, Object> data = svc.dashboard(TENANT);

        // No client-level identifier leaks (a cpid key or value would be a per-person leak);
        // "enrolled_persons" is a COUNT of distinct persons, not a person identifier.
        assertThat(data.keySet()).noneMatch(k -> k.toLowerCase().contains("cpid"));
        // Every value is a numeric aggregate — never a row/list/identifier string.
        assertThat(data.values()).allMatch(v -> v instanceof Number);
        assertThat(data).containsKeys("active_enrolments", "assessments_completed",
                "high_risk_escalations", "follow_up_completion_pct", "care_linkages_routed");
    }

    @Test
    void pctZeroDenominator_isZeroNotDivideByZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object.class))).thenReturn(0L);
        WellnessAggregateService svc = new WellnessAggregateService(jdbc);
        Map<String, Object> data = svc.dashboard(TENANT);
        assertThat(data.get("screening_coverage_pct")).isEqualTo(0);
        assertThat(data.get("follow_up_completion_pct")).isEqualTo(0);
    }
}
