package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.BookingFollowUpClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Surgery-specialised discharge summary (Wave 4 §14). Composes the operative procedure summary,
 * wound/drain status, discharge medicines, follow-up (which can be a real teleconsult booking),
 * warning signs, rehab plan, referral-back, patient instructions and a provider summary. Crucially it
 * snapshots + surfaces the PENDING histopathology (specimens that survive discharge — pathology is
 * tracked afterwards), publishes a FHIR Composition toward Butano/SHR, and emits
 * {@code theatre.discharge.completed}. The case SoR remains procedure_episode; this is a projection.
 */
@Service
public class SurgicalDischargeService {

    private static final Logger log = LoggerFactory.getLogger(SurgicalDischargeService.class);
    private static final List<String> PATHOLOGY_TERMINAL = List.of("RESULTED", "ACKNOWLEDGED", "REJECTED");

    private final ProcedureDischargeRepository dischargeRepository;
    private final ProcedureEpisodeRepository episodeRepository;
    private final ProcedureNoteRepository noteRepository;
    private final ProcedureSpecimenRepository specimenRepository;
    private final EventOutboxRepository outboxRepository;
    private final BookingFollowUpClient bookingFollowUpClient;
    private final ObjectMapper objectMapper;

    public SurgicalDischargeService(ProcedureDischargeRepository dischargeRepository,
                                    ProcedureEpisodeRepository episodeRepository,
                                    ProcedureNoteRepository noteRepository,
                                    ProcedureSpecimenRepository specimenRepository,
                                    EventOutboxRepository outboxRepository,
                                    BookingFollowUpClient bookingFollowUpClient,
                                    ObjectMapper objectMapper) {
        this.dischargeRepository = dischargeRepository;
        this.episodeRepository = episodeRepository;
        this.noteRepository = noteRepository;
        this.specimenRepository = specimenRepository;
        this.outboxRepository = outboxRepository;
        this.bookingFollowUpClient = bookingFollowUpClient;
        this.objectMapper = objectMapper;
    }

