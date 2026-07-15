package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;
import zw.gov.mohcc.impilo.pct.integration.NotificationClient;
import zw.gov.mohcc.impilo.pct.integration.VashandiOnCallResolver;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaActivationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaTeamMemberEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdVisitEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EdTraumaTeamMemberRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trauma-team roster → notify → ack → escalate (W4, G1.7). On activation, resolves the on-call
 * trauma team from VASHANDI (read-only), materialises one member row per rostered clinician, and
 * raises a REAL notification delivery-intent per member (not a fabricated "sent"). Members
 * acknowledge; those who do not ack within the timeout are escalated with a fresh delivery-intent.
 * Replaces the old jsonb team blob + PENDING→SENT paging.
 */
@Service
public class EdTraumaTeamService {

    private static final Logger log = LoggerFactory.getLogger(EdTraumaTeamService.class);
    private static final String ACTIVATION_TEMPLATE = "ED_TRAUMA_TEAM";
    private static final String ESCALATION_TEMPLATE = "ED_TRAUMA_ESCALATION";
    private static final String CHANNEL = "PUSH";

    private final EdTraumaTeamMemberRepository memberRepo;
    private final VashandiOnCallResolver onCallResolver;
    private final NotificationClient notifications;
    private final DaidzaiEpisodeClient daidzaiEpisodes;

    public EdTraumaTeamService(EdTraumaTeamMemberRepository memberRepo,
                               VashandiOnCallResolver onCallResolver,
                               NotificationClient notifications,
                               DaidzaiEpisodeClient daidzaiEpisodes) {
        this.memberRepo = memberRepo;
        this.onCallResolver = onCallResolver;
        this.notifications = notifications;
        this.daidzaiEpisodes = daidzaiEpisodes;
    }

    /**
     * Resolve the on-call trauma team and notify each member. Idempotent per member
     * (UNIQUE(trauma_id, workforce_profile_id)) — re-activating does not double-notify existing
     * members. Runs after the activation row is created; a degraded VASHANDI/notifier never blocks
     * the activation (the panel is simply empty / the delivery ref null — honestly visible).
     */
    @Transactional
    public List<EdTraumaTeamMemberEntity> activateTeam(UUID tenantId, EdVisitEntity visit,
                                                       EdTraumaActivationEntity activation) {
        UUID facilityId = visit.getFacilityId();
        var resolved = onCallResolver.resolveTraumaTeam(tenantId, facilityId);
        for (var r : resolved) {
            if (memberRepo.findByTraumaIdAndWorkforceProfileId(activation.getTraumaId(), r.workforceProfileId()).isPresent()) {
                continue; // already on this activation
            }
            EdTraumaTeamMemberEntity m = new EdTraumaTeamMemberEntity();
            m.setTenantId(tenantId);
            m.setTraumaId(activation.getTraumaId());
            m.setVisitId(visit.getVisitId());
            m.setTraumaEpisodeId(visit.getTraumaEpisodeId());
            m.setWorkforceProfileId(r.workforceProfileId());
            m.setRole(r.role());
            m.setShiftId(r.shiftId());
            m.setAckStatus("PENDING");
            m = memberRepo.save(m);

            String ref = notifications.notify(tenantId, ACTIVATION_TEMPLATE, CHANNEL, r.workforceProfileId(),
                    vars(visit, activation, r.role(), "Trauma team activation"));
            m.setNotificationRef(ref);
            m.setNotifiedAt(OffsetDateTime.now());
            memberRepo.save(m);
        }
        List<EdTraumaTeamMemberEntity> team = memberRepo.findByTraumaIdOrderByCreatedAtAsc(activation.getTraumaId());
        if (visit.getTraumaEpisodeId() != null) {
            daidzaiEpisodes.registerPhase(tenantId, visit.getTraumaEpisodeId(), "TRIAGE",
                    activation.getTraumaId().toString(), "TEAM_ACTIVATED_" + team.size(),
                    "pct.ed.trauma_team_activated");
        }
        log.info("Trauma team activated for trauma {} — {} member(s) notified", activation.getTraumaId(), team.size());
        return team;
    }

    /** A rostered member acknowledges the activation. Idempotent. */
    @Transactional
    public EdTraumaTeamMemberEntity acknowledge(UUID tenantId, UUID memberId, String ackBy) {
        EdTraumaTeamMemberEntity m = memberRepo.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found"));
        if ("ACKED".equals(m.getAckStatus())) {
            return m;
        }
        m.setAckStatus("ACKED");
        m.setAckAt(OffsetDateTime.now());
        m.setAckBy(ackBy);
        return memberRepo.save(m);
    }

    /**
     * Escalate members who have not acknowledged within {@code timeoutSeconds} of being notified.
     * Each escalated member gets a fresh escalation delivery-intent. Returns the escalated members.
     * (In production a scheduler calls this; the rig calls it with timeout=0 to prove the path.)
     */
    @Transactional
    public List<EdTraumaTeamMemberEntity> escalateNoAck(UUID tenantId, UUID traumaId, long timeoutSeconds) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(Math.max(0, timeoutSeconds));
        List<EdTraumaTeamMemberEntity> pending =
                memberRepo.findByTraumaIdAndAckStatusAndNotifiedAtBefore(traumaId, "PENDING", cutoff);
        for (EdTraumaTeamMemberEntity m : pending) {
            m.setAckStatus("ESCALATED");
            m.setEscalatedAt(OffsetDateTime.now());
            Map<String, String> v = new LinkedHashMap<>();
            v.put("workforceProfileId", m.getWorkforceProfileId());
            v.put("role", m.getRole() == null ? "" : m.getRole());
            v.put("message", "No acknowledgement — trauma team escalation");
            String ref = notifications.notify(tenantId, ESCALATION_TEMPLATE, CHANNEL,
                    escalationRecipient(m), v);
            m.setEscalationRef(ref);
            memberRepo.save(m);
            if (m.getTraumaEpisodeId() != null) {
                daidzaiEpisodes.registerPhase(tenantId, m.getTraumaEpisodeId(), "TRIAGE",
                        m.getId().toString(), "ESCALATED", "pct.ed.trauma_team_escalated");
            }
        }
        log.info("Trauma team escalation for trauma {} — {} member(s) escalated on no-ack", traumaId, pending.size());
        return pending;
    }

    @Transactional(readOnly = true)
    public List<EdTraumaTeamMemberEntity> listTeam(UUID traumaId) {
        return memberRepo.findByTraumaIdOrderByCreatedAtAsc(traumaId);
    }

    /** Escalation routes to the on-call supervisor pool in production; here it re-pages the member. */
    private String escalationRecipient(EdTraumaTeamMemberEntity m) {
        return "supervisor-oncall:" + m.getWorkforceProfileId();
    }

    private Map<String, String> vars(EdVisitEntity visit, EdTraumaActivationEntity activation,
                                     String role, String message) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("location", visit.getZone() != null ? visit.getZone() : "ED_RESUS");
        v.put("patientId", visit.getPatientCpid());
        v.put("traumaLevel", String.valueOf(activation.getTraumaLevel()));
        v.put("mechanism", activation.getMechanism() != null ? activation.getMechanism() : "");
        v.put("recipientRole", role == null ? "" : role);
        v.put("message", message);
        return v;
    }
}
