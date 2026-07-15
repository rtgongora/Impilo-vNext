package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ProcedureEpisodeService {

    /** Tenant for the current request from the trust context (was a hardcoded shared default). */
    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    /** Real actor for clinical-audit provenance — never a fabricated name (G037). */
    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    /** Real facility id from the trust context (was the literal "Impilo Facility" — G038). */
    private UUID currentFacility() {
        try {
            return TrustContextHolder.require().facilityId();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static final Map<String, List<String[]>> WHO_CHECKLIST = Map.of(
            "SIGN_IN", List.of(
                    new String[]{"ID_CONFIRM", "Patient identity confirmed"},
                    new String[]{"SITE_MARKED", "Procedure site marked"},
                    new String[]{"CONSENT", "Consent verified"},
                    new String[]{"ANAESTHESIA_CHECK", "Anaesthesia safety check complete"}),
            "TIME_OUT", List.of(
                    new String[]{"TEAM_INTRO", "Team introductions"},
                    new String[]{"PROCEDURE_CONFIRM", "Procedure confirmed"},
                    new String[]{"ANTIBIOTIC", "Antibiotic prophylaxis given"},
                    new String[]{"CRITICAL_EVENTS", "Critical events anticipated"}),
            "SIGN_OUT", List.of(
                    new String[]{"PROCEDURE_RECORD", "Procedure recorded"},
                    new String[]{"INSTRUMENT_COUNT", "Instrument/sponge count correct"},
                    new String[]{"SPECIMEN", "Specimen labelling"},
                    new String[]{"RECOVERY_PLAN", "Recovery plan discussed"}));

    private final ProcedureEpisodeRepository episodeRepository;
    private final ProcedurePreopAssessmentRepository preopRepository;
    private final ProcedureChecklistItemRepository checklistRepository;
    private final ProcedureIntraopEventRepository intraopRepository;
    private final ProcedurePostopRecordRepository postopRepository;
    // ── Wave 4 §12: time-series PACU (recovery) observation set ──
    private final ProcedurePacuObservationRepository pacuObservationRepository;
    private final ProcedureEpisodeDocumentRepository documentRepository;
    private final ProcedureConsumableRepository consumableRepository;
    // ── Wave 4 §5: surgical-case completion event carries implant/consumable/blood counts for COSTA ──
    private final ProcedureImplantRepository implantRepository;
    private final ProcedureAnaesthesiaScoreRepository anaesthesiaScoreRepository;
    // ── Wave 1 clinical-safety collaborators (count gate, anaesthesia chart, blood-channel projection) ──
    private final ProcedureCountRepository countRepository;
    private final ProcedureAnaesthesiaChartEntryRepository chartRepository;
    private final ProcedureBloodLinkRepository bloodLinkRepository;
    private final EventOutboxRepository outboxRepository;
    // ── Wave 2: perioperative consent bundle (references MVUMO, the consent SoR) ──
    private final ProcedureConsentRepository procedureConsentRepository;
    private final ObjectMapper objectMapper;

    public ProcedureEpisodeService(
            ProcedureEpisodeRepository episodeRepository,
            ProcedurePreopAssessmentRepository preopRepository,
            ProcedureChecklistItemRepository checklistRepository,
            ProcedureIntraopEventRepository intraopRepository,
            ProcedurePostopRecordRepository postopRepository,
            ProcedurePacuObservationRepository pacuObservationRepository,
            ProcedureEpisodeDocumentRepository documentRepository,
            ProcedureConsumableRepository consumableRepository,
            ProcedureImplantRepository implantRepository,
            ProcedureAnaesthesiaScoreRepository anaesthesiaScoreRepository,
            ProcedureCountRepository countRepository,
            ProcedureAnaesthesiaChartEntryRepository chartRepository,
            ProcedureBloodLinkRepository bloodLinkRepository,
            EventOutboxRepository outboxRepository,
            ProcedureConsentRepository procedureConsentRepository,
            ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.preopRepository = preopRepository;
        this.checklistRepository = checklistRepository;
        this.intraopRepository = intraopRepository;
        this.postopRepository = postopRepository;
        this.pacuObservationRepository = pacuObservationRepository;
        this.documentRepository = documentRepository;
        this.consumableRepository = consumableRepository;
        this.implantRepository = implantRepository;
        this.anaesthesiaScoreRepository = anaesthesiaScoreRepository;
        this.countRepository = countRepository;
        this.chartRepository = chartRepository;
        this.bloodLinkRepository = bloodLinkRepository;
        this.outboxRepository = outboxRepository;
        this.procedureConsentRepository = procedureConsentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createEpisode(Map<String, Object> body) {
        String cpid = requirePatient(body);
        String bookingId = ClinicalPayloadMapper.str(body, "bookingId", "booking_id");
        if (bookingId != null && !bookingId.isBlank()) {
            Optional<ProcedureEpisodeEntity> existing =
                    episodeRepository.findByTenantIdAndBookingId(currentTenant(), bookingId);
            if (existing.isPresent()) {
                return episodeDetail(existing.get().getEpisodeId());
            }
        }
        String name = Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "procedureName", "procedure_name"), "Procedure");
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setTenantId(currentTenant());
        e.setSubjectCpid(cpid);
        e.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        e.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        e.setBookingId(ClinicalPayloadMapper.str(body, "bookingId", "booking_id"));
        e.setTheatreId(ClinicalPayloadMapper.uuid(body, "theatreId", "theatre_id"));
        e.setProcedureName(name);
        e.setProcedureCode(ClinicalPayloadMapper.str(body, "procedureCode", "procedure_code"));
        e.setSurgeonId(ClinicalPayloadMapper.str(body, "surgeonId", "surgeon_id"));
        e.setAnaesthetistId(ClinicalPayloadMapper.str(body, "anaesthetistId", "anaesthetist_id"));
        e.setStatus("BOOKED");
        String scheduled = ClinicalPayloadMapper.str(body, "scheduledAt", "scheduled_at");
        if (scheduled != null) {
            e.setScheduledAt(OffsetDateTime.parse(scheduled));
        }
        e = episodeRepository.save(e);
        seedChecklist(e.getEpisodeId());
        return episodeDetail(e.getEpisodeId());
    }

    public List<Map<String, Object>> listEpisodes(String patientId) {
        return episodeRepository.findByTenantIdAndSubjectCpidOrderByScheduledAtDesc(currentTenant(), patientId)
                .stream().map(this::episodeSummary).toList();
    }

    public List<Map<String, Object>> listEpisodesForHistory(String patientId) {
        return episodeRepository.findByTenantIdAndSubjectCpidOrderByScheduledAtDesc(currentTenant(), patientId)
                .stream().map(this::historyRow).toList();
    }

    public Map<String, Object> getEpisode(UUID episodeId) {
        return episodeDetail(episodeId);
    }

    @Transactional
    public Map<String, Object> submitPreop(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        String type = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "assessmentType", "assessment_type"), "NURSING").toUpperCase();
        ProcedurePreopAssessmentEntity a = preopRepository.findByEpisodeIdAndAssessmentType(episodeId, type)
                .orElseGet(ProcedurePreopAssessmentEntity::new);
        a.setEpisodeId(episodeId);
        a.setAssessmentType(type);
        a.setAssessedBy(ClinicalPayloadMapper.str(body, "assessedBy", "assessed_by"));
        if ("ANAESTHESIA".equals(type)) {
            a.setAsaClass(ClinicalPayloadMapper.str(body, "asaClass", "asa_class"));
            a.setMallampatiClass(ClinicalPayloadMapper.str(body, "mallampatiClass", "mallampati_class"));
            a.setCormackLehaneGrade(ClinicalPayloadMapper.str(body, "cormackLehaneGrade", "cormack_lehane_grade"));
            String airway = ClinicalPayloadMapper.str(body, "airwayAssessment", "airway_assessment");
            if (airway == null || airway.isBlank()) {
                airway = buildAirwaySummary(a.getMallampatiClass(), a.getCormackLehaneGrade());
            }
            a.setAirwayAssessment(airway);
            a.setAnaesthetistNotes(ClinicalPayloadMapper.str(body, "anaesthetistNotes", "anaesthetist_notes", "notes"));
            a.setClearedForTheatre(Boolean.TRUE.equals(body.get("clearedForTheatre"))
                    || Boolean.parseBoolean(String.valueOf(body.getOrDefault("cleared_for_theatre", "false"))));
            preopRepository.save(a);
            persistScoreFromPreop(episodeId, body, a);
            if (a.isClearedForTheatre()) {
                autoCompleteAnaesthesiaChecklist(episodeId);
            }
        } else {
            a.setFastingVerified(body.get("fastingVerified") instanceof Boolean b ? b
                    : Boolean.parseBoolean(String.valueOf(body.getOrDefault("fasting_verified", "false"))));
            a.setAllergiesReviewed(body.get("allergiesReviewed") instanceof Boolean b ? b
                    : Boolean.parseBoolean(String.valueOf(body.getOrDefault("allergies_reviewed", "false"))));
            a.setNursingNotes(ClinicalPayloadMapper.str(body, "nursingNotes", "nursing_notes", "notes"));
        }
        preopRepository.save(a);
        if ("PREOP".equals(episode.getStatus()) || "BOOKED".equals(episode.getStatus())) {
            episode.setStatus("PREOP");
            episodeRepository.save(episode);
        }
        maybeAdvanceToReady(episodeId);
        return episodeDetail(episodeId);
    }

    @Transactional
    public Map<String, Object> completeChecklistItems(UUID episodeId, String phase, Map<String, Object> body) {
        requireEpisode(episodeId);
        String normalizedPhase = phase.toUpperCase();
        // ── Wave 1 clinical-safety: WHO Sign-Out surgical-count gate ──────────────────────────────
        // A retained-item risk (unreconciled swab/instrument/needle count) BLOCKS Sign-Out completion
        // unless the theatre team explicitly reconciles or overrides with a reason. Never silent.
        if ("SIGN_OUT".equals(normalizedPhase)) {
            enforceCountGate(episodeId, body);
        }
        @SuppressWarnings("unchecked")
        List<String> itemIds = body.get("itemIds") instanceof List<?> raw
                ? (List<String>) raw : List.of();
        String completedBy = ClinicalPayloadMapper.str(body, "completedBy", "completed_by");
        for (String itemId : itemIds) {
            checklistRepository.findById(UUID.fromString(itemId)).ifPresent(item -> {
                if (item.getEpisodeId().equals(episodeId) && item.getPhase().equals(normalizedPhase)) {
                    item.setCompleted(true);
                    item.setCompletedBy(completedBy);
                    item.setCompletedAt(OffsetDateTime.now());
                    checklistRepository.save(item);
                }
            });
        }
        maybeAdvanceToReady(episodeId);
        return Map.of("phase", normalizedPhase, "checklist", checklistForEpisode(episodeId));
    }

    @Transactional
    public Map<String, Object> bindConsentEvidence(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        UUID mvumoId = ClinicalPayloadMapper.uuid(body, "mvumoConsentRequestId", "mvumo_consent_request_id");
        if (mvumoId != null) {
            episode.setMvumoConsentRequestId(mvumoId);
        }
        UUID tshepoId = ClinicalPayloadMapper.uuid(body, "tshepoConsentId", "tshepo_consent_id");
        if (tshepoId != null) {
            episode.setTshepoConsentId(tshepoId);
        }
        String proofRef = ClinicalPayloadMapper.str(body, "consentProofRef", "consent_proof_ref", "proofRef", "proof_ref");
        if (proofRef != null) {
            episode.setConsentProofRef(proofRef);
        }
        String status = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "consentStatus", "consent_status"), "PENDING");

        // ── Wave 2: multi-type consent bundle. A consentType binds a specific
        // required consent (PROCEDURE/ANAESTHESIA/TRANSFUSION) to its MVUMO request.
        // The episode-level consent_status still reflects the PROCEDURE consent for
        // backward compatibility.
        String consentType = ClinicalPayloadMapper.str(body, "consentType", "consent_type");
        if (consentType != null && !consentType.isBlank()) {
            upsertConsentBundle(episodeId, consentType.toUpperCase(), mvumoId, status,
                    !Boolean.FALSE.equals(body.get("required")));
        }
        boolean isProcedureConsent = consentType == null || consentType.isBlank()
                || ProcedureConsentEntity.TYPE_PROCEDURE.equalsIgnoreCase(consentType);
        if (isProcedureConsent) {
            episode.setConsentStatus(status);
            if ("GRANTED".equals(status)) {
                episode.setConsentVerified(true);
                autoCompleteConsentChecklist(episodeId);
            }
        }
        episodeRepository.save(episode);
        return episodeDetail(episodeId);
    }

    /** Upsert a perioperative consent-bundle row (Wave 2). Idempotent per type. */
    private void upsertConsentBundle(UUID episodeId, String consentType, UUID mvumoId, String status,
                                     boolean required) {
        ProcedureConsentEntity row = procedureConsentRepository
                .findByEpisodeIdAndConsentType(episodeId, consentType)
                .orElseGet(() -> {
                    ProcedureConsentEntity fresh = new ProcedureConsentEntity();
                    fresh.setEpisodeId(episodeId);
                    fresh.setConsentType(consentType);
                    return fresh;
                });
        if (mvumoId != null) {
            row.setMvumoConsentRequestId(mvumoId);
        }
        row.setStatus(status);
        row.setRequired(required);
        row.setWithdrawn("WITHDRAWN".equals(status));
        procedureConsentRepository.save(row);
    }

    public Map<String, Object> getConsentStatus(UUID episodeId) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("episode_id", episodeId.toString());
        m.put("consent_status", episode.getConsentStatus());
        m.put("consent_verified", episode.isConsentVerified());
        m.put("mvumo_consent_request_id", episode.getMvumoConsentRequestId() != null
                ? episode.getMvumoConsentRequestId().toString() : null);
        m.put("tshepo_consent_id", episode.getTshepoConsentId() != null
                ? episode.getTshepoConsentId().toString() : null);
        m.put("consent_proof_ref", episode.getConsentProofRef());
        return m;
    }

    @Transactional
    public Map<String, Object> linkDocument(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        UUID objectId = ClinicalPayloadMapper.uuid(body, "documentObjectId", "document_object_id");
        if (objectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentObjectId is required");
        }
        ProcedureEpisodeDocumentEntity doc = new ProcedureEpisodeDocumentEntity();
        doc.setEpisodeId(episodeId);
        doc.setDocumentObjectId(objectId);
        doc.setDocumentType(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "documentType", "document_type"), "OPERATIVE_NOTE"));
        doc.setTitle(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "title"), "Operative note"));
        doc.setStorageKey(ClinicalPayloadMapper.str(body, "storageKey", "storage_key"));
        doc.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        doc = documentRepository.save(doc);
        return documentRow(doc);
    }

    public List<Map<String, Object>> listDocuments(UUID episodeId) {
        requireEpisode(episodeId);
        return documentRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId).stream()
                .map(this::documentRow).toList();
    }

    @Transactional
    public Map<String, Object> recordConsumable(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String itemCode = ClinicalPayloadMapper.str(body, "itemCode", "item_code");
        if (itemCode == null || itemCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemCode is required");
        }
        UUID facilityId = ClinicalPayloadMapper.uuid(body, "facilityId", "facility_id");
        UUID storeId = ClinicalPayloadMapper.uuid(body, "storeId", "store_id");
        if (facilityId == null || storeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facilityId and storeId are required");
        }
        Integer qtyObj = ClinicalPayloadMapper.integer(body, "quantity", "qty");
        if (qtyObj == null || qtyObj <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }
        ProcedureConsumableEntity row = new ProcedureConsumableEntity();
        row.setEpisodeId(episodeId);
        row.setItemCode(itemCode);
        row.setItemName(ClinicalPayloadMapper.str(body, "itemName", "item_name"));
        row.setQuantity(qtyObj);
        row.setFacilityId(facilityId);
        row.setStoreId(storeId);
        row.setInventoryLedgerRef(ClinicalPayloadMapper.str(body, "inventoryLedgerRef", "inventory_ledger_ref"));
        row.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        row = consumableRepository.save(row);
        return consumableRow(row);
    }

    public List<Map<String, Object>> listConsumables(UUID episodeId) {
        requireEpisode(episodeId);
        return consumableRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId).stream()
                .map(this::consumableRow).toList();
    }

    @Transactional
    public Map<String, Object> startProcedure(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        if (!List.of("READY_FOR_THEATRE", "PREOP", "BOOKED").contains(episode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Episode not ready for theatre");
        }
        // ── Wave 5b §15: emergency consent exception. Life-saving surgery must NOT be blocked on absent
        // consent. When the emergency-exception basis (deferred / proxy / two-doctor) has been recorded
        // and AUDITED (via TheatreService.recordEmergencyConsentException, which sets consent_status =
        // EMERGENCY_EXCEPTION + emergency_override), the GRANTED consent gate is bypassed. Consent is NOT
        // marked verified — honest: it was legally excepted, not obtained. Every override is auditable.
        boolean emergencyException = "EMERGENCY_EXCEPTION".equals(episode.getConsentStatus());
        if (emergencyException) {
            if (!episode.isEmergencyOverride()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Emergency consent exception requires an emergency override before theatre start");
            }
        } else {
            if (!"GRANTED".equals(episode.getConsentStatus()) || episode.getMvumoConsentRequestId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "MVUMO surgical consent must be GRANTED with evidence before theatre start");
            }
            // ── Wave 2: multi-type consent gate. Every REQUIRED consent in the bundle
            // must be GRANTED and none may be WITHDRAWN. Anaesthesia consent gates
            // start; transfusion consent gates the BLOOD readiness domain (Wave 1).
            for (ProcedureConsentEntity consent : procedureConsentRepository.findByEpisodeId(episodeId)) {
                if (!consent.isRequired()) {
                    continue;
                }
                if (consent.isWithdrawn()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            consent.getConsentType() + " consent has been WITHDRAWN — theatre start blocked");
                }
                if (!"GRANTED".equals(consent.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            consent.getConsentType() + " consent must be GRANTED before theatre start");
                }
            }
        }
        episode.setStatus("IN_PROGRESS");
        if (!emergencyException) {
            episode.setConsentVerified(true);
        }
        episodeRepository.save(episode);
        return episodeDetail(episodeId);
    }

    @Transactional
    public Map<String, Object> recordIntraopEvent(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        ProcedureIntraopEventEntity event = new ProcedureIntraopEventEntity();
        event.setEpisodeId(episodeId);
        event.setEventType(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "eventType", "event_type"), "NOTE"));
        event.setDescription(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "description"), "Intra-operative event"));
        event.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        Object vitals = body.get("vitals");
        if (vitals != null) {
            try {
                event.setVitalsJson(vitals instanceof String s ? s : objectMapper.writeValueAsString(vitals));
            } catch (JsonProcessingException ex) {
                event.setVitalsJson(vitals.toString());
            }
        }
        event = intraopRepository.save(event);
        return Map.of("id", event.getEventId().toString(), "event_type", event.getEventType());
    }

    public List<Map<String, Object>> listIntraopEvents(UUID episodeId) {
        requireEpisode(episodeId);
        return intraopRepository.findByEpisodeIdOrderByRecordedAtAsc(episodeId).stream()
                .map(this::intraopRow).toList();
    }

    @Transactional
    public Map<String, Object> enterPacu(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        episode.setStatus("PACU");
        episodeRepository.save(episode);
        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedurePostopRecordEntity::new);
        postop.setEpisodeId(episodeId);
        postop.setPacuArrivalAt(OffsetDateTime.now());
        postop.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        postopRepository.save(postop);
        return episodeDetail(episodeId);
    }

    @Transactional
    public Map<String, Object> completePostop(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedurePostopRecordEntity::new);
        postop.setEpisodeId(episodeId);
        postop.setAldreteScore(ClinicalPayloadMapper.integer(body, "aldreteScore", "aldrete_score"));
        postop.setPainScore(ClinicalPayloadMapper.integer(body, "painScore", "pain_score"));
        postop.setComplications(ClinicalPayloadMapper.str(body, "complications"));
        postop.setDisposition(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "disposition"), "WARD"));
        postop.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        Object orders = body.get("postopOrders");
        if (orders != null) {
            try {
                postop.setPostopOrdersJson(orders instanceof String s ? s : objectMapper.writeValueAsString(orders));
            } catch (JsonProcessingException ex) {
                postop.setPostopOrdersJson(orders.toString());
            }
        }
        postopRepository.save(postop);
        String pacuRecordedBy = Objects.requireNonNullElse(postop.getRecordedBy(), "pacu-submit");
        if (postop.getAldreteScore() != null) {
            recordAnaesthesiaScore(episodeId, Map.of(
                    "scoreType", "ALDRETE",
                    "phase", "PACU",
                    "components", Map.of("aldreteScore", postop.getAldreteScore()),
                    "recordedBy", pacuRecordedBy));
        }
        if (postop.getPainScore() != null) {
            recordAnaesthesiaScore(episodeId, Map.of(
                    "scoreType", "PAIN",
                    "phase", "PACU",
                    "components", Map.of("painScore", postop.getPainScore()),
                    "recordedBy", pacuRecordedBy));
        }
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        episode.setStatus("RECOVERED");
        episodeRepository.save(episode);
        return episodeDetail(episodeId);
    }

    @Transactional
    public Map<String, Object> completeEpisode(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        episode.setStatus("COMPLETED");
        episode.setCompletedAt(OffsetDateTime.now());
        episodeRepository.save(episode);

        // Wave 4 §5 — theatre completion is the COSTA value trigger: emit theatre.case.completed carrying
        // the billable composition (theatre time + anaesthesia + implant/consumable/blood usage counts).
        int theatreMinutes = Optional.ofNullable(ClinicalPayloadMapper.integer(body, "theatreMinutes", "theatre_minutes"))
                .orElse(60);
        boolean hasAnaesthesia = episode.getAnaesthetistId() != null
                || Boolean.TRUE.equals(body.get("hasAnaesthesia")) || Boolean.TRUE.equals(body.get("has_anaesthesia"));
        int implantCount = implantRepository.countByEpisodeId(episodeId);
        int consumableCount = consumableRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId).size();
        int bloodUnits = bloodLinkRepository.findByEpisodeIdOrderByCreatedAtAsc(episodeId).size();
        UUID facilityId = currentFacility();
        Map<String, Object> completed = new LinkedHashMap<>();
        // event_type + tenant_id are injected by appendOutbox so the legacy inpatient.events consumers
        // (COSTA billing + reporting) can filter/synthesize context from the raw payload map.
        completed.put("episode_id", episodeId.toString());
        completed.put("patient_id", episode.getSubjectCpid());
        completed.put("encounter_id", episode.getEncounterId() != null ? episode.getEncounterId().toString() : "");
        completed.put("facility_id", facilityId != null ? facilityId.toString() : "");
        completed.put("procedure_code", episode.getProcedureCode() != null ? episode.getProcedureCode() : "");
        completed.put("procedure_name", episode.getProcedureName() != null ? episode.getProcedureName() : "");
        completed.put("theatre_minutes", theatreMinutes);
        completed.put("has_anaesthesia", hasAnaesthesia);
        completed.put("implant_count", implantCount);
        completed.put("consumable_count", consumableCount);
        completed.put("blood_units", bloodUnits);
        // Wave 5b: carry the trauma_episode_id (when trauma-originated) so the daidzai episode-timeline
        // consumer correlates the COMPLETED theatre phase to the ONE shared trauma episode.
        if (episode.getTraumaEpisodeId() != null) {
            completed.put("trauma_episode_id", episode.getTraumaEpisodeId().toString());
        }
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.completed", completed);
        return episodeDetail(episodeId);
    }

    // ── Wave 4 §12: time-series PACU recovery observations + Aldrete discharge-readiness ─────────────
    /**
     * Record one time-series PACU observation (airway/breathing/haemodynamics/consciousness/pain/
     * nausea/bleeding/temperature/fluids/drains/medications). When an Aldrete score is supplied, the
     * discharge-readiness band is computed via {@link AnaesthesiaScoringEngine} and mirrored onto the
     * postop record so the discharge decision can gate on it. The patient stays a tracked case: the
     * PACU stay is its own phase, not an afterthought once surgery is COMPLETED.
     */
    @Transactional
    public Map<String, Object> recordPacuObservation(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        ProcedurePacuObservationEntity obs = new ProcedurePacuObservationEntity();
        obs.setTenantId(episode.getTenantId());
        obs.setEpisodeId(episodeId);
        obs.setSeq(pacuObservationRepository.countByEpisodeId(episodeId) + 1);
        obs.setAirway(ClinicalPayloadMapper.str(body, "airway"));
        obs.setBreathing(ClinicalPayloadMapper.str(body, "breathing"));
        obs.setSpo2(ClinicalPayloadMapper.integer(body, "spo2", "oxygen_saturation"));
        obs.setSystolicBp(ClinicalPayloadMapper.integer(body, "systolicBp", "systolic_bp", "sbp"));
        obs.setDiastolicBp(ClinicalPayloadMapper.integer(body, "diastolicBp", "diastolic_bp", "dbp"));
        obs.setHeartRate(ClinicalPayloadMapper.integer(body, "heartRate", "heart_rate", "hr"));
        obs.setConsciousness(ClinicalPayloadMapper.str(body, "consciousness", "loc"));
        obs.setPainScore(ClinicalPayloadMapper.integer(body, "painScore", "pain_score"));
        obs.setNauseaVomiting(ClinicalPayloadMapper.str(body, "nauseaVomiting", "nausea_vomiting", "ponv"));
        obs.setBleedingWound(ClinicalPayloadMapper.str(body, "bleedingWound", "bleeding_wound", "wound"));
        Object temp = body.containsKey("temperatureC") ? body.get("temperatureC")
                : body.get("temperature_c") != null ? body.get("temperature_c") : body.get("temperature");
        if (temp != null) {
            try { obs.setTemperatureC(new java.math.BigDecimal(String.valueOf(temp))); }
            catch (NumberFormatException ignored) { /* leave null */ }
        }
        obs.setUrineOutputMl(ClinicalPayloadMapper.integer(body, "urineOutputMl", "urine_output_ml"));
        obs.setFluids(ClinicalPayloadMapper.str(body, "fluids"));
        obs.setDrainsDevices(ClinicalPayloadMapper.str(body, "drainsDevices", "drains_devices", "drains"));
        obs.setMedications(ClinicalPayloadMapper.str(body, "medications", "meds"));
        obs.setNotes(ClinicalPayloadMapper.str(body, "notes"));
        obs.setRecordedBy(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"), currentActor()));

        Integer aldrete = ClinicalPayloadMapper.integer(body, "aldreteScore", "aldrete_score");
        obs.setAldreteScore(aldrete);
        if (aldrete != null) {
            AnaesthesiaScoringEngine.ScoreResult r =
                    AnaesthesiaScoringEngine.calculate("ALDRETE", "PACU", Map.of("aldreteScore", aldrete));
            obs.setDischargeReadinessBand(r.riskBand());
        }
        pacuObservationRepository.save(obs);

        // Mirror the latest scored snapshot onto the postop record (the discharge-readiness gate reads it).
        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedurePostopRecordEntity::new);
        postop.setEpisodeId(episodeId);
        if (postop.getPacuArrivalAt() == null) postop.setPacuArrivalAt(OffsetDateTime.now());
        if (aldrete != null) {
            postop.setAldreteScore(aldrete);
            postop.setDischargeReadinessScore(aldrete);
            postop.setDischargeReadinessBand(obs.getDischargeReadinessBand());
            // keep the anaesthesia score chart continuous
            recordAnaesthesiaScore(episodeId, Map.of("scoreType", "ALDRETE", "phase", "PACU",
                    "components", Map.of("aldreteScore", aldrete), "recordedBy", obs.getRecordedBy()));
        }
        if (obs.getPainScore() != null) {
            postop.setPainScore(obs.getPainScore());
            recordAnaesthesiaScore(episodeId, Map.of("scoreType", "PAIN", "phase", "PACU",
                    "components", Map.of("painScore", obs.getPainScore()), "recordedBy", obs.getRecordedBy()));
        }
        postopRepository.save(postop);
        return pacuObservationRow(obs);
    }

    public List<Map<String, Object>> listPacuObservations(UUID episodeId) {
        requireEpisode(episodeId);
        return pacuObservationRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream()
                .map(this::pacuObservationRow).toList();
    }

    /**
     * Aldrete-based PACU discharge readiness (Wave 4 §12). Uses the most-recent scored observation;
     * band LOW (Aldrete ≥ 9) = ready for ward, MODERATE = continue monitoring, HIGH = remain in PACU.
     */
    public Map<String, Object> computeDischargeReadiness(UUID episodeId) {
        requireEpisode(episodeId);
        Optional<ProcedurePacuObservationEntity> latest = pacuObservationRepository
                .findByEpisodeIdOrderBySeqAsc(episodeId).stream()
                .filter(o -> o.getAldreteScore() != null)
                .reduce((a, b) -> b);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode_id", episodeId.toString());
        if (latest.isEmpty()) {
            out.put("ready", false);
            out.put("band", "UNSCORED");
            out.put("interpretation", "No Aldrete score recorded — record a PACU observation with an Aldrete score");
            return out;
        }
        ProcedurePacuObservationEntity o = latest.get();
        AnaesthesiaScoringEngine.ScoreResult r = AnaesthesiaScoringEngine.calculate(
                "ALDRETE", "PACU", Map.of("aldreteScore", o.getAldreteScore()));
        out.put("aldrete_score", o.getAldreteScore());
        out.put("band", r.riskBand());
        out.put("ready", "LOW".equals(r.riskBand()));
        out.put("interpretation", r.interpretation());
        return out;
    }

    /**
     * Return-to-theatre: a PACU/recovery patient who deteriorates and needs re-operation goes back to a
     * re-operative state (READY_FOR_THEATRE) rather than being closed. Keeps the SAME episode/case.
     */
    @Transactional
    public Map<String, Object> returnToTheatre(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        if (!List.of("PACU", "RECOVERED", "IN_PROGRESS").contains(episode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Return-to-theatre only from a PACU/RECOVERED case (was " + episode.getStatus() + ")");
        }
        episode.setStatus("READY_FOR_THEATRE");
        episodeRepository.save(episode);
        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedurePostopRecordEntity::new);
        postop.setEpisodeId(episodeId);
        postop.setReturnToTheatre(true);
        postopRepository.save(postop);
        return episodeDetail(episodeId);
    }

    private Map<String, Object> pacuObservationRow(ProcedurePacuObservationEntity o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getObservationId() != null ? o.getObservationId().toString() : null);
        m.put("seq", o.getSeq());
        m.put("observed_at", o.getObservedAt());
        m.put("airway", o.getAirway());
        m.put("breathing", o.getBreathing());
        m.put("spo2", o.getSpo2());
        m.put("systolic_bp", o.getSystolicBp());
        m.put("diastolic_bp", o.getDiastolicBp());
        m.put("heart_rate", o.getHeartRate());
        m.put("consciousness", o.getConsciousness());
        m.put("pain_score", o.getPainScore());
        m.put("nausea_vomiting", o.getNauseaVomiting());
        m.put("bleeding_wound", o.getBleedingWound());
        m.put("temperature_c", o.getTemperatureC());
        m.put("urine_output_ml", o.getUrineOutputMl());
        m.put("fluids", o.getFluids());
        m.put("drains_devices", o.getDrainsDevices());
        m.put("medications", o.getMedications());
        m.put("aldrete_score", o.getAldreteScore());
        m.put("discharge_readiness_band", o.getDischargeReadinessBand());
        m.put("notes", o.getNotes());
        m.put("recorded_by", o.getRecordedBy());
        return m;
    }

    private void seedChecklist(UUID episodeId) {
        for (var entry : WHO_CHECKLIST.entrySet()) {
            for (String[] item : entry.getValue()) {
                ProcedureChecklistItemEntity row = new ProcedureChecklistItemEntity();
                row.setEpisodeId(episodeId);
                row.setPhase(entry.getKey());
                row.setItemCode(item[0]);
                row.setItemLabel(item[1]);
                checklistRepository.save(row);
            }
        }
    }

    private void maybeAdvanceToReady(UUID episodeId) {
        ProcedureEpisodeEntity episode = requireEpisode(episodeId);
        boolean nursing = preopRepository.findByEpisodeIdAndAssessmentType(episodeId, "NURSING")
                .map(a -> a.getFastingVerified() != null && a.getFastingVerified()).orElse(false);
        boolean anaesthesia = preopRepository.findByEpisodeIdAndAssessmentType(episodeId, "ANAESTHESIA")
                .map(ProcedurePreopAssessmentEntity::isClearedForTheatre).orElse(false);
        boolean signIn = phaseComplete(episodeId, "SIGN_IN");
        if (nursing && anaesthesia && signIn && !"IN_PROGRESS".equals(episode.getStatus())
                && !"PACU".equals(episode.getStatus()) && !"RECOVERED".equals(episode.getStatus())
                && !"COMPLETED".equals(episode.getStatus())) {
            episode.setStatus("READY_FOR_THEATRE");
            episodeRepository.save(episode);
        } else if (("BOOKED".equals(episode.getStatus()) || "READY_FOR_THEATRE".equals(episode.getStatus()))
                && (nursing || anaesthesia)) {
            episode.setStatus("PREOP");
            episodeRepository.save(episode);
        }
    }

    private boolean phaseComplete(UUID episodeId, String phase) {
        List<ProcedureChecklistItemEntity> items = checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, phase);
        return !items.isEmpty() && items.stream().allMatch(ProcedureChecklistItemEntity::isCompleted);
    }

    private Map<String, Object> episodeDetail(UUID episodeId) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        Map<String, Object> m = episodeSummary(e);
        m.put("preop_assessments", preopRepository.findByEpisodeIdOrderByAssessedAtAsc(episodeId)
                .stream().map(this::preopRow).toList());
        m.put("checklist", checklistForEpisode(episodeId));
        m.put("intraop_events", intraopRepository.findByEpisodeIdOrderByRecordedAtAsc(episodeId)
                .stream().map(this::intraopRow).toList());
        postopRepository.findByEpisodeId(episodeId).ifPresent(p -> m.put("postop", postopRow(p)));
        m.put("pacu_observations", pacuObservationRepository.findByEpisodeIdOrderBySeqAsc(episodeId)
                .stream().map(this::pacuObservationRow).toList());
        m.put("discharge_readiness", computeDischargeReadiness(episodeId));
        m.put("documents", listDocuments(episodeId));
        m.put("consumables", listConsumables(episodeId));
        m.put("anaesthesia_scores", listAnaesthesiaScores(episodeId));
        m.put("anaesthesia_score_suggestions", suggestAnaesthesiaScores(episodeId));
        return m;
    }

    public List<Map<String, Object>> suggestAnaesthesiaScores(UUID episodeId) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String asa = preopRepository.findByEpisodeIdAndAssessmentType(episodeId, "ANAESTHESIA")
                .map(ProcedurePreopAssessmentEntity::getAsaClass).orElse(null);
        return AnaesthesiaScoringEngine.suggestScores(e.getProcedureName(), e.getProcedureCode(), asa)
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("score_type", s.scoreType());
                    m.put("reason", s.reason());
                    m.put("recommended", s.recommended());
                    m.put("priority", s.priority());
                    return m;
                }).toList();
    }

    public List<Map<String, Object>> listAnaesthesiaScores(UUID episodeId) {
        requireEpisode(episodeId);
        return anaesthesiaScoreRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId)
                .stream().map(this::anaesthesiaScoreRow).toList();
    }

    @Transactional
    public Map<String, Object> recordAnaesthesiaScore(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String scoreType = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "scoreType", "score_type"), "").toUpperCase();
        if (scoreType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scoreType is required");
        }
        String phase = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "phase"), "PREOP").toUpperCase();
        @SuppressWarnings("unchecked")
        Map<String, Object> components = body.get("components") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw : body;
        AnaesthesiaScoringEngine.ScoreResult result =
                AnaesthesiaScoringEngine.calculate(scoreType, phase, components);
        ProcedureAnaesthesiaScoreEntity row = new ProcedureAnaesthesiaScoreEntity();
        row.setEpisodeId(episodeId);
        row.setScoreType(result.scoreType());
        row.setPhase(result.phase());
        row.setTotalScore(result.totalScore());
        row.setInterpretation(result.interpretation());
        row.setRiskBand(result.riskBand());
        row.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        try {
            row.setComponentsJson(objectMapper.writeValueAsString(result.components()));
        } catch (JsonProcessingException ex) {
            row.setComponentsJson(result.components().toString());
        }
        row = anaesthesiaScoreRepository.save(row);
        syncPreopFromScore(episodeId, result);
        return anaesthesiaScoreRow(row);
    }

    @Transactional
    public Map<String, Object> recordIntraopVitals(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        AnaesthesiaScoringEngine.ScoreResult vitals =
                AnaesthesiaScoringEngine.calculate("INTRAOP_VITALS", "INTRAOP", body);
        ProcedureAnaesthesiaScoreEntity row = new ProcedureAnaesthesiaScoreEntity();
        row.setEpisodeId(episodeId);
        row.setScoreType("INTRAOP_VITALS");
        row.setPhase("INTRAOP");
        row.setTotalScore(vitals.totalScore());
        row.setInterpretation(vitals.interpretation());
        row.setRiskBand(vitals.riskBand());
        row.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        try {
            row.setComponentsJson(objectMapper.writeValueAsString(vitals.components()));
        } catch (JsonProcessingException ex) {
            row.setComponentsJson(vitals.components().toString());
        }
        anaesthesiaScoreRepository.save(row);

        ProcedureIntraopEventEntity event = new ProcedureIntraopEventEntity();
        event.setEpisodeId(episodeId);
        event.setEventType("VITALS");
        event.setDescription(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "description"), vitals.interpretation()));
        event.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        try {
            event.setVitalsJson(objectMapper.writeValueAsString(vitals.components()));
        } catch (JsonProcessingException ex) {
            event.setVitalsJson(vitals.components().toString());
        }
        intraopRepository.save(event);

        // ── Wave 1 clinical-safety: also append a VITAL entry on the multi-channel anaesthesia chart ──
        ProcedureAnaesthesiaChartEntryEntity vitalEntry = newChartEntry(episodeId, "VITAL");
        vitalEntry.setLabel("Intra-op vitals");
        vitalEntry.setValueText(vitals.interpretation());
        vitalEntry.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        try {
            vitalEntry.setDetailJson(objectMapper.writeValueAsString(vitals.components()));
        } catch (JsonProcessingException ex) {
            vitalEntry.setDetailJson(String.valueOf(vitals.components()));
        }
        chartRepository.save(vitalEntry);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.anaesthesia.charted",
                Map.of("episode_id", episodeId.toString(), "channel", "VITAL", "seq", vitalEntry.getSeq()));
        return Map.of("score", anaesthesiaScoreRow(row), "event_type", "VITALS");
    }

    // ── Wave 1 clinical-safety: anaesthesia chart (multi-channel time series) ──────────────────────
    @Transactional
    public Map<String, Object> recordChartEntry(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String channel = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "channel"), "EVENT").toUpperCase();
        if (!Set.of("AGENT", "FLUID", "BLOOD", "MEDICATION", "MONITORING", "VITAL", "EVENT").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown anaesthesia chart channel: " + channel);
        }
        ProcedureAnaesthesiaChartEntryEntity entry = newChartEntry(episodeId, channel);
        entry.setTechnique(normaliseTechnique(ClinicalPayloadMapper.str(body, "technique")));
        entry.setLabel(ClinicalPayloadMapper.str(body, "label"));
        entry.setValueText(ClinicalPayloadMapper.str(body, "valueText", "value_text"));
        entry.setUnit(ClinicalPayloadMapper.str(body, "unit"));
        Object v = body.getOrDefault("valueNumeric", body.get("value_numeric"));
        if (v != null && !String.valueOf(v).isBlank()) {
            try { entry.setValueNumeric(new java.math.BigDecimal(String.valueOf(v))); }
            catch (NumberFormatException ignored) { /* leave null — keep the text value */ }
        }
        entry.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        Object detail = body.get("detail");
        if (detail != null) {
            try { entry.setDetailJson(detail instanceof String s ? s : objectMapper.writeValueAsString(detail)); }
            catch (JsonProcessingException ex) { entry.setDetailJson(String.valueOf(detail)); }
        }
        entry.setIdempotencyKey(ClinicalPayloadMapper.str(body, "idempotencyKey", "idempotency_key"));
        chartRepository.save(entry);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.anaesthesia.charted",
                Map.of("episode_id", episodeId.toString(), "channel", channel, "seq", entry.getSeq()));
        return chartEntryRow(entry);
    }

    /**
     * Ordered multi-channel anaesthesia chart. Persisted chart entries + a PROJECTED BLOOD channel
     * derived from procedure_blood_link (madi_transfusion_episode_id) so administered blood shows on
     * the chart without duplicate entry (single source of truth: MADI owns the transfusion episode).
     */
    public Map<String, Object> getAnaesthesiaChart(UUID episodeId) {
        requireEpisode(episodeId);
        List<Map<String, Object>> series = new ArrayList<>();
        for (ProcedureAnaesthesiaChartEntryEntity e : chartRepository.findByEpisodeIdOrderByRecordedAtAscChannelAsc(episodeId)) {
            series.add(chartEntryRow(e));
        }
        // Project the BLOOD channel from the blood link (no re-entry).
        for (ProcedureBloodLinkEntity link : bloodLinkRepository.findByEpisodeIdOrderByCreatedAtAsc(episodeId)) {
            if (link.getMadiTransfusionEpisodeId() != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("channel", "BLOOD");
                m.put("projected", true);
                m.put("madi_transfusion_episode_id", link.getMadiTransfusionEpisodeId());
                m.put("label", link.getComponentType() != null ? link.getComponentType() : "Blood");
                m.put("value_text", link.getStatus());
                m.put("unit", link.getUnitsRequested() != null ? "units:" + link.getUnitsRequested() : null);
                m.put("recorded_at", link.getUpdatedAt());
                series.add(m);
            }
        }
        series.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("recorded_at", ""))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode_id", episodeId.toString());
        out.put("entries", series);
        return out;
    }

    private void persistScoreFromPreop(UUID episodeId, Map<String, Object> body, ProcedurePreopAssessmentEntity a) {
        if (a.getAsaClass() != null) {
            recordAnaesthesiaScore(episodeId, Map.of(
                    "scoreType", "ASA",
                    "phase", "PREOP",
                    "components", Map.of("asaClass", a.getAsaClass()),
                    "recordedBy", a.getAssessedBy() != null ? a.getAssessedBy() : currentActor()));
        }
        if (a.getMallampatiClass() != null) {
            recordAnaesthesiaScore(episodeId, Map.of(
                    "scoreType", "MALLAMPATI",
                    "phase", "PREOP",
                    "components", Map.of("mallampatiClass", a.getMallampatiClass()),
                    "recordedBy", a.getAssessedBy() != null ? a.getAssessedBy() : currentActor()));
        }
        if (a.getCormackLehaneGrade() != null) {
            recordAnaesthesiaScore(episodeId, Map.of(
                    "scoreType", "CORMACK_LEHANE",
                    "phase", "PREOP",
                    "components", Map.of("cormackLehaneGrade", a.getCormackLehaneGrade()),
                    "recordedBy", a.getAssessedBy() != null ? a.getAssessedBy() : currentActor()));
        }
        Object extraScores = body.get("scores");
        if (extraScores instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> scoreBody) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    scoreBody.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    copy.putIfAbsent("phase", "PREOP");
                    copy.putIfAbsent("recordedBy", a.getAssessedBy());
                    recordAnaesthesiaScore(episodeId, copy);
                }
            }
        }
    }

    private void syncPreopFromScore(UUID episodeId, AnaesthesiaScoringEngine.ScoreResult result) {
        if (!"PREOP".equals(result.phase())) {
            return;
        }
        preopRepository.findByEpisodeIdAndAssessmentType(episodeId, "ANAESTHESIA").ifPresent(a -> {
            switch (result.scoreType()) {
                case "ASA" -> a.setAsaClass(String.valueOf(result.components().getOrDefault("asa_class", "")));
                case "MALLAMPATI" -> a.setMallampatiClass(String.valueOf(result.components().getOrDefault("mallampati_class", "")));
                case "CORMACK_LEHANE" -> a.setCormackLehaneGrade(String.valueOf(result.components().getOrDefault("cormack_lehane_grade", "")));
                default -> { /* calculated scores only in score table */ }
            }
            if (a.getMallampatiClass() != null || a.getCormackLehaneGrade() != null) {
                a.setAirwayAssessment(buildAirwaySummary(a.getMallampatiClass(), a.getCormackLehaneGrade()));
            }
            preopRepository.save(a);
        });
    }

    private static String buildAirwaySummary(String mallampati, String cormack) {
        StringBuilder sb = new StringBuilder();
        if (mallampati != null && !mallampati.isBlank()) {
            sb.append("Mallampati ").append(mallampati);
        }
        if (cormack != null && !cormack.isBlank()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append("Cormack-Lehane ").append(cormack);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void autoCompleteAnaesthesiaChecklist(UUID episodeId) {
        checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, "SIGN_IN").stream()
                .filter(item -> "ANAESTHESIA_CHECK".equals(item.getItemCode()) && !item.isCompleted())
                .forEach(item -> {
                    item.setCompleted(true);
                    item.setCompletedBy(currentActor());
                    item.setCompletedAt(OffsetDateTime.now());
                    checklistRepository.save(item);
                });
    }

    private Map<String, Object> anaesthesiaScoreRow(ProcedureAnaesthesiaScoreEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getScoreId().toString());
        m.put("score_type", row.getScoreType());
        m.put("phase", row.getPhase());
        m.put("total_score", row.getTotalScore());
        m.put("interpretation", row.getInterpretation());
        m.put("risk_band", row.getRiskBand());
        m.put("components_json", row.getComponentsJson());
        m.put("recorded_by", row.getRecordedBy());
        m.put("recorded_at", row.getRecordedAt());
        return m;
    }

    private void autoCompleteConsentChecklist(UUID episodeId) {
        checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, "SIGN_IN").stream()
                .filter(item -> "CONSENT".equals(item.getItemCode()) && !item.isCompleted())
                .forEach(item -> {
                    item.setCompleted(true);
                    item.setCompletedBy(currentActor());
                    item.setCompletedAt(OffsetDateTime.now());
                    checklistRepository.save(item);
                });
        maybeAdvanceToReady(episodeId);
    }

    private List<Map<String, Object>> checklistForEpisode(UUID episodeId) {
        return checklistRepository.findByEpisodeIdOrderByPhaseAscItemCodeAsc(episodeId)
                .stream().map(this::checklistRow).toList();
    }

    private ProcedureEpisodeEntity requireEpisode(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedure episode not found"));
    }

    /**
     * WHO Sign-Out surgical-count gate. Throws 409 when any swab/instrument/needle count is
     * unreconciled (discrepancy != 0 and not overridden). An explicit override reason on the request
     * (countOverrideReason) reconciles-by-override the outstanding counts and lets Sign-Out proceed —
     * the override + reason are persisted for audit. Real retained-item safety is never bypassed silently.
     */
    private void enforceCountGate(UUID episodeId, Map<String, Object> body) {
        List<ProcedureCountEntity> unreconciled =
                countRepository.findByEpisodeIdAndReconciledFalseAndOverrideFalse(episodeId);
        if (unreconciled.isEmpty()) {
            return;
        }
        String overrideReason = ClinicalPayloadMapper.str(body,
                "countOverrideReason", "count_override_reason", "overrideReason", "override_reason");
        if (overrideReason == null || overrideReason.isBlank()) {
            String kinds = unreconciled.stream().map(ProcedureCountEntity::getKind).distinct()
                    .reduce((a, b) -> a + "," + b).orElse("");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "WHO Sign-Out blocked: unreconciled surgical count(s) [" + kinds
                            + "] — reconcile the count or supply countOverrideReason to override");
        }
        for (ProcedureCountEntity c : unreconciled) {
            c.setOverride(true);
            c.setOverrideReason(overrideReason);
            countRepository.save(c);
        }
    }

    private ProcedureAnaesthesiaChartEntryEntity newChartEntry(UUID episodeId, String channel) {
        ProcedureAnaesthesiaChartEntryEntity entry = new ProcedureAnaesthesiaChartEntryEntity();
        entry.setTenantId(currentTenant());
        entry.setEpisodeId(episodeId);
        entry.setChannel(channel);
        entry.setSeq(chartRepository.countByEpisodeIdAndChannel(episodeId, channel) + 1);
        entry.setRecordedAt(OffsetDateTime.now());
        return entry;
    }

    private static String normaliseTechnique(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return Set.of("GA", "REGIONAL_SPINAL", "LOCAL_SEDATION", "CONVERSION", "COMPLICATION").contains(t) ? t : null;
    }

    private Map<String, Object> chartEntryRow(ProcedureAnaesthesiaChartEntryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEntryId().toString());
        m.put("channel", e.getChannel());
        m.put("seq", e.getSeq());
        m.put("technique", e.getTechnique());
        m.put("label", e.getLabel());
        m.put("value_numeric", e.getValueNumeric());
        m.put("value_text", e.getValueText());
        m.put("unit", e.getUnit());
        m.put("detail_json", e.getDetailJson());
        m.put("recorded_at", e.getRecordedAt());
        m.put("recorded_by", e.getRecordedBy());
        return m;
    }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setTenantId(currentTenant().toString());
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        try {
            // Inject event_type + tenant_id so downstream consumers of the LEGACY inpatient.events
            // stream (which receives the raw payload map, not an envelope) — COSTA billing + reporting
            // projection — can self-describe/filter every theatre event.
            Map<String, Object> enriched = new LinkedHashMap<>(payload);
            enriched.put("event_type", eventType);
            enriched.put("tenant_id", currentTenant().toString());
            outbox.setPayloadJson(objectMapper.writeValueAsString(enriched));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize anaesthesia-chart outbox payload", ex);
        }
        outboxRepository.save(outbox);
    }

    private static String requirePatient(Map<String, Object> body) {
        String cpid = ClinicalPayloadMapper.str(body, "patientId", "patient_id", "subjectCpid", "subject_cpid");
        if (cpid == null || cpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        return cpid;
    }

    private Map<String, Object> episodeSummary(ProcedureEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEpisodeId().toString());
        m.put("patient_id", e.getSubjectCpid());
        m.put("encounter_id", e.getEncounterId() != null ? e.getEncounterId().toString() : null);
        m.put("procedure_name", e.getProcedureName());
        m.put("procedure_code", e.getProcedureCode());
        m.put("status", e.getStatus());
        m.put("scheduled_at", e.getScheduledAt());
        m.put("theatre_id", e.getTheatreId() != null ? e.getTheatreId().toString() : null);
        m.put("surgeon_id", e.getSurgeonId());
        m.put("anaesthetist_id", e.getAnaesthetistId());
        m.put("consent_verified", e.isConsentVerified());
        m.put("consent_status", e.getConsentStatus());
        m.put("mvumo_consent_request_id", e.getMvumoConsentRequestId() != null
                ? e.getMvumoConsentRequestId().toString() : null);
        m.put("tshepo_consent_id", e.getTshepoConsentId() != null ? e.getTshepoConsentId().toString() : null);
        m.put("consent_proof_ref", e.getConsentProofRef());
        m.put("booking_id", e.getBookingId());
        // Wave 4 §13 — theatre→inpatient continuity: the case references its inpatient admission episode.
        m.put("admission_ref", e.getAdmissionRef() != null ? e.getAdmissionRef().toString() : null);
        return m;
    }

    private Map<String, Object> documentRow(ProcedureEpisodeDocumentEntity doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getDocumentLinkId().toString());
        m.put("document_object_id", doc.getDocumentObjectId().toString());
        m.put("document_type", doc.getDocumentType());
        m.put("title", doc.getTitle());
        m.put("storage_key", doc.getStorageKey());
        m.put("recorded_by", doc.getRecordedBy());
        m.put("recorded_at", doc.getRecordedAt());
        return m;
    }

    private Map<String, Object> consumableRow(ProcedureConsumableEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getConsumableId().toString());
        m.put("item_code", row.getItemCode());
        m.put("item_name", row.getItemName());
        m.put("quantity", row.getQuantity());
        m.put("facility_id", row.getFacilityId().toString());
        m.put("store_id", row.getStoreId().toString());
        m.put("inventory_ledger_ref", row.getInventoryLedgerRef());
        m.put("recorded_by", row.getRecordedBy());
        m.put("recorded_at", row.getRecordedAt());
        return m;
    }

    private Map<String, Object> historyRow(ProcedureEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEpisodeId().toString());
        m.put("name", e.getProcedureName());
        m.put("type", e.getProcedureCode() != null ? e.getProcedureCode() : "Surgical");
        m.put("date", e.getScheduledAt() != null ? e.getScheduledAt().toLocalDate().toString()
                : e.getCreatedAt().toLocalDate().toString());
        m.put("surgeon", e.getSurgeonId() != null ? e.getSurgeonId() : "—");
        m.put("facility", currentFacility() != null ? currentFacility().toString() : null);
        m.put("status", mapHistoryStatus(e.getStatus()));
        m.put("notes", "");
        return m;
    }

    private static String mapHistoryStatus(String status) {
        return switch (status) {
            case "COMPLETED", "RECOVERED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            case "IN_PROGRESS", "PACU" -> "In Progress";
            default -> "Scheduled";
        };
    }

    private Map<String, Object> preopRow(ProcedurePreopAssessmentEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getAssessmentId().toString());
        m.put("assessment_type", a.getAssessmentType());
        m.put("asa_class", a.getAsaClass());
        m.put("airway_assessment", a.getAirwayAssessment());
        m.put("mallampati_class", a.getMallampatiClass());
        m.put("cormack_lehane_grade", a.getCormackLehaneGrade());
        m.put("anaesthetist_notes", a.getAnaesthetistNotes());
        m.put("fasting_verified", a.getFastingVerified());
        m.put("allergies_reviewed", a.getAllergiesReviewed());
        m.put("cleared_for_theatre", a.isClearedForTheatre());
        m.put("assessed_by", a.getAssessedBy());
        m.put("assessed_at", a.getAssessedAt());
        return m;
    }

    private Map<String, Object> checklistRow(ProcedureChecklistItemEntity item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getItemId().toString());
        m.put("phase", item.getPhase());
        m.put("item_code", item.getItemCode());
        m.put("item_label", item.getItemLabel());
        m.put("completed", item.isCompleted());
        m.put("completed_by", item.getCompletedBy());
        m.put("completed_at", item.getCompletedAt());
        return m;
    }

    private Map<String, Object> intraopRow(ProcedureIntraopEventEntity event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", event.getEventId().toString());
        m.put("event_type", event.getEventType());
        m.put("description", event.getDescription());
        m.put("vitals_json", event.getVitalsJson());
        m.put("recorded_by", event.getRecordedBy());
        m.put("recorded_at", event.getRecordedAt());
        return m;
    }

    private Map<String, Object> postopRow(ProcedurePostopRecordEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getPostopId().toString());
        m.put("pacu_arrival_at", p.getPacuArrivalAt());
        m.put("aldrete_score", p.getAldreteScore());
        m.put("pain_score", p.getPainScore());
        m.put("complications", p.getComplications());
        m.put("disposition", p.getDisposition());
        m.put("recorded_at", p.getRecordedAt());
        // Wave 4 §12/§13 — PACU discharge decision + continuity
        m.put("destination", p.getDestination());
        m.put("unplanned_icu", p.isUnplannedIcu());
        m.put("return_to_theatre", p.isReturnToTheatre());
        m.put("discharge_readiness_band", p.getDischargeReadinessBand());
        m.put("discharge_readiness_score", p.getDischargeReadinessScore());
        m.put("nhume_delivery_id", p.getNhumeDeliveryId());
        m.put("linked_admission_ref", p.getLinkedAdmissionRef() != null ? p.getLinkedAdmissionRef().toString() : null);
        m.put("escalated_to", p.getEscalatedTo());
        m.put("escalation_ref", p.getEscalationRef());
        m.put("escalated_at", p.getEscalatedAt());
        m.put("discharge_decided_at", p.getDischargeDecidedAt());
        m.put("discharge_decided_by", p.getDischargeDecidedBy());
        return m;
    }
}