    private UUID tenant() { return TrustContextHolder.require().tenantId(); }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null && !a.isBlank() ? a : "system";
        } catch (IllegalStateException e) { return "system"; }
    }

    private ProcedureEpisodeEntity requireEpisode(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .filter(e -> e.getTenantId().equals(tenant()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found"));
    }

    @Transactional
    public Map<String, Object> saveDraft(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureDischargeEntity d = dischargeRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedureDischargeEntity::new);
        if ("COMPLETED".equals(d.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Surgical discharge already completed");
        }
        applyBody(d, e, body);
        d.setStatus("DRAFT");
        d.setAuthoredBy(actor());
        dischargeRepository.save(d);
        return row(d);
    }

    public Map<String, Object> get(UUID episodeId) {
        requireEpisode(episodeId);
        return dischargeRepository.findByEpisodeId(episodeId).map(this::row).orElse(Map.of("status", "NONE"));
    }

    /**
     * Finalise the surgical discharge: compose the summary (pulling the operative note where the caller
     * did not override), snapshot pending histopathology, book a real follow-up, publish a FHIR
     * Composition toward Butano, and emit {@code theatre.discharge.completed}.
     */
    @Transactional
    public Map<String, Object> complete(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureDischargeEntity d = dischargeRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedureDischargeEntity::new);
        if ("COMPLETED".equals(d.getStatus())) {
            return row(d);
        }
        applyBody(d, e, body);

        // Compose from the operative note where the caller did not override.
        ProcedureNoteEntity note = noteRepository.findByEpisodeId(episodeId).orElse(null);
        if (note != null) {
            if (isBlank(d.getProcedureSummary())) d.setProcedureSummary(buildProcedureSummary(note));
            if (isBlank(d.getComplications())) d.setComplications(note.getComplications());
        }

        // Snapshot pending histopathology — pathology is tracked AFTER discharge and must be surfaced.
        List<Map<String, Object>> pending = pendingPathology(episodeId);
        d.setPendingPathologyJson(jsonOf(pending));

        // Real follow-up booking (best-effort; a teleconsult follow-up is a valid channel).
        if (d.getFollowUpDate() != null && d.getFollowUpBookingRef() == null) {
            String idem = "inpatient-discharge:" + episodeId + ":followup";
            String bookingRef = bookingFollowUpClient.createFollowUp(e.getSubjectCpid(),
                    d.getFollowUpService(), d.getFollowUpDate(), d.isFollowUpIsTeleconsult(),
                    "Post-operative review — " + Objects.requireNonNullElse(e.getProcedureName(), "surgery"), idem);
            d.setFollowUpBookingRef(bookingRef);
        }

        d.setStatus("COMPLETED");
        d.setCompletedBy(actor());
        d.setCompletedAt(OffsetDateTime.now());
        dischargeRepository.save(d);

        // FHIR Composition projection → Butano/SHR (the longitudinal record is owned by Butano).
        emit("PROCEDURE", episodeId.toString(), "theatre.discharge.published", Map.of(
                "episode_id", episodeId.toString(),
                "subject_cpid", nullSafe(e.getSubjectCpid()),
                "resource_type", "Composition",
                "composition", buildFhirComposition(e, d, pending)));

        // Case-level discharge completion.
        emit("PROCEDURE", episodeId.toString(), "theatre.discharge.completed", Map.of(
                "episode_id", episodeId.toString(),
                "subject_cpid", nullSafe(e.getSubjectCpid()),
                "follow_up_booking_ref", nullSafe(d.getFollowUpBookingRef()),
                "follow_up_is_teleconsult", d.isFollowUpIsTeleconsult(),
                "pending_pathology_count", pending.size(),
                "referral_back_facility_ref", nullSafe(d.getReferralBackFacilityRef())));

        log.info("INPATIENT-DISCHARGE: surgical discharge completed for episode {} ({} pending histopath)",
                episodeId, pending.size());
        return row(d);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────
    private void applyBody(ProcedureDischargeEntity d, ProcedureEpisodeEntity e, Map<String, Object> body) {
        d.setTenantId(e.getTenantId());
        d.setEpisodeId(e.getEpisodeId());
        d.setSubjectCpid(e.getSubjectCpid());
        d.setEncounterRef(e.getEncounterId());
        setIf(body, d::setFinalDiagnosis, "finalDiagnosis", "final_diagnosis");
        setIf(body, d::setProcedureSummary, "procedureSummary", "procedure_summary");
        setIf(body, d::setComplications, "complications");
        setIf(body, d::setWoundStatus, "woundStatus", "wound_status");
        setIf(body, d::setDrainStatus, "drainStatus", "drain_status");
        setIf(body, d::setWarningSigns, "warningSigns", "warning_signs");
        setIf(body, d::setRehabPlan, "rehabPlan", "rehab_plan");
        setIf(body, d::setFollowUpService, "followUpService", "follow_up_service");
        setIf(body, d::setReferralBackFacilityRef, "referralBackFacilityRef", "referral_back_facility_ref");
        setIf(body, d::setPatientInstructions, "patientInstructions", "patient_instructions");
        setIf(body, d::setProviderSummary, "providerSummary", "provider_summary");
        Object meds = body.get("dischargeMedications");
        if (meds == null) meds = body.get("discharge_medications");
        if (meds != null) d.setDischargeMedicationsJson(jsonOf(meds));
        Object docs = body.get("supportingDocs");
        if (docs == null) docs = body.get("supporting_docs");
        if (docs != null) d.setSupportingDocsJson(jsonOf(docs));
        Integer sick = ClinicalPayloadMapper.integer(body, "sickLeaveDays", "sick_leave_days");
        if (sick != null) d.setSickLeaveDays(sick);
        String followUp = ClinicalPayloadMapper.str(body, "followUpDate", "follow_up_date");
        if (followUp != null) {
            try { d.setFollowUpDate(LocalDate.parse(followUp)); }
            catch (RuntimeException ignored) { /* leave prior value */ }
        }
        Object tele = body.containsKey("followUpIsTeleconsult") ? body.get("followUpIsTeleconsult")
                : body.get("follow_up_is_teleconsult");
        if (tele != null) d.setFollowUpIsTeleconsult(Boolean.parseBoolean(String.valueOf(tele)));
    }

    private List<Map<String, Object>> pendingPathology(UUID episodeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProcedureSpecimenEntity s : specimenRepository.findByEpisodeIdOrderBySeqAsc(episodeId)) {
            if (PATHOLOGY_TERMINAL.contains(s.getStatus())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("specimen_id", s.getSpecimenId() != null ? s.getSpecimenId().toString() : null);
            m.put("label", s.getSpecimenLabel());
            m.put("type", s.getSpecimenType());
            m.put("status", s.getStatus());
            m.put("oros_specimen_id", s.getOrosSpecimenId());
            out.add(m);
        }
        return out;
    }

    private String buildProcedureSummary(ProcedureNoteEntity note) {
        StringBuilder sb = new StringBuilder();
        if (note.getPerformedProcedure() != null) sb.append(note.getPerformedProcedure());
        if (note.getFindings() != null) sb.append(sb.length() > 0 ? " — Findings: " : "Findings: ").append(note.getFindings());
        if (note.getEstimatedBloodLossMl() != null) sb.append(" (EBL ").append(note.getEstimatedBloodLossMl()).append(" mL)");
        return sb.length() > 0 ? sb.toString() : null;
    }

    private Map<String, Object> buildFhirComposition(ProcedureEpisodeEntity e, ProcedureDischargeEntity d,
                                                     List<Map<String, Object>> pending) {
        Map<String, Object> comp = new LinkedHashMap<>();
        comp.put("resourceType", "Composition");
        comp.put("status", "final");
        comp.put("type", Map.of("coding", List.of(Map.of(
                "system", "http://loinc.org", "code", "18842-5", "display", "Discharge summary"))));
        comp.put("subject", Map.of("identifier", Map.of("value", nullSafe(e.getSubjectCpid()))));
        comp.put("title", "Surgical Discharge Summary");
        comp.put("date", OffsetDateTime.now().toString());
        comp.put("section", List.of(
                section("Final Diagnosis", d.getFinalDiagnosis()),
                section("Procedure", d.getProcedureSummary()),
                section("Complications", d.getComplications()),
                section("Wound / Drain", nullSafe(d.getWoundStatus()) + " / " + nullSafe(d.getDrainStatus())),
                section("Discharge Medications", d.getDischargeMedicationsJson()),
                section("Follow-up", nullSafe(d.getFollowUpService())),
                section("Pending Pathology", pending.size() + " specimen(s) awaiting histopathology result"),
                section("Rehabilitation", d.getRehabPlan()),
                section("Warning Signs", d.getWarningSigns()),
                section("Patient Instructions", d.getPatientInstructions())));
        return comp;
    }

    private Map<String, Object> section(String title, String text) {
        Map<String, Object> sec = new LinkedHashMap<>();
        sec.put("title", title);
        sec.put("text", Map.of("status", "generated", "div", text != null ? text : ""));
        return sec;
    }

    private Map<String, Object> row(ProcedureDischargeEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getDischargeId() != null ? d.getDischargeId().toString() : null);
        m.put("episode_id", d.getEpisodeId() != null ? d.getEpisodeId().toString() : null);
        m.put("subject_cpid", d.getSubjectCpid());
        m.put("status", d.getStatus());
        m.put("final_diagnosis", d.getFinalDiagnosis());
        m.put("procedure_summary", d.getProcedureSummary());
        m.put("complications", d.getComplications());
        m.put("wound_status", d.getWoundStatus());
        m.put("drain_status", d.getDrainStatus());
        m.put("discharge_medications", d.getDischargeMedicationsJson());
        m.put("follow_up_date", d.getFollowUpDate());
        m.put("follow_up_service", d.getFollowUpService());
        m.put("follow_up_booking_ref", d.getFollowUpBookingRef());
        m.put("follow_up_is_teleconsult", d.isFollowUpIsTeleconsult());
        m.put("warning_signs", d.getWarningSigns());
        m.put("rehab_plan", d.getRehabPlan());
        m.put("pending_pathology", parseJsonList(d.getPendingPathologyJson()));
        m.put("sick_leave_days", d.getSickLeaveDays());
        m.put("referral_back_facility_ref", d.getReferralBackFacilityRef());
        m.put("patient_instructions", d.getPatientInstructions());
        m.put("provider_summary", d.getProviderSummary());
        m.put("butano_document_ref", d.getButanoDocumentRef());
        m.put("completed_by", d.getCompletedBy());
        m.put("completed_at", d.getCompletedAt());
        return m;
    }

    private Object parseJsonList(String json) {
        if (json == null) return List.of();
        try { return objectMapper.readValue(json, List.class); }
        catch (JsonProcessingException e) { return List.of(); }
    }

    private static void setIf(Map<String, Object> body, java.util.function.Consumer<String> setter, String... keys) {
        String v = ClinicalPayloadMapper.str(body, keys);
        if (v != null) setter.accept(v);
    }

    private String jsonOf(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return null; }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String nullSafe(String s) { return s != null ? s : ""; }

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
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise surgical discharge event", ex);
        }
        outboxRepository.save(outbox);
    }
}
