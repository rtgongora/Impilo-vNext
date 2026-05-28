package zw.gov.mohcc.impilo.wellness.domain;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Canonical wellness domain API consumed by experience-bff {@code WellnessServiceClient}.
 */
@RestController
@RequestMapping("/internal/v1/wellness")
public class WellnessDomainController {

    private final JdbcTemplate jdbc;

    public WellnessDomainController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/challenges")
    public ResponseEntity<Map<String, Object>> listChallenges(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, tenant_id, title, description, challenge_type, target_value, target_unit,
                       start_date, end_date, participant_count, status
                FROM wellness_challenges
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY start_date DESC
                """,
                tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }
}
