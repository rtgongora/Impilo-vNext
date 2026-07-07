package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Programme-engagement dashboard for wellness-social. Produces ONLY aggregate counts/rates for a
 * programme — NEVER per-person social content, never a person_cpid. Gated to PROGRAMME_ADMIN by the
 * controller via {@link zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard#requireAggregate}. Uses
 * JdbcTemplate so each metric is a single set-based query.
 *
 * <p>Metrics: groups, communities, active members, posts, challenge enrolment + completion, reported
 * content, moderation backlog, official engagement, safety escalations.
 */
@Service
public class ProgrammeEngagementService {

    private final JdbcTemplate jdbc;

    public ProgrammeEngagementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID tenantId, UUID programmeId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("programme_id", programmeId == null ? null : programmeId.toString());

        // Groups + communities scoped to the programme.
        out.put("groups", count(
                "SELECT COUNT(*) FROM simba.simba_group WHERE tenant_id = ? AND programme_id = ? AND status = 'ACTIVE'",
                tenantId, programmeId));
        out.put("communities", count(
                "SELECT COUNT(*) FROM simba.simba_community WHERE tenant_id = ? AND programme_id = ? AND status = 'ACTIVE'",
                tenantId, programmeId));

        // Active members across the programme's groups (distinct persons — count only, no identity).
        out.put("active_members", count(
                """
                SELECT COUNT(DISTINCT m.person_cpid)
                FROM simba.simba_group_membership m
                JOIN simba.simba_group g ON g.group_id = m.group_id
                WHERE m.tenant_id = ? AND g.programme_id = ? AND m.status = 'ACTIVE'
                """, tenantId, programmeId));

        // Posts + official engagement scoped to the programme.
        out.put("posts", count(
                "SELECT COUNT(*) FROM simba.simba_social_post WHERE tenant_id = ? AND programme_id = ?",
                tenantId, programmeId));
        out.put("official_posts", count(
                "SELECT COUNT(*) FROM simba.simba_social_post WHERE tenant_id = ? AND programme_id = ? AND official = TRUE",
                tenantId, programmeId));

        // Challenge enrolment + completion (progress meeting the challenge target).
        long enrolment = count(
                """
                SELECT COUNT(*) FROM simba.challenge_participants p
                JOIN simba.challenges c ON c.challenge_id = p.challenge_id
                WHERE p.tenant_id = ? AND c.programme_id = ?
                """, tenantId, programmeId);
        long completions = count(
                """
                SELECT COUNT(*) FROM simba.challenge_participants p
                JOIN simba.challenges c ON c.challenge_id = p.challenge_id
                WHERE p.tenant_id = ? AND c.programme_id = ?
                  AND c.target_value IS NOT NULL AND c.target_value > 0
                  AND p.progress_value >= c.target_value
                """, tenantId, programmeId);
        out.put("challenge_enrolment", enrolment);
        out.put("challenge_completions", completions);
        out.put("challenge_completion_pct", pct(completions, enrolment));

        // Reported content + moderation backlog (open reports across the programme's content).
        out.put("reported_content", count(
                """
                SELECT COUNT(*) FROM simba.simba_content_report r
                WHERE r.tenant_id = ? AND r.subject_id IN (
                    SELECT post_id FROM simba.simba_social_post WHERE tenant_id = ? AND programme_id = ?
                )
                """, tenantId, tenantId, programmeId));
        out.put("moderation_backlog", count(
                """
                SELECT COUNT(*) FROM simba.simba_content_report r
                WHERE r.tenant_id = ? AND r.status = 'OPEN' AND r.subject_id IN (
                    SELECT post_id FROM simba.simba_social_post WHERE tenant_id = ? AND programme_id = ?
                )
                """, tenantId, tenantId, programmeId));

        // Safety escalations (reports that produced a care linkage).
        out.put("safety_escalations", count(
                """
                SELECT COUNT(*) FROM simba.simba_content_report r
                WHERE r.tenant_id = ? AND r.care_linkage_id IS NOT NULL AND r.subject_id IN (
                    SELECT post_id FROM simba.simba_social_post WHERE tenant_id = ? AND programme_id = ?
                )
                """, tenantId, tenantId, programmeId));

        return out;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private static int pct(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }
}
