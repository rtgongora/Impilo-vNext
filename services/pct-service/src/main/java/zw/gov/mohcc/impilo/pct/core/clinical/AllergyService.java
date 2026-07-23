package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.AllergyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AllergyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Allergy/intolerance SoR service (OF-B3) — mirrors the problems-list pattern:
 * tenant-scoped, subject-relationship gated on write, every mutation audited via
 * the outbox. Deactivation is a status flip, never a delete (clinical history stands).
 */
@Service
public class AllergyService {

    private static final Logger log = LoggerFactory.getLogger(AllergyService.class);

    private final AllergyRepository allergyRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public AllergyService(AllergyRepository allergyRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper,
                          ClinicalAccessGuard accessGuard) {
        this.allergyRepository = allergyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<AllergyEntity> list(String subjectCpid, String clinicalStatus) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (clinicalStatus == null || clinicalStatus.isBlank()) {
            return allergyRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenantId, subjectCpid);
        }
        return allergyRepository.findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
                tenantId, subjectCpid, clinicalStatus.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public AllergyEntity add(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        AllergyEntity a = new AllergyEntity();
        a.setAllergyId(UUID.randomUUID());
        a.setTenantId(ctx.tenantId());
        // BFF strangler contract sends patient_id; PCT convention is subject_cpid — accept both.
        String subject = str(body.get("subject_cpid"));
        if (subject == null) subject = str(body.get("patient_id"));
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Missing field: patient_id");
        }
        a.setSubjectCpid(subject);
        // Subject-relationship gate (dimension 6), same rule as the problems list.
        accessGuard.requireCareRelationship(ctx, subject, str(body.get("journey_id")), str(body.get("encounter_id")));

        String allergen = str(body.get("allergen"));
        if (allergen == null || allergen.isBlank()) {
            throw new IllegalArgumentException("Missing field: allergen");
        }
        a.setAllergen(allergen);
        a.setAllergenCode(str(body.get("allergen_code")));
        a.setCodeSystem(str(body.get("code_system")));
        a.setAllergenType(upperOr(body.get("allergen_type"), "DRUG"));
        a.setReaction(str(body.get("reaction")));
        String severity = str(body.get("severity"));
        a.setSeverity(severity != null ? severity.trim().toUpperCase(Locale.ROOT) : null);
        String onset = str(body.get("onset_date"));
        if (onset != null) {
            a.setOnsetDate(LocalDate.parse(onset));
        }
        a.setNotes(str(body.get("notes")));
        String recordedBy = str(body.get("recorded_by"));
        a.setRecordedBy(recordedBy != null ? recordedBy : ctx.actorId());
        a = allergyRepository.save(a);

        emit("ALLERGY_RECORDED", a);
        log.info("pct.allergy.recorded id={} subject={} severity={} coded={} by={} correlationId={}",
                a.getAllergyId(), a.getSubjectCpid(), a.getSeverity(),
                a.getAllergenCode() != null, ctx.actorId(), ctx.correlationId());
        return a;
    }

    @Transactional
    public AllergyEntity deactivate(UUID allergyId) {
        TrustContext ctx = TrustContextHolder.require();
        AllergyEntity a = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new IllegalArgumentException("Allergy not found: " + allergyId));
        if (!a.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Allergy not found: " + allergyId);
        }
        a.setClinicalStatus("INACTIVE");
        a.setDeactivatedAt(OffsetDateTime.now());
        a.setDeactivatedBy(ctx.actorId());
        a = allergyRepository.save(a);
        emit("ALLERGY_DEACTIVATED", a);
        log.info("pct.allergy.deactivated id={} subject={} by={} correlationId={}",
                a.getAllergyId(), a.getSubjectCpid(), ctx.actorId(), ctx.correlationId());
        return a;
    }

    private void emit(String eventType, AllergyEntity a) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allergyId", a.getAllergyId().toString());
        payload.put("subjectCpid", a.getSubjectCpid());
        payload.put("allergen", a.getAllergen());
        payload.put("allergenCode", a.getAllergenCode());
        payload.put("severity", a.getSeverity());
        payload.put("clinicalStatus", a.getClinicalStatus());
        payload.put("actorId", ctx.actorId());
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("ALLERGY");
        outbox.setAggregateId(a.getAllergyId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise allergy event payload: {}", e.getMessage());
            return "{}";
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static String upperOr(Object v, String fallback) {
        String s = str(v);
        return s != null ? s.toUpperCase(Locale.ROOT) : fallback;
    }
}
