package zw.gov.mohcc.impilo.referral.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.referral.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.referral.persistence.entity.SurgicalReferralEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.referral.persistence.repository.SurgicalReferralRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Surgical referral orchestration. Wraps the existing {@link ReferralEntity}
 * lifecycle FSM (accept/respond/complete) and attaches surgical-domain depth via
 * {@link SurgicalReferralEntity}. The receiving-team decision is the funnel:
 * an ACCEPTED surgical referral emits {@code referral.surgical.accepted}, the
 * single trigger that places an elective case on the scheduling waitlist.
 */
@Service
public class SurgicalReferralService {

    static final String EVT_CREATED = "referral.surgical.created";
    static final String EVT_ACCEPTED = "referral.surgical.accepted";
    static final String EVT_DECLINED = "referral.surgical.declined";
    static final String EVT_REDIRECTED = "referral.surgical.redirected";
    static final String EVT_INFO_REQUESTED = "referral.surgical.info_requested";

    private final ReferralRepository referralRepository;
    private final SurgicalReferralRepository surgicalRepository;
    private final ReferralOutboxPublisher outboxPublisher;

    public SurgicalReferralService(ReferralRepository referralRepository,
                                   SurgicalReferralRepository surgicalRepository,
                                   ReferralOutboxPublisher outboxPublisher) {
        this.referralRepository = referralRepository;
        this.surgicalRepository = surgicalRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public Map<String, Object> createSurgicalReferral(Map<String, Object> body) {
        String patientId = str(body, "patientId");
        if (patientId == null || patientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        UUID tenantId = tenantId();

        // The referral aggregate is the source of truth for the lifecycle FSM.
        UUID referralId = UUID.randomUUID();
        Map<String, Object> referralPayload = new LinkedHashMap<>();
        referralPayload.put("kind", "SURGICAL");
        referralPayload.put("intendedProcedureZiboCode", str(body, "intendedProcedureZiboCode"));
        referralPayload.put("targetSpecialty", str(body, "targetSpecialty"));
        ReferralEntity referral = new ReferralEntity(referralId, tenantId, patientId, referralPayload);
        referralRepository.save(referral);

        SurgicalReferralEntity surgical = new SurgicalReferralEntity(UUID.randomUUID(), referralId, tenantId, patientId);
        surgical.setIndication(str(body, "indication"));
        surgical.setIntendedProcedureZiboCode(str(body, "intendedProcedureZiboCode"));
        surgical.setUrgency(str(body, "urgency"));
        surgical.setClinicalPriority(str(body, "clinicalPriority"));
        surgical.setLaterality(str(body, "laterality"));
        surgical.setAnatomicalSite(str(body, "anatomicalSite"));
        surgical.setRequestedInvestigations(stringList(body.get("requestedInvestigations")));
        surgical.setAnaesthesiaReviewRequested(bool(body, "anaesthesiaReviewRequested"));
        surgical.setSpecialistReviewRequested(bool(body, "specialistReviewRequested"));
        surgical.setTargetSpecialty(str(body, "targetSpecialty"));
        try {
            surgicalRepository.save(surgical);
        } catch (DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active surgical referral already exists for this patient and procedure");
        }

        emit(surgical, EVT_CREATED);
        return surgical.toEnvelope();
    }

    /**
     * Receiving-team decision. ACCEPTED runs the referral FSM accept() and emits
     * the funnel trigger; DECLINED/REDIRECTED/INFO_REQUESTED emit their own events
     * and never reach scheduling.
     */
    @Transactional
    public Map<String, Object> decideSurgicalReferral(String id, Map<String, Object> body) {
        SurgicalReferralEntity surgical = require(id);
        String decision = str(body, "decision");
        if (decision == null || decision.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision is required");
        }
        decision = decision.toUpperCase();
        String reason = str(body, "reason", "decisionReason");

        try {
            surgical.decide(decision, reason);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }

        ReferralEntity referral = referralRepository.findByTenantIdAndId(tenantId(), surgical.getReferralId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent referral missing"));

        String event;
        switch (decision) {
            case SurgicalReferralEntity.DECISION_ACCEPTED -> {
                referral.accept();
                event = EVT_ACCEPTED;
            }
            case SurgicalReferralEntity.DECISION_DECLINED -> {
                referral.respond(reason != null ? reason : "declined");
                event = EVT_DECLINED;
            }
            case SurgicalReferralEntity.DECISION_REDIRECTED -> {
                referral.respond(reason != null ? reason : "redirected");
                event = EVT_REDIRECTED;
            }
            case SurgicalReferralEntity.DECISION_INFO_REQUESTED -> {
                referral.respond(reason != null ? reason : "information requested");
                event = EVT_INFO_REQUESTED;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported decision: " + decision);
        }
        referralRepository.save(referral);
        surgicalRepository.save(surgical);
        emit(surgical, event);
        return surgical.toEnvelope();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSurgicalReferral(String id) {
        return require(id).toEnvelope();
    }

    /**
     * Receiving-team worklist. A filtered READ of the same surgical_referral rows
     * (by specialty and/or decision) — no duplication of the referral data.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> worklist(String specialty, String decision) {
        UUID tenantId = tenantId();
        List<SurgicalReferralEntity> rows;
        String dec = decision != null && !decision.isBlank()
                ? decision.toUpperCase() : SurgicalReferralEntity.DECISION_PENDING;
        if (specialty != null && !specialty.isBlank()) {
            rows = surgicalRepository.findByTenantIdAndTargetSpecialtyAndDecisionOrderByUpdatedAtDesc(
                    tenantId, specialty, dec);
        } else {
            rows = surgicalRepository.findByTenantIdAndDecisionOrderByUpdatedAtDesc(tenantId, dec);
        }
        return rows.stream().map(SurgicalReferralEntity::toEnvelope).toList();
    }

    private SurgicalReferralEntity require(String id) {
        UUID surgicalId;
        try {
            surgicalId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid surgical referral id");
        }
        return surgicalRepository.findByTenantIdAndId(tenantId(), surgicalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Surgical referral not found: " + id));
    }

    private void emit(SurgicalReferralEntity surgical, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", surgical.getTenantId().toString());
        payload.put("surgicalReferralId", surgical.getId().toString());
        payload.put("referralId", surgical.getReferralId().toString());
        payload.put("patientId", surgical.getPatientId());
        payload.put("intendedProcedureZiboCode", surgical.getIntendedProcedureZiboCode());
        payload.put("clinicalPriority", surgical.getClinicalPriority());
        payload.put("targetSpecialty", surgical.getTargetSpecialty());
        payload.put("decision", surgical.getDecision());
        outboxPublisher.append("SurgicalReferral", surgical.getId().toString(), eventType, payload);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private static boolean bool(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
