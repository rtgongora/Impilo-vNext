package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.simba.social.core.ProgrammeEngagementService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Programme-engagement is aggregate-only: it produces counts/rates and NEVER a per-person key.
 * Each metric is a single COUNT query; here we stub the JdbcTemplate to fixed counts and assert the
 * shape + derived completion rate + the absence of any person_cpid in the output.
 */
class ProgrammeEngagementServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000050");
    private static final UUID PROGRAMME = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void dashboardIsAggregateOnlyWithCompletionRate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Return counts based on the SQL text so enrolment/completion produce a known rate.
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            // Most-specific matches first.
            if (sql.contains("care_linkage_id IS NOT NULL")) return 1L;             // escalations
            if (sql.contains("status = 'OPEN'") && sql.contains("simba_content_report")) return 2L; // backlog
            if (sql.contains("simba_content_report")) return 6L;                    // reported content
            if (sql.contains("p.progress_value >= c.target_value")) return 5L;      // completions
            if (sql.contains("challenge_participants")) return 10L;                 // enrolment
            if (sql.contains("COUNT(DISTINCT m.person_cpid)")) return 12L;
            if (sql.contains("official = TRUE")) return 4L;
            if (sql.contains("simba_social_post")) return 20L;                      // posts
            if (sql.contains("simba_community")) return 1L;
            if (sql.contains("simba.simba_group ")) return 3L;                      // groups
            return 0L;
        });

        Map<String, Object> out = new ProgrammeEngagementService(jdbc).dashboard(TENANT, PROGRAMME);

        assertThat(out.get("groups")).isEqualTo(3L);
        assertThat(out.get("communities")).isEqualTo(1L);
        assertThat(out.get("active_members")).isEqualTo(12L);
        assertThat(out.get("official_posts")).isEqualTo(4L);
        assertThat(out.get("challenge_enrolment")).isEqualTo(10L);
        assertThat(out.get("challenge_completions")).isEqualTo(5L);
        assertThat(out.get("challenge_completion_pct")).isEqualTo(50);
        assertThat(out.get("moderation_backlog")).isEqualTo(2L);
        assertThat(out.get("safety_escalations")).isEqualTo(1L);

        // No per-person leak: no output value is a raw cpid, and no key names a person.
        assertThat(out.keySet()).noneMatch(k -> k.toLowerCase().contains("cpid")
                || k.toLowerCase().contains("person")
                || k.toLowerCase().contains("member_list"));
    }
}
