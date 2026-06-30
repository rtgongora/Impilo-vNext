package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeClearanceEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeSummaryEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeClearanceRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeSummaryRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds + finalises the inpatient discharge summary, GATED on discharge-clearance completion. The
 * native summary (med reconciliation, discharge meds, follow-up, referrals) is the inpatient SoR; on
 * finalisation a FHIR Composition projection is emitted toward Butano/SHR and a post-discharge follow-up
 * is REQUESTED from Khuluma (request, never send). Discharge cannot finalise while any clearance is
 * PENDING — enforcing the checklist gate.
 */
@Service
public class DischargeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(DischargeSummaryService.class);

    private final DischargeSummaryRepository summaryRepository;
    private final DischargeClearanceRepository clearanceRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DischargeSummaryService(DischargeSummaryRepository summaryRepository,
                                   DischargeClearanceRepository clearanceRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.summaryRepository = summaryRepository;
        this.clearanceRepository = clearanceRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    private UUID tenant() {
        return TrustContextHolder.require().tenantId();
    }

    /** Create or update the draft discharge summary for an encounter. */
    @Transactional
    public Map<String, Object> saveDraft(Map<String, Object> body) {
        UUID encounterId = ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id");
        String cpid = ClinicalPayloadMapper.str(body, "patientId", "patient_id", "subjectCpid", "subject_cpid");
        if (encounterId == null || cpid == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "encounterId and patientId are required");
        }
        DischargeSummaryEntity s = summaryRepository
                .findByTenantIdAndEncounterId(tenant(), encounterId)
                .orElseGet(DischargeSummaryEntity::new);
        if (s.getSummaryId() != null && "FINALISED".equals(s.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discharge summary already finalised");
        }
        s.setTenantId(tenant());
        s.setEncounterId(encounterId);
        s.setSubjectCpid(cpid);
        s.setFacilityId(ClinicalPayloadMapper.uuid(body, "facilityId", "facility_id"));
        s.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        s.setDischargeDiagnosis(ClinicalPayloadMapper.str(body, "dischargeDiagnosis", "discharge_diagnosis"));
        s.setDischargeDisposition(ClinicalPayloadMapper.str(body, "disposition", "dischargeDisposition", "discharge_disposition"));
        s.setHospitalCourse(ClinicalPayloadMapper.str(body, "hospitalCourse", "hospital_course"));
        s.setMedicationReconciliationJson(jsonOf(body.get("medicationReconciliation")));
        s.setDischargeMedicationsJson(jsonOf(body.get("dischargeMedications")));
        s.setFollowUpJson(jsonOf(body.get("followUp")));
        s.setReferralsJson(jsonOf(body.get("referrals")));
        s.setStatus("DRAFT");
        s = summaryRepository.save(s);
        return row(s);
    }

    public Map<String, Object> get(UUID encounterId) {
        DischargeSummaryEntity s = summaryRepository.findByTenantIdAndEncounterId(tenant(), encounterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No discharge summary"));
        return row(s);
    }

    /**
     * Finalise the discharge summary. GATE: every discharge clearance for the encounter must be CLEARED
     * or WAIVED — any PENDING clearance blocks finalisation (409). On success: project a FHIR Composition,
     * emit it toward Butano, emit the discharge event, and request a Khuluma follow-up.
     */
    @Transactional
    public Map<String, Object> finalise(UUID encounterId) {
        DischargeSummaryEntity s = summaryRepository.findByTenantIdAndEncounterId(tenant(), encounterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No discharge summary"));
        if ("FINALISED".equals(s.getStatus())) {
            return row(s);
        }

        List<DischargeClearanceEntity> clearances = clearanceRepository
                .findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant(), encounterId);
        if (clearances.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Discharge clearances not initialised — cannot finalise summary");
        }
        List<String> pending = clearances.stream()
                .filter(c -> !"CLEARED".equals(c.getStatus()) && !"WAIVED".equals(c.getStatus()))
                .map(DischargeClearanceEntity::getClearanceType)
                .toList();
        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Discharge blocked — clearances pending: " + String.join(", ", pending));
        }

        String composition = buildFhirComposition(s);
        s.setFhirCompositionJson(composition);
        s.setStatus("FINALISED");
        s.setFinalisedBy(TrustContextHolder.require().actorId());
        s.setFinalisedAt(OffsetDateTime.now());
        s = summaryRepository.save(s);

        // FHIR Composition projection → Butano/SHR (the longitudinal record is owned by Butano).
        Map<String, Object> butano = new LinkedHashMap<>();
        butano.put("encounter_id", s.getEncounterId().toString());
        butano.put("subject_cpid", s.getSubjectCpid());
        butano.put("resource_type", "Composition");
        butano.put("composition", composition);
        emit("DISCHARGE_SUMMARY", s.getSummaryId().toString(),
                "inpatient.discharge.summary_finalised", butano);

        // Request a post-discharge follow-up from Khuluma (request, never send).
        Map<String, Object> followup = new LinkedHashMap<>();
        followup.put("subject_cpid", s.getSubjectCpid());
        followup.put("encounter_id", s.getEncounterId().toString());
        followup.put("channel", "PREFERRED");
        followup.put("reason", "POST_DISCHARGE_FOLLOWUP");
        followup.put("follow_up", s.getFollowUpJson());
        emit("DISCHARGE_SUMMARY", s.getSummaryId().toString(),
                "inpatient.discharge.followup_requested", followup);

        log.info("INPATIENT: discharge summary finalised for encounter {} (subject {})",
                encounterId, s.getSubjectCpid());
        return row(s);
    }

    /**
     * Minimal FHIR R4 Composition projection (discharge summary doc) — the load-bearing sections:
     * Hospital Course, Discharge Diagnosis, Medication Reconciliation, Discharge Medications, Follow-up.
     * Butano performs full FHIR validation/storage; this is the bridge shape.
     */
    private String buildFhirComposition(DischargeSummaryEntity s) {
        Map<String, Object> comp = new LinkedHashMap<>();
        comp.put("resourceType", "Composition");
        comp.put("status", "final");
        comp.put("type", Map.of("coding", List.of(Map.of(
                "system", "http://loinc.org", "code", "18842-5", "display", "Discharge summary"))));
        comp.put("subject", Map.of("identifier", Map.of("value", s.getSubjectCpid())));
        comp.put("encounter", Map.of("reference", "Encounter/" + s.getEncounterId()));
        comp.put("date", OffsetDateTime.now().toString());
        comp.put("title", "Inpatient Discharge Summary");
        List<Map<String, Object>> sections = List.of(
                section("Hospital Course", s.getHospitalCourse()),
                section("Discharge Diagnosis", s.getDischargeDiagnosis()),
                section("Medication Reconciliation", s.getMedicationReconciliationJson()),
                section("Discharge Medications", s.getDischargeMedicationsJson()),
                section("Follow-up", s.getFollowUpJson()),
                section("Referrals", s.getReferralsJson()));
        comp.put("section", sections);
        comp.put("disposition", s.getDischargeDisposition());
        return jsonOf(comp);
    }

    private Map<String, Object> section(String title, String text) {
        Map<String, Object> sec = new LinkedHashMap<>();
        sec.put("title", title);
        sec.put("text", Map.of("status", "generated", "div", text != null ? text : ""));
        return sec;
    }

    private Map<String, Object> row(DischargeSummaryEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getSummaryId() != null ? s.getSummaryId().toString() : null);
        m.put("encounter_id", s.getEncounterId().toString());
        m.put("subject_cpid", s.getSubjectCpid());
        m.put("status", s.getStatus());
        m.put("discharge_diagnosis", s.getDischargeDiagnosis());
        m.put("discharge_disposition", s.getDischargeDisposition());
        m.put("hospital_course", s.getHospitalCourse());
        m.put("medication_reconciliation", s.getMedicationReconciliationJson());
        m.put("discharge_medications", s.getDischargeMedicationsJson());
        m.put("follow_up", s.getFollowUpJson());
        m.put("referrals", s.getReferralsJson());
        m.put("fhir_composition", s.getFhirCompositionJson());
        m.put("finalised_by", s.getFinalisedBy());
        m.put("finalised_at", s.getFinalisedAt());
        return m;
    }

    private String jsonOf(Object o) {
        if (o == null) return null;
        if (o instanceof String str) return str;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void emit(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setTenantId(tenant().toString());
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise discharge event", e);
        }
        outboxRepository.save(outbox);
    }
}
