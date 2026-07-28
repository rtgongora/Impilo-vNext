package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class EdVisitService {

    private static final Logger log = LoggerFactory.getLogger(EdVisitService.class);

    private final JourneyStateMachine journeyStateMachine;
    private final TriageService triageService;
    private final EncounterService encounterService;
    private final DischargeWorkflow dischargeWorkflow;
    private final EdVisitRepository visitRepository;
    private final EdTriageAssessmentRepository triageAssessmentRepository;
    private final EdTraumaActivationRepository traumaRepository;
    private final EdTraumaSurveyRepository traumaSurveyRepository;
    private final EdDispositionRepository dispositionRepository;
    private final EdClinicalPageRepository pageRepository;
    private final EmergencyCaseRepository emergencyCaseRepository;
    private final JourneyRepository journeyRepository;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient daidzaiEpisodes;
    private final EdTraumaTeamService traumaTeamService;
    private final EmergencyEpisodeService emergencyEpisodeService;
    private final EmergencyDispositionService emergencyDispositionService;

    public EdVisitService(JourneyStateMachine journeyStateMachine,
                          TriageService triageService,
                          EncounterService encounterService,
                          DischargeWorkflow dischargeWorkflow,
                          EdVisitRepository visitRepository,
                          EdTriageAssessmentRepository triageAssessmentRepository,
                          EdTraumaActivationRepository traumaRepository,
                          EdTraumaSurveyRepository traumaSurveyRepository,
                          EdDispositionRepository dispositionRepository,
                          EdClinicalPageRepository pageRepository,
                          EmergencyCaseRepository emergencyCaseRepository,
                          JourneyRepository journeyRepository,
                          ObjectMapper objectMapper,
                          zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient daidzaiEpisodes,
                          EdTraumaTeamService traumaTeamService,
                          EmergencyEpisodeService emergencyEpisodeService,
                          EmergencyDispositionService emergencyDispositionService) {
        this.journeyStateMachine = journeyStateMachine;
        this.triageService = triageService;
        this.encounterService = encounterService;
        this.dischargeWorkflow = dischargeWorkflow;
        this.visitRepository = visitRepository;
        this.triageAssessmentRepository = triageAssessmentRepository;
        this.traumaRepository = traumaRepository;
        this.traumaSurveyRepository = traumaSurveyRepository;
        this.dispositionRepository = dispositionRepository;
        this.pageRepository = pageRepository;
        this.emergencyCaseRepository = emergencyCaseRepository;
        this.journeyRepository = journeyRepository;
        this.objectMapper = objectMapper;
        this.daidzaiEpisodes = daidzaiEpisodes;
        this.traumaTeamService = traumaTeamService;
        this.emergencyEpisodeService = emergencyEpisodeService;
        this.emergencyDispositionService = emergencyDispositionService;
    }

    /**
     * ed_visit's free-text arrival mode -> emergency_episode's closed entry_route vocabulary.
     * WALK_IN and AMBULANCE both already agree between the two systems; anything else recorded on
     * an ED visit predates a defined mapping and is honestly logged as UNKNOWN_OR_UNRESPONSIVE
     * rather than guessed at, matching this pack's care-first-but-honest identity discipline.
     */
    private static String toEpisodeEntryRoute(String arrivalMode) {
        if (arrivalMode == null) return "WALK_IN";
        return switch (arrivalMode) {
            case "WALK_IN", "AMBULANCE", "STATUTORY_SERVICE", "INTERFACILITY_REFERRAL", "CHW_REFERRAL",
                 "PRIVATE_VEHICLE", "WORKPLACE_OR_SCHOOL" -> arrivalMode;
            default -> "UNKNOWN_OR_UNRESPONSIVE";
        };
    }

    @Transactional
    public Map<String, Object> openVisit(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID facilityId = EdPayloadMapper.uuid(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        String patientCpid = EdPayloadMapper.str(body, "patientCpid", "patient_cpid", "patientId", "patient_id");
        if (patientCpid == null || patientCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientCpid is required");
        }
        String arrivalMode = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "arrivalMode", "arrival_mode"), "WALK_IN").toUpperCase();
        JourneyEntity journey = journeyStateMachine.createJourney(
                facilityId, patientCpid, "EMERGENCY", EdPayloadMapper.str(body, "referralId", "referral_id"));

        EdVisitEntity visit = new EdVisitEntity();
        visit.setTenantId(ctx.tenantId());
        visit.setFacilityId(facilityId);
        visit.setJourneyId(journey.getJourneyId());
        visit.setPatientCpid(patientCpid);
        visit.setArrivalMode(arrivalMode);
        visit.setAmbulanceCallSign(EdPayloadMapper.str(body, "ambulanceCallSign", "ambulance_call_sign"));
        visit.setChiefComplaint(EdPayloadMapper.str(body, "chiefComplaint", "chief_complaint"));
        visit.setPresentingProblemCode(EdPayloadMapper.str(body, "presentingProblemCode", "presenting_problem_code"));
        visit.setEmergencyCaseId(EdPayloadMapper.uuid(body, "emergencyCaseId", "emergency_case_id"));
        // Canonical trauma spine: an arriving trauma patient's ED visit inherits the episode id
        // minted upstream by DAIDZAI (carried on X-Trauma-Episode-ID, injected into the body by the
        // controller, or passed explicitly). PCT keeps its own SoR row and merely stamps the id.
        UUID traumaEpisodeId = EdPayloadMapper.uuid(body, "traumaEpisodeId", "trauma_episode_id");
        visit.setTraumaEpisodeId(traumaEpisodeId);
        visit.setStatus("REGISTERED");
        visit = visitRepository.save(visit);

        // W15: mint the pct.emergency_episode spine alongside the visit. Idempotent on
        // (entry_route, entrySourceRef=visit_id) — a replayed registration finds the same episode,
        // never a duplicate. journeyId is already known at this point, so the episode is
        // CC-5-anchored from creation rather than starting PRE_ARRIVAL.
        var episodeResult = emergencyEpisodeService.open(new EmergencyEpisodeService.OpenEpisodeCommand(
                ctx.tenantId(),
                facilityId,
                toEpisodeEntryRoute(arrivalMode),
                "pct-service",
                visit.getVisitId().toString(),
                null,
                "UNDIFFERENTIATED",
                patientCpid,
                visit.getJourneyId(),
                null,
                traumaEpisodeId,
                visit.getEmergencyCaseId(),
                false,
                OffsetDateTime.now(),
                ctx.actorId()));
        visit.setEmergencyEpisodeId(episodeResult.episode().getEpisodeId());
        visit = visitRepository.save(visit);

        if (traumaEpisodeId != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), traumaEpisodeId, "ED", visit.getVisitId().toString(),
                    "ARRIVED", "pct.ed.visit_opened");
            // V-3 closing: the patient has reached a facility and the ED visit carries a journey, so
            // the prehospital spine can now resolve to that journey. Best-effort, after the phase.
            daidzaiEpisodes.continuumLink(ctx.tenantId(), traumaEpisodeId, visit.getJourneyId(), null);
        }
        return visitDetail(visit.getVisitId());
    }

    /**
     * ED pre-arrival projection (W3): materialise the prehospital ePCR snapshot DAIDZAI pushes for an
     * incoming EMS patient, so the ED sees them BEFORE arrival. Upsert keyed by trauma_episode_id — the
     * first push creates a PRE_ARRIVAL ed_visit (with its journey); later pushes refresh the snapshot.
     * On physical arrival (W4 handover) the same visit becomes an active ED visit — no duplicate record.
     */
    @Transactional
    public Map<String, Object> upsertPreArrival(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID episodeId = EdPayloadMapper.uuid(body, "traumaEpisodeId", "trauma_episode_id");
        if (episodeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "traumaEpisodeId is required");
        }
        UUID facilityId = EdPayloadMapper.uuid(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        String patientCpid = EdPayloadMapper.str(body, "patientCpid", "patient_cpid");
        if (patientCpid == null || patientCpid.isBlank()) {
            patientCpid = "TEMP-EMS-" + episodeId.toString().substring(0, 8).toUpperCase();
        }

        EdVisitEntity visit = visitRepository
                .findFirstByTenantIdAndTraumaEpisodeIdOrderByCreatedAtDesc(ctx.tenantId(), episodeId)
                .orElse(null);
        if (visit == null) {
            if (facilityId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "facilityId is required to open a pre-arrival record");
            }
            JourneyEntity journey = journeyStateMachine.createJourney(facilityId, patientCpid, "EMERGENCY", null);
            visit = new EdVisitEntity();
            visit.setTenantId(ctx.tenantId());
            visit.setFacilityId(facilityId);
            visit.setJourneyId(journey.getJourneyId());
            visit.setPatientCpid(patientCpid);
            visit.setArrivalMode("AMBULANCE");
            visit.setTraumaEpisodeId(episodeId);
            visit.setStatus("PRE_ARRIVAL");
        }
        visit.setEmsMissionRef(EdPayloadMapper.str(body, "emsMissionRef", "ems_mission_ref"));
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("snapshot", body.get("snapshot"));
        projection.put("missionState", body.get("missionState"));
        projection.put("emsMissionRef", body.get("emsMissionRef"));
        visit.setPreArrivalJson(jsonField(projection, "{}"));
        visit.setPreArrivalAt(OffsetDateTime.now());
        visit = visitRepository.save(visit);
        return visitDetail(visit.getVisitId());
    }

    /**
     * ED arrival/handover from EMS (W4): the ambulance physically arrives and hands over. Transition
     * the EXISTING PRE_ARRIVAL ed_visit (opened in W3, keyed by trauma_episode_id) to an active ED
     * visit — the SAME row, never a duplicate. If no pre-arrival record exists (an ePCR-less mission),
     * open the active visit here. Idempotent: arriving an already-active episode returns it unchanged.
     */
    @Transactional
    public Map<String, Object> arriveFromEms(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID episodeId = EdPayloadMapper.uuid(body, "traumaEpisodeId", "trauma_episode_id");
        if (episodeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "traumaEpisodeId is required");
        }
        String emsMissionRef = EdPayloadMapper.str(body, "emsMissionRef", "ems_mission_ref");
        UUID facilityId = EdPayloadMapper.uuid(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        String patientCpid = EdPayloadMapper.str(body, "patientCpid", "patient_cpid");

        EdVisitEntity visit = visitRepository
                .findFirstByTenantIdAndTraumaEpisodeIdOrderByCreatedAtDesc(ctx.tenantId(), episodeId)
                .orElse(null);
        if (visit == null) {
            // No pre-arrival projection was pushed — open the active visit now (still one row).
            if (facilityId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "facilityId is required to open an ED arrival");
            }
            String cpid = (patientCpid != null && !patientCpid.isBlank())
                    ? patientCpid : "TEMP-EMS-" + episodeId.toString().substring(0, 8).toUpperCase();
            JourneyEntity journey = journeyStateMachine.createJourney(facilityId, cpid, "EMERGENCY", null);
            visit = new EdVisitEntity();
            visit.setTenantId(ctx.tenantId());
            visit.setFacilityId(facilityId);
            visit.setJourneyId(journey.getJourneyId());
            visit.setPatientCpid(cpid);
            visit.setArrivalMode("AMBULANCE");
            visit.setTraumaEpisodeId(episodeId);
        }
        // Reuse the pre-arrival row: PRE_ARRIVAL → REGISTERED (active). No duplicate ed_visit.
        if (visit.getStatus() == null || "PRE_ARRIVAL".equals(visit.getStatus())) {
            visit.setStatus("REGISTERED");
        }
        if (emsMissionRef != null) visit.setEmsMissionRef(emsMissionRef);
        visit = visitRepository.save(visit);

        if (visit.getTraumaEpisodeId() != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), visit.getTraumaEpisodeId(), "ED",
                    visit.getVisitId().toString(), "ARRIVED", "pct.ed.ems_arrival");
            // V-3 closing: the pre-alerted ambulance patient has physically arrived onto a journey,
            // which is exactly the moment the prehospital spine becomes resolvable to the continuum.
            daidzaiEpisodes.continuumLink(ctx.tenantId(), visit.getTraumaEpisodeId(), visit.getJourneyId(), null);
        }
        return visitDetail(visit.getVisitId());
    }

    /** The ED pre-arrival board for a facility: incoming EMS patients not yet physically arrived. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPreArrival(UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID fac = facilityId != null ? facilityId : ctx.facilityId();
        return visitRepository
                .findByTenantIdAndFacilityIdAndStatusOrderByPreArrivalAtDesc(ctx.tenantId(), fac, "PRE_ARRIVAL")
                .stream().map(v -> visitDetail(v.getVisitId())).toList();
    }

    @Transactional
    public Map<String, Object> openEmergencyCase(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String complaint = EdPayloadMapper.str(body, "presentingComplaint", "presenting_complaint", "chiefComplaint");
        if (complaint == null || complaint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentingComplaint is required");
        }
        EmergencyCaseEntity ec = new EmergencyCaseEntity();
        ec.setTenantId(ctx.tenantId());
        ec.setTempPersonRef(EdPayloadMapper.str(body, "tempPersonRef", "temp_person_ref"));
        // Clinical intake carries the subject CPID (Identity Contract §7.2); the
        // legacy wire key accepted for compat but the value must
        // already be a mapped CPID — PCT never resolves or stores Health IDs.
        ec.setKnownSubjectCpid(EdPayloadMapper.str(body,
                "knownSubjectCpid", "known_subject_cpid", "knownHealthId", "known_health_id")); // actor-plane HID (legacy wire key; value is a CPID)
        ec.setTriageCategory(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "triageCategory", "triage_category"), "RED").toUpperCase());
        ec.setPresentingComplaint(complaint);
        ec.setOpenedByActorId(ctx.actorId());
        ec.setOpenedByProviderId(EdPayloadMapper.str(body, "providerId", "provider_id"));
        UUID facilityId = EdPayloadMapper.uuid(body, "facilityId", "facility_id");
        if (facilityId == null) facilityId = ctx.facilityId();
        ec.setFacilityId(facilityId);
        ec.setLocationDescription(EdPayloadMapper.str(body, "locationDescription", "location_description"));
        ec.setNotes(EdPayloadMapper.str(body, "notes"));
        ec = emergencyCaseRepository.save(ec);

        String cpid = Objects.requireNonNullElse(ec.getKnownSubjectCpid(),
                "TEMP-ED-" + ec.getEmergencyCaseId().toString().substring(0, 8).toUpperCase());
        Map<String, Object> visitBody = new LinkedHashMap<>(body);
        visitBody.put("patientCpid", cpid);
        visitBody.put("facilityId", facilityId.toString());
        visitBody.put("chiefComplaint", complaint);
        visitBody.put("emergencyCaseId", ec.getEmergencyCaseId().toString());
        visitBody.put("arrivalMode", "EMERGENCY_UNKNOWN");
        Map<String, Object> visit = openVisit(visitBody);
        ec.setJourneyId((String) visit.get("journey_id"));
        ec.setVisitId(UUID.fromString((String) visit.get("visit_id")));
        emergencyCaseRepository.save(ec);
        visit.put("emergency_case_id", ec.getEmergencyCaseId().toString());
        return visit;
    }

    @Transactional
    public Map<String, Object> reconcileEmergencyCase(UUID caseId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        EmergencyCaseEntity ec = emergencyCaseRepository.findByEmergencyCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency case not found"));
        // Identity Contract §7.2: reconciliation targets a CPID minted/resolved by
        // tshepo-identity. Legacy key spellings accepted for wire compat; the value
        // is a CPID either way — PCT never touches Health IDs.
        String reconciledCpid = EdPayloadMapper.str(body,
                "reconciledCpid", "reconciled_cpid", "reconciledHealthId", "reconciled_health_id"); // actor-plane HID (legacy wire key; value is a CPID)
        if (reconciledCpid == null || reconciledCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reconciledCpid is required");
        }
        ec.setReconciledCpid(reconciledCpid);
        ec.setReconciledAt(OffsetDateTime.now());
        ec.setReconciledBy(ctx.actorId());
        ec.setStatus("RECONCILED");
        emergencyCaseRepository.save(ec);
        if (ec.getVisitId() != null) {
            visitRepository.findById(ec.getVisitId()).ifPresent(v -> {
                v.setPatientCpid(reconciledCpid);
                visitRepository.save(v);
            });
        }
        return Map.of("emergency_case_id", caseId.toString(), "reconciled_cpid", reconciledCpid, "status", "RECONCILED");
    }

    public List<Map<String, Object>> listVisits(UUID facilityId, String statusFilter) {
        TrustContext ctx = TrustContextHolder.require();
        UUID fid = facilityId != null ? facilityId : ctx.facilityId();
        List<String> statuses = statusFilter != null && !statusFilter.isBlank()
                ? List.of(statusFilter.split(","))
                : List.of("REGISTERED", "TRIAGED", "IN_TREATMENT", "AWAITING_RESULTS", "DISPOSITION_PENDING");
        return visitRepository.findByTenantIdAndFacilityIdAndStatusInOrderByCreatedAtDesc(
                        ctx.tenantId(), fid, statuses)
                .stream().map(v -> visitSummary(v.getVisitId())).toList();
    }

    public Map<String, Object> getVisit(UUID visitId) {
        return visitDetail(visitId);
    }

    public List<Map<String, Object>> triageDiscriminatorCatalog() {
        return EdTriageDiscriminatorEngine.discriminatorCatalog();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> scoreTriage(Map<String, Object> body) {
        String system = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "triageSystem", "triage_system"), "ESI");
        Map<String, Object> discriminators = discriminatorsMap(body);
        Map<String, Object> vitals = vitalsMap(body);
        if (Boolean.TRUE.equals(EdPayloadMapper.bool(body, "scoreBoth", "score_both"))) {
            return EdTriageDiscriminatorEngine.scoreBoth(discriminators, vitals);
        }
        EdTriageDiscriminatorEngine.TriageScoreResult scored =
                EdTriageDiscriminatorEngine.calculate(system, discriminators, vitals);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("system", scored.system());
        out.put("acuity", scored.acuity());
        out.put("category", scored.category());
        out.put("colour", scored.colour());
        out.put("rationale", scored.rationale());
        out.put("computed_discriminators", scored.computedDiscriminators());
        return out;
    }

    @Transactional
    public Map<String, Object> recordStructuredTriage(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        TrustContext ctx = TrustContextHolder.require();
        String triageSystem = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "triageSystem", "triage_system"), "IMPILO_5");
        Map<String, Object> discriminators = discriminatorsMap(body);
        Map<String, Object> vitals = vitalsMap(body);
        Map<String, Object> scoringPayload = new LinkedHashMap<>(discriminators);
        scoringPayload.putAll(vitals);
        if (body.get("painScore") != null || body.get("pain_score") != null) {
            scoringPayload.put("pain_score", EdPayloadMapper.integer(body, "painScore", "pain_score"));
        }
        if (body.get("news2Score") != null || body.get("news2_score") != null) {
            scoringPayload.put("news2_score", EdPayloadMapper.integer(body, "news2Score", "news2_score"));
        }

        // WHO IITT is the acuity authority of record (PO ruling R7). ESI/MTS are computed only as
        // advisory scores that never set the acuity and never overwrite the tool of record — the
        // Math.min reconciliation and the `acuity = 3` default are both gone. The whole decision is
        // a pure function of the request (EmergencyTriageDecider) so it is unit-testable without a
        // running service; this method only persists its result.
        Integer explicitAcuity = EdPayloadMapper.integer(body, "acuity");
        boolean wantAdvisory = shouldApplyDiscriminatorEngine(triageSystem, body) || !discriminators.isEmpty();
        Map<String, Object> advisory = wantAdvisory
                ? EdTriageDiscriminatorEngine.scoreBoth(scoringPayload, vitals)
                : null;

        TriageDecision decision = EmergencyTriageDecider.decide(body, advisory, triageSystem, explicitAcuity);
        if (!decision.triageable()) {
            // An unassessed patient is a refusal returned to the caller, never a stored acuity 3.
            String reason = decision.refusalCode() + ": " + decision.refusalMessage()
                    + (decision.unassessedInputs().isEmpty()
                            ? ""
                            : " Unassessed inputs: " + decision.unassessedInputs());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
        }
        int acuity = decision.acuity();
        if (acuity < 1 || acuity > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "acuity must be 1-5");
        }
        // Keep the legacy triage_system column in step with the authoritative tool so it stops lying.
        triageSystem = decision.triageTool();

        Map<String, Object> enrichedDiscriminators = new LinkedHashMap<>(discriminators);
        if (advisory != null) {
            enrichedDiscriminators.put("advisory", advisory);
        }
        boolean fastTrack = Boolean.TRUE.equals(EdPayloadMapper.bool(body, "fastTrack", "fast_track"));

        EdTriageAssessmentEntity assessment = new EdTriageAssessmentEntity();
        assessment.setVisitId(visitId);
        assessment.setTenantId(ctx.tenantId());
        assessment.setAcuity(acuity);
        assessment.setTriageSystem(triageSystem);
        assessment.setTriageTool(decision.triageTool());
        assessment.setIittPriority(decision.iittPriority());
        assessment.setRequiresClinicianReview(decision.requiresClinicianReview());
        assessment.setReviewReason(decision.reviewReason());
        if (decision.emergencySigns() != null && !decision.emergencySigns().isEmpty()) {
            assessment.setEmergencySignsJson(jsonField(decision.emergencySigns(), "{}"));
        }
        if (advisory != null) {
            assessment.setAdvisoryScoresJson(jsonField(advisory, "{}"));
        }
        assessment.setChiefComplaint(EdPayloadMapper.str(body, "chiefComplaint", "chief_complaint"));
        assessment.setPresentingProblemCode(EdPayloadMapper.str(body, "presentingProblemCode", "presenting_problem_code"));
        assessment.setPainScore(EdPayloadMapper.integer(body, "painScore", "pain_score"));
        assessment.setVitalsJson(jsonField(vitals.isEmpty() ? body.get("vitals") : vitals, "{}"));
        assessment.setDangerSignsJson(jsonField(firstPresent(body, "dangerSigns", "danger_signs"), "[]"));
        assessment.setDiscriminatorsJson(jsonField(enrichedDiscriminators, "{}"));
        assessment.setNews2Score(EdPayloadMapper.integer(body, "news2Score", "news2_score"));
        assessment.setFastTrack(fastTrack);
        assessment.setNotes(EdPayloadMapper.str(body, "notes"));
        assessment.setTriagedBy(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "triagedBy", "triaged_by"), ctx.actorId()));
        triageAssessmentRepository.save(assessment);

        triageService.recordTriage(visit.getJourneyId(), acuity, assessment.getVitalsJson(), assessment.getNotes());

        visit.setCurrentAcuity(acuity);
        visit.setNews2Score(assessment.getNews2Score());
        if (assessment.getChiefComplaint() != null) visit.setChiefComplaint(assessment.getChiefComplaint());
        if (assessment.getPresentingProblemCode() != null) {
            visit.setPresentingProblemCode(assessment.getPresentingProblemCode());
        }
        visit.setZone(EdPayloadMapper.str(body, "zone"));
        if (visit.getZone() == null) {
            visit.setZone(EdProtocolCatalog.zoneForAcuity(acuity, fastTrack));
        }
        visit.setStatus("TRIAGED");
        visitRepository.save(visit);
        // Trauma spine: link the triage level to the canonical episode timeline (G1.6).
        if (visit.getTraumaEpisodeId() != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), visit.getTraumaEpisodeId(), "TRIAGE",
                    assessment.getId().toString(), "ACUITY_" + acuity, "pct.ed.triaged");
        }
        return visitDetail(visitId);
    }

    @Transactional
    public Map<String, Object> assignZone(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        String zone = EdPayloadMapper.str(body, "zone");
        if (zone == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zone is required");
        visit.setZone(zone.toUpperCase());
        visit.setTrackNumber(EdPayloadMapper.str(body, "trackNumber", "track_number"));
        visit.setAssignedClinician(EdPayloadMapper.str(body, "assignedClinician", "assigned_clinician"));
        visitRepository.save(visit);
        return visitDetail(visitId);
    }

    @Transactional
    public Map<String, Object> startEdEncounter(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        JourneyEntity journey = journeyRepository.findByJourneyId(visit.getJourneyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found"));
        if (journey.getState() == JourneyState.TRIAGED) {
            journeyStateMachine.transition(journey, JourneyState.QUEUED);
        }
        EncounterEntity encounter = encounterService.startEncounter(
                visit.getJourneyId(),
                "EMERGENCY",
                "emergency",
                "emergency_arrival",
                "IN_PERSON",
                null,
                "facility",
                visit.getCurrentAcuity() != null && visit.getCurrentAcuity() <= 2 ? "emergency" : "urgent",
                visit.getCurrentAcuity() != null ? "ACUITY_" + visit.getCurrentAcuity() : null,
                visit.getPathwayRef(),
                visit.getProtocolRef());
        visit.setEncounterId(encounter.getId());
        visit.setStatus("IN_TREATMENT");
        visitRepository.save(visit);
        return visitDetail(visitId);
    }

    @Transactional
    public Map<String, Object> bindProtocol(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        String pathwayRef = EdPayloadMapper.str(body, "pathwayRef", "pathway_ref");
        String protocolRef = EdPayloadMapper.str(body, "protocolRef", "protocol_ref", "protocolCode", "protocol_code");
        UUID sessionId = EdPayloadMapper.uuid(body, "pathwaySessionId", "pathway_session_id");
        if (pathwayRef != null) visit.setPathwayRef(pathwayRef);
        if (protocolRef != null) visit.setProtocolRef(protocolRef);
        if (sessionId != null) visit.setPathwaySessionId(sessionId);
        visitRepository.save(visit);
        if (visit.getEncounterId() != null && (pathwayRef != null || protocolRef != null)) {
            encounterService.updateEncounterPathwayProtocol(visit.getEncounterId(), pathwayRef, protocolRef);
        }
        return visitDetail(visitId);
    }

    public List<Map<String, Object>> protocolSuggestions(UUID visitId) {
        EdVisitEntity visit = requireVisit(visitId);
        boolean traumaActive = traumaRepository.findFirstByVisitIdAndStatusOrderByActivatedAtDesc(visitId, "ACTIVE").isPresent();
        String mechanism = traumaRepository.findByVisitIdOrderByActivatedAtDesc(visitId).stream()
                .findFirst().map(EdTraumaActivationEntity::getMechanism).orElse(null);
        return EdProtocolCatalog.suggest(visit.getChiefComplaint(), visit.getCurrentAcuity(), mechanism, traumaActive);
    }

    @Transactional
    public Map<String, Object> activateTrauma(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        TrustContext ctx = TrustContextHolder.require();
        Integer level = EdPayloadMapper.integer(body, "traumaLevel", "trauma_level");
        if (level == null) level = 2;
        EdTraumaActivationEntity trauma = new EdTraumaActivationEntity();
        trauma.setVisitId(visitId);
        trauma.setTenantId(ctx.tenantId());
        trauma.setTraumaLevel(level);
        trauma.setMechanism(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "mechanism"), "UNSPECIFIED"));
        trauma.setTeamLeader(EdPayloadMapper.str(body, "teamLeader", "team_leader"));
        trauma.setTeamAssignmentsJson(jsonField(firstPresent(body, "teamAssignments", "team_assignments"), "[]"));
        trauma.setActivatedBy(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "activatedBy", "activated_by"), ctx.actorId()));
        trauma = traumaRepository.save(trauma);
        visit.setProtocolRef("TRAUMA");
        visit.setPathwayRef("f0000001-0001-4001-8001-000000000106");
        visit.setZone("RESUS");
        // ED-first walk-in trauma: trauma-team activation IS the trauma declaration. If this visit
        // did not arrive on an existing episode (no upstream incident), PCT mints the canonical
        // episode against itself (origin_kind=ED_WALK_IN, idempotent on the ed_visit id) and stamps
        // it. A degraded DAIDZAI never blocks the activation — the id simply stays null this pass.
        UUID episodeId = visit.getTraumaEpisodeId();
        if (episodeId == null) {
            episodeId = EdPayloadMapper.uuid(body, "traumaEpisodeId", "trauma_episode_id");
        }
        if (episodeId == null) {
            // No upstream episode → ED-first walk-in trauma: PCT mints against itself.
            episodeId = daidzaiEpisodes.mintEdWalkIn(ctx.tenantId(), visitId, visit.getPatientCpid(),
                    isProvisionalCpid(visit.getPatientCpid()) ? "PROVISIONAL" : "KNOWN");
        }
        if (episodeId != null) {
            visit.setTraumaEpisodeId(episodeId);
        }
        visitRepository.save(visit);
        if (episodeId != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), episodeId, "TRIAGE", trauma.getTraumaId().toString(),
                    "TRAUMA_ACTIVATED", "pct.ed.trauma_activated");
        }
        // Trauma-team activation (G1.7): resolve the on-call team from VASHANDI and raise a real
        // notification delivery-intent per rostered member. Best-effort — never blocks the activation.
        var team = traumaTeamService.activateTeam(ctx.tenantId(), visit, trauma);
        Map<String, Object> detail = visitDetail(visitId);
        detail.put("active_trauma_id", trauma.getTraumaId().toString());
        if (episodeId != null) detail.put("trauma_episode_id", episodeId.toString());
        detail.put("trauma_team", team.stream().map(this::traumaTeamMemberRow).toList());
        return detail;
    }

    private Map<String, Object> traumaTeamMemberRow(zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaTeamMemberEntity m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId().toString());
        row.put("workforce_profile_id", m.getWorkforceProfileId());
        row.put("role", m.getRole());
        row.put("ack_status", m.getAckStatus());
        row.put("notification_ref", m.getNotificationRef());
        row.put("notified_at", m.getNotifiedAt() != null ? m.getNotifiedAt().toString() : null);
        row.put("ack_at", m.getAckAt() != null ? m.getAckAt().toString() : null);
        row.put("escalated_at", m.getEscalatedAt() != null ? m.getEscalatedAt().toString() : null);
        return row;
    }

    /** An ED temp/provisional cpid (unknown patient) carries a TEMP-/PROV- prefix rather than a real Health ID. */
    private static boolean isProvisionalCpid(String cpid) {
        if (cpid == null) return true;
        String c = cpid.toUpperCase();
        return c.startsWith("TEMP-") || c.startsWith("PROV-") || c.startsWith("TEMP-ED-");
    }

    @Transactional
    public Map<String, Object> recordTraumaSurvey(UUID visitId, Map<String, Object> body) {
        requireVisit(visitId);
        TrustContext ctx = TrustContextHolder.require();
        UUID traumaId = EdPayloadMapper.uuid(body, "traumaId", "trauma_id");
        if (traumaId == null) {
            traumaId = traumaRepository.findFirstByVisitIdAndStatusOrderByActivatedAtDesc(visitId, "ACTIVE")
                    .map(EdTraumaActivationEntity::getTraumaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active trauma activation"));
        }
        String surveyType = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "surveyType", "survey_type"), "PRIMARY").toUpperCase();
        EdVisitEntity visit = requireVisit(visitId);
        EdTraumaSurveyEntity survey = new EdTraumaSurveyEntity();
        survey.setTraumaId(traumaId);
        survey.setTenantId(ctx.tenantId());
        survey.setTraumaEpisodeId(visit.getTraumaEpisodeId());
        survey.setSurveyType(surveyType);
        survey.setSectionKey(EdPayloadMapper.str(body, "sectionKey", "section_key"));
        survey.setChecklistJson(jsonField(body.get("checklist"), "{}"));
        survey.setVitalsJson(jsonField(body.get("vitals"), "{}"));
        survey.setNotes(EdPayloadMapper.str(body, "notes"));
        survey.setRecordedBy(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "recordedBy", "recorded_by"), ctx.actorId()));
        // AMEND without overwriting: a later survey that amends an earlier one (new injuries found on
        // re-examination) is a NEW revision referencing the superseded row, which is RETAINED.
        String amendsRaw = EdPayloadMapper.str(body, "amendsSurveyId", "amends_survey_id");
        Long amendsId = (amendsRaw != null && !amendsRaw.isBlank()) ? Long.valueOf(amendsRaw.trim()) : null;
        if (amendsId != null) {
            EdTraumaSurveyEntity original = traumaSurveyRepository.findById(amendsId)
                    .filter(o -> ctx.tenantId().equals(o.getTenantId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amended survey not found"));
            survey.setAmendsSurveyId(original.getId());
            survey.setRevision(original.getRevision() + 1);
            original.setStatus("AMENDED");   // preserved, not overwritten
            traumaSurveyRepository.save(original);
        }
        survey = traumaSurveyRepository.save(survey);
        if (visit.getTraumaEpisodeId() != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), visit.getTraumaEpisodeId(),
                    "SECONDARY".equals(surveyType) ? "DEFINITIVE" : "RESUS",
                    survey.getId().toString(), surveyType + "_SURVEY_R" + survey.getRevision(),
                    "pct.ed.trauma_survey");
        }
        return visitDetail(visitId);
    }

    @Transactional
    public Map<String, Object> endTrauma(UUID visitId, Map<String, Object> body) {
        UUID traumaId = EdPayloadMapper.uuid(body, "traumaId", "trauma_id");
        EdTraumaActivationEntity trauma = traumaId != null
                ? traumaRepository.findById(traumaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trauma not found"))
                : traumaRepository.findFirstByVisitIdAndStatusOrderByActivatedAtDesc(visitId, "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active trauma"));
        trauma.setStatus("ENDED");
        trauma.setEndedAt(OffsetDateTime.now());
        trauma.setOutcome(EdPayloadMapper.str(body, "outcome"));
        trauma.setNotes(EdPayloadMapper.str(body, "notes"));
        traumaRepository.save(trauma);
        return visitDetail(visitId);
    }

    @Transactional
    public Map<String, Object> requestClinicalPage(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        TrustContext ctx = TrustContextHolder.require();
        String pageType = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "pageType", "page_type"), "TEAM").toUpperCase();
        String message = EdPayloadMapper.str(body, "message");
        if (message == null || message.isBlank()) {
            message = "ED page for " + visit.getPatientCpid() + " — " + pageType;
        }
        EdClinicalPageEntity page = new EdClinicalPageEntity();
        page.setVisitId(visitId);
        page.setTenantId(ctx.tenantId());
        page.setPageType(pageType);
        page.setUrgency(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "urgency"), "STAT").toUpperCase());
        page.setRecipientRole(EdPayloadMapper.str(body, "recipientRole", "recipient_role"));
        page.setRecipientId(EdPayloadMapper.str(body, "recipientId", "recipient_id"));
        page.setMessage(message);
        page.setStatus("PENDING");
        page.setSentBy(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "sentBy", "sent_by"), ctx.actorId()));
        page = pageRepository.save(page);
        return Map.of("page", pageRow(page), "visit", visitDetail(visitId));
    }

    @Transactional
    public Map<String, Object> markPageDelivered(UUID pageId, Map<String, Object> body) {
        EdClinicalPageEntity page = pageRepository.findById(pageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
        page.setStatus("SENT");
        page.setSupportTicketRef(EdPayloadMapper.str(body, "supportTicketRef", "support_ticket_ref"));
        page.setNotificationRef(EdPayloadMapper.str(body, "notificationRef", "notification_ref"));
        pageRepository.save(page);
        return pageRow(page);
    }

    @Transactional
    public Map<String, Object> recordDisposition(UUID visitId, Map<String, Object> body) {
        EdVisitEntity visit = requireVisit(visitId);
        TrustContext ctx = TrustContextHolder.require();
        if (dispositionRepository.findByVisitId(visitId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disposition already recorded");
        }
        String dispositionType = Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "dispositionType", "disposition_type"), "DISCHARGE").toUpperCase();

        EdDispositionEntity d = new EdDispositionEntity();
        d.setVisitId(visitId);
        d.setTenantId(ctx.tenantId());
        d.setDispositionType(dispositionType);
        d.setPrimaryDiagnosisCode(EdPayloadMapper.str(body, "primaryDiagnosisCode", "primary_diagnosis_code"));
        d.setPrimaryDiagnosisDisplay(EdPayloadMapper.str(body, "primaryDiagnosisDisplay", "primary_diagnosis_display"));
        d.setSecondaryDiagnosesJson(jsonField(firstPresent(body, "secondaryDiagnoses", "secondary_diagnoses"), "[]"));
        d.setExternalCauseCode(EdPayloadMapper.str(body, "externalCauseCode", "external_cause_code"));
        d.setExternalCauseDisplay(EdPayloadMapper.str(body, "externalCauseDisplay", "external_cause_display"));
        d.setProcedureCodesJson(jsonField(firstPresent(body, "procedureCodes", "procedure_codes"), "[]"));
        d.setDischargeSummary(EdPayloadMapper.str(body, "dischargeSummary", "discharge_summary"));
        d.setDestinationFacility(EdPayloadMapper.str(body, "destinationFacility", "destination_facility"));
        d.setDestinationWard(EdPayloadMapper.str(body, "destinationWard", "destination_ward"));
        d.setDispositionBy(Objects.requireNonNullElse(
                EdPayloadMapper.str(body, "dispositionBy", "disposition_by"), ctx.actorId()));
        dispositionRepository.save(d);

        visit.setStatus("COMPLETED");
        visitRepository.save(visit);

        if ("LWBS".equals(dispositionType)) {
            JourneyEntity journey = journeyRepository.findByJourneyId(visit.getJourneyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found"));
            journeyStateMachine.transition(journey, JourneyState.LEFT_WITHOUT_BEING_SEEN);
        } else {
            String dischargeType = mapDispositionToDischarge(dispositionType);
            dischargeWorkflow.startDischarge(visit.getJourneyId(), dischargeType);
        }
        // Trauma spine (G1.11): ED disposition ends the trauma journey → close the canonical episode
        // (register the DISPOSITION phase, then close). Surgery does NOT close (theatre owns that).
        if (visit.getTraumaEpisodeId() != null) {
            daidzaiEpisodes.registerPhase(TrustContextHolder.require().tenantId(), visit.getTraumaEpisodeId(),
                    "DISPOSITION", visitId.toString(), dispositionType, "pct.ed.disposition");
            daidzaiEpisodes.close(TrustContextHolder.require().tenantId(), visit.getTraumaEpisodeId(),
                    "ED disposition: " + dispositionType);
        }

        // W15: close the real emergency_episode disposition (15-type, R12 mandatory-content) with
        // this same ed_visit disposition record — the episode-spine reachability fix (see V211's
        // rationale) means every ED visit now has a live episode, and it must not go on being closed
        // by ed_visit's own disposition alone while the episode stays open forever.
        if (visit.getEmergencyEpisodeId() != null) {
            recordEpisodeDispositionBestEffort(visit.getEmergencyEpisodeId(), ctx.tenantId(), dispositionType, d);
        }
        return visitDetail(visitId);
    }

    /**
     * Maps ed_visit's 7-value disposition onto the episode's 15-type vocabulary and records it via
     * {@link EmergencyDispositionService}. Best-effort: the episode's mandatory-content gate is
     * stricter than ed_visit's own form ever collected (a destination or last-seen time this legacy
     * flow never asked for), so a mapping that cannot satisfy the gate is logged and skipped rather
     * than fabricating a value to force it through — the episode stays open until that gap is filled
     * by a real write, which is the honest state, not a silently-forced closure.
     */
    private void recordEpisodeDispositionBestEffort(UUID episodeId, UUID tenantId, String edDispositionType,
                                                     EdDispositionEntity d) {
        String episodeType = switch (edDispositionType) {
            case "DISCHARGE" -> "DISCHARGED_HOME";
            case "ADMIT" -> "ADMITTED_WARD";
            case "TRANSFER" -> "TRANSFERRED_OUT";
            case "REFER" -> "REFERRED_OUTPATIENT";
            case "LAMA" -> "LEFT_AGAINST_ADVICE";
            case "LWBS" -> "LEFT_BEFORE_ASSESSMENT";
            case "DEATH" -> "DIED";
            default -> null;
        };
        if (episodeType == null) {
            log.warn("ed_visit disposition type {} has no episode-disposition mapping; episode {} stays open",
                    edDispositionType, episodeId);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", episodeType);
        body.put("disposedBy", d.getDispositionBy());
        String reason = firstNonBlank(d.getDischargeSummary(), d.getPrimaryDiagnosisDisplay());
        body.put("dispositionReason", reason != null ? reason
                : "Recorded via ED visit disposition (see ed_visit " + d.getVisitId() + ")");
        String destination = firstNonBlank(d.getDestinationFacility(), d.getDestinationWard());
        if (destination != null) body.put("destination", destination);
        if ("DIED".equals(episodeType)) {
            body.put("causeOrCircumstances", firstNonBlank(d.getExternalCauseDisplay(), d.getDischargeSummary(),
                    "Cause not further specified at ED disposition"));
        }
        if ("LEFT_BEFORE_ASSESSMENT".equals(episodeType) || "LEFT_AGAINST_ADVICE".equals(episodeType)) {
            // The moment this disposition is recorded IS the last-observed moment for a patient who
            // has left — a real fact, not a placeholder, for exactly these two types.
            body.put("lastSeenAt", OffsetDateTime.now().toString());
        }
        try {
            emergencyDispositionService.record(episodeId, tenantId, body);
        } catch (ResponseStatusException e) {
            log.warn("Episode disposition for episode {} could not be recorded from ed_visit disposition "
                    + "(mapped type {}): {} — episode stays open until completed with the missing field",
                    episodeId, episodeType, e.getReason());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String mapDispositionToDischarge(String dispositionType) {
        return switch (dispositionType) {
            case "ADMIT" -> "ADMIT";
            case "TRANSFER" -> "TRANSFER";
            case "REFER" -> "REFER";
            case "DEATH" -> "DEATH";
            case "LAMA", "AMA" -> "AMA";
            default -> "CLINICAL";
        };
    }

    private EdVisitEntity requireVisit(UUID visitId) {
        return visitRepository.findById(visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ED visit not found"));
    }

    private Map<String, Object> visitSummary(UUID visitId) {
        EdVisitEntity v = requireVisit(visitId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visit_id", v.getVisitId().toString());
        m.put("journey_id", v.getJourneyId());
        m.put("patient_cpid", v.getPatientCpid());
        m.put("status", v.getStatus());
        m.put("zone", v.getZone());
        m.put("current_acuity", v.getCurrentAcuity());
        m.put("chief_complaint", v.getChiefComplaint());
        m.put("arrival_mode", v.getArrivalMode());
        m.put("created_at", v.getCreatedAt());
        return m;
    }

    private Map<String, Object> visitDetail(UUID visitId) {
        EdVisitEntity v = requireVisit(visitId);
        Map<String, Object> m = new LinkedHashMap<>(visitSummary(visitId));
        m.put("facility_id", v.getFacilityId().toString());
        m.put("emergency_case_id", v.getEmergencyCaseId() != null ? v.getEmergencyCaseId().toString() : null);
        m.put("track_number", v.getTrackNumber());
        m.put("assigned_clinician", v.getAssignedClinician());
        m.put("pathway_ref", v.getPathwayRef());
        m.put("protocol_ref", v.getProtocolRef());
        m.put("pathway_session_id", v.getPathwaySessionId() != null ? v.getPathwaySessionId().toString() : null);
        m.put("encounter_id", v.getEncounterId());
        m.put("news2_score", v.getNews2Score());
        m.put("presenting_problem_code", v.getPresentingProblemCode());
        m.put("ambulance_call_sign", v.getAmbulanceCallSign());
        m.put("trauma_episode_id", v.getTraumaEpisodeId() != null ? v.getTraumaEpisodeId().toString() : null);
        m.put("emergency_episode_id", v.getEmergencyEpisodeId() != null ? v.getEmergencyEpisodeId().toString() : null);
        m.put("ems_mission_ref", v.getEmsMissionRef());
        m.put("pre_arrival", v.getPreArrivalJson());
        m.put("pre_arrival_at", v.getPreArrivalAt() != null ? v.getPreArrivalAt().toString() : null);
        m.put("triage_assessments", triageAssessmentRepository.findByVisitIdOrderByCreatedAtDesc(visitId)
                .stream().map(this::triageRow).toList());
        m.put("trauma_activations", traumaRepository.findByVisitIdOrderByActivatedAtDesc(visitId)
                .stream().map(this::traumaRow).toList());
        m.put("clinical_pages", pageRepository.findByVisitIdOrderBySentAtDesc(visitId)
                .stream().map(this::pageRow).toList());
        m.put("protocol_suggestions", protocolSuggestions(visitId));
        dispositionRepository.findByVisitId(visitId).ifPresent(d -> m.put("disposition", dispositionRow(d)));
        traumaRepository.findFirstByVisitIdAndStatusOrderByActivatedAtDesc(visitId, "ACTIVE").ifPresent(t -> {
            m.put("active_trauma_id", t.getTraumaId().toString());
            m.put("trauma_surveys", traumaSurveyRepository.findByTraumaIdOrderByRecordedAtAsc(t.getTraumaId())
                    .stream().map(this::surveyRow).toList());
        });
        return m;
    }

    private Map<String, Object> triageRow(EdTriageAssessmentEntity t) {
        // Null-tolerant (LinkedHashMap, not Map.of): iitt_priority and review_reason are null on a
        // manual row, and the IITT decision fields are what W4d surfaces — the priority as the tier
        // of record, the review flag as the interruptive prompt, the advisory scores as clearly
        // secondary. The legacy triage_system is retained but now equals the authoritative tool.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("acuity", t.getAcuity());
        m.put("triage_system", t.getTriageSystem());
        m.put("triage_tool", t.getTriageTool());
        m.put("iitt_priority", t.getIittPriority());
        m.put("requires_clinician_review", t.isRequiresClinicianReview());
        m.put("review_reason", t.getReviewReason());
        m.put("emergency_signs", t.getEmergencySignsJson());
        m.put("advisory_scores", t.getAdvisoryScoresJson());
        m.put("chief_complaint", Objects.requireNonNullElse(t.getChiefComplaint(), ""));
        m.put("pain_score", t.getPainScore() != null ? t.getPainScore() : 0);
        m.put("news2_score", t.getNews2Score() != null ? t.getNews2Score() : 0);
        m.put("fast_track", t.isFastTrack());
        m.put("triaged_by", t.getTriagedBy());
        m.put("created_at", t.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> traumaRow(EdTraumaActivationEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trauma_id", t.getTraumaId().toString());
        m.put("trauma_level", t.getTraumaLevel());
        m.put("mechanism", t.getMechanism());
        m.put("status", t.getStatus());
        m.put("team_leader", t.getTeamLeader());
        m.put("activated_at", t.getActivatedAt().toString());
        return m;
    }

    private Map<String, Object> surveyRow(EdTraumaSurveyEntity s) {
        return Map.of(
                "id", s.getId(),
                "survey_type", s.getSurveyType(),
                "section_key", Objects.requireNonNullElse(s.getSectionKey(), ""),
                "revision", s.getRevision(),
                "status", s.getStatus(),
                "amends_survey_id", s.getAmendsSurveyId() != null ? s.getAmendsSurveyId() : "",
                "recorded_by", s.getRecordedBy(),
                "recorded_at", s.getRecordedAt().toString());
    }

    private Map<String, Object> pageRow(EdClinicalPageEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page_id", p.getPageId().toString());
        m.put("page_type", p.getPageType());
        m.put("urgency", p.getUrgency());
        m.put("message", p.getMessage());
        m.put("status", p.getStatus());
        m.put("sent_at", p.getSentAt().toString());
        return m;
    }

    private Map<String, Object> dispositionRow(EdDispositionEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("disposition_id", d.getDispositionId().toString());
        m.put("disposition_type", d.getDispositionType());
        m.put("primary_diagnosis_code", d.getPrimaryDiagnosisCode());
        m.put("primary_diagnosis_display", d.getPrimaryDiagnosisDisplay());
        m.put("external_cause_code", d.getExternalCauseCode());
        m.put("disposition_at", d.getDispositionAt().toString());
        return m;
    }

    private static Object firstPresent(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) return body.get(key);
        }
        return null;
    }

    private static boolean shouldApplyDiscriminatorEngine(String triageSystem, Map<String, Object> body) {
        if (Boolean.TRUE.equals(EdPayloadMapper.bool(body, "applyDiscriminators", "apply_discriminators"))) {
            return true;
        }
        if (triageSystem == null) return false;
        String normalized = triageSystem.trim().toUpperCase(Locale.ROOT);
        return "ESI".equals(normalized) || "MTS".equals(normalized) || "MANCHESTER".equals(normalized);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> discriminatorsMap(Map<String, Object> body) {
        Object raw = firstPresent(body, "discriminators");
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vitalsMap(Map<String, Object> body) {
        Object raw = body.get("vitals");
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    private String jsonField(Object value, String fallback) {
        if (value == null) return fallback;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }
}
