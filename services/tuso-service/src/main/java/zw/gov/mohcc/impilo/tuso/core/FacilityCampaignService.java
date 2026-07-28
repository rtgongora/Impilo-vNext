package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Driving a slice of the estate through the journey, and seeing who never moved (FCV-W6).
 *
 * <p>The 1,774 Ministry facilities will not claim themselves. A campaign groups a province, district
 * or facility type, invites it, and shows progress — most usefully the facilities that never
 * respond, because those are the ones that need a site visit rather than another notification.</p>
 *
 * <p><b>Progress is derived, never stored.</b> The brief lists thirteen campaign statuses; every one
 * is a function of three facts that already live elsewhere — whether a claim was approved, whether a
 * profile submission moved, whether a legitimacy decision was issued. Mirroring them into a status
 * column would mean thirteen values kept in step with three state machines, and they would drift the
 * first time one changed. The view reads the rails, so a campaign board cannot claim a facility is
 * progressing when its claim was revoked yesterday.</p>
 */
@Service
public class FacilityCampaignService {

    private static final Logger log = LoggerFactory.getLogger(FacilityCampaignService.class);

    private final JdbcTemplate jdbc;

    public FacilityCampaignService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CampaignView(UUID id, String name, int members) {}

    /** Create a campaign over a slice of the estate. Membership is resolved once, at creation. */
    @Transactional
    public CampaignView create(String name, String description, String province, String district,
                               String facilityType, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A campaign needs a name.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tuso.facility_onboarding_campaign "
                        + "(id, tenant_id, name, description, province, district, facility_type, created_by) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                id, ctx.tenantId(), name.trim(), description, province, district, facilityType, ctx.actorId());

        int members = jdbc.update(
                "INSERT INTO tuso.facility_campaign_member (campaign_id, facility_uuid) "
                        + "SELECT ?, f.facility_uuid FROM tuso.facility f "
                        + "WHERE f.tenant_id = ? "
                        + "  AND (?::text IS NULL OR upper(btrim(f.province)) = upper(btrim(?))) "
                        + "  AND (?::text IS NULL OR upper(btrim(f.district)) = upper(btrim(?))) "
                        + "  AND (?::text IS NULL OR f.facility_type = ?) "
                        + "ORDER BY f.id LIMIT ? "
                        + "ON CONFLICT (campaign_id, facility_uuid) DO NOTHING",
                id, ctx.tenantId(), province, province, district, district, facilityType, facilityType,
                Math.min(Math.max(limit, 1), 5000));

        log.info("Facility onboarding campaign {} '{}' created by {} with {} member(s)",
                id, name, ctx.actorId(), members);
        return new CampaignView(id, name.trim(), members);
    }

    /** Mark members invited. The invitation itself is dispatched by the notification lane. */
    @Transactional
    public int markInvited(UUID campaignId) {
        TrustContext ctx = TrustContextHolder.require();
        return jdbc.update("UPDATE tuso.facility_campaign_member SET invited_at=now(), invited_by=? "
                + "WHERE campaign_id=? AND invited_at IS NULL", ctx.actorId(), campaignId);
    }

    /**
     * Flag a facility as needing help the platform cannot give — a site visit, a paper process.
     * The one campaign fact a human decides rather than the rails deriving it.
     */
    @Transactional
    public void escalate(UUID campaignId, UUID facilityUuid, String note) {
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say why this facility needs assistance — an escalation without a reason cannot be actioned.");
        }
        int updated = jdbc.update("UPDATE tuso.facility_campaign_member "
                        + "SET escalated_at=now(), assistance_note=? WHERE campaign_id=? AND facility_uuid=?",
                note.trim(), campaignId, facilityUuid);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That facility is not in this campaign.");
        }
    }

    /** The board, read live from the claim / submission / decision rails. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> progress(UUID campaignId) {
        return jdbc.queryForList(
                "SELECT * FROM tuso.facility_campaign_progress WHERE campaign_id=? "
                        + "ORDER BY derived_status, facility_name", campaignId);
    }

    /** Counts by derived status — the number a national administrator actually looks at. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> summary(UUID campaignId) {
        return jdbc.queryForList(
                "SELECT derived_status, count(*) AS facilities FROM tuso.facility_campaign_progress "
                        + "WHERE campaign_id=? GROUP BY derived_status ORDER BY 2 DESC", campaignId);
    }
}
