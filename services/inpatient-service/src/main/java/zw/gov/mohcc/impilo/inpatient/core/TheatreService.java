package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.ButanoProcedureClient;
import zw.gov.mohcc.impilo.inpatient.integration.MadiBloodClient;
import zw.gov.mohcc.impilo.inpatient.integration.NhumeTransportClient;
import zw.gov.mohcc.impilo.inpatient.integration.OrosOrderClient;
import zw.gov.mohcc.impilo.inpatient.integration.OrosSpecimenClient;
import zw.gov.mohcc.impilo.inpatient.integration.RitoSafetyClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreDeathClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreReadinessClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreReadinessClient.ReadinessResult;
import zw.gov.mohcc.impilo.inpatient.api.dto.CreateAdmissionRequest;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Theatre &amp; perioperative depth orchestration on top of the procedure-episode pipeline.
 * Real, owner-routed depth: OROS PROCEDURE intake, triage, multi-owner booking-readiness (fails safely
 * with owner blockers), signable operative note (Butano-linked), cancellation that releases owner
 * reservations, safety-event routing (Rito/Madi/asset-registry), and death-in-theatre → PCT DeathWorkflow.
 * Never a SoR for orders/blood/stock/equipment/death — it records the theatre-side fact + owner ref.
 */
@Service
public class TheatreService {

    private static final Logger log = LoggerFactory.getLogger(TheatreService.class);

    private static final Set<String> TRIAGE_PRIORITIES =
            Set.of("IMMEDIATE", "EMERGENCY", "URGENT", "ELECTIVE", "DAY_CASE");
    private static final List<String> ACTIVE_STATUSES =
            List.of("BOOKED", "PREOP", "READY_FOR_THEATRE", "IN_PROGRESS", "PACU");

    private final ProcedureEpisodeRepository episodeRepository;
    private final ProcedureReadinessCheckRepository readinessRepository;
    private final ProcedureNoteRepository noteRepository;
    private final ProcedureSafetyEventRepository safetyRepository;
    private final ProcedureChecklistItemRepository checklistRepository;
    private final EventOutboxRepository outboxRepository;
    private final ProcedureEpisodeService episodeService;
    private final OrosOrderClient orosOrderClient;
    private final TheatreReadinessClient readinessClient;
    private final TheatreDeathClient deathClient;
    private final ButanoProcedureClient butanoClient;
    // ── Wave 1 clinical-safety collaborators (peers stay source-of-truth; these hold ref + projection) ──
    private final ProcedureBloodLinkRepository bloodLinkRepository;
    private final ProcedureTransportRepository transportRepository;
    private final ProcedureSpecimenRepository specimenRepository;
    private final ProcedureCountRepository countRepository;
    private final MadiBloodClient madiBloodClient;
    private final NhumeTransportClient nhumeTransportClient;
    private final OrosSpecimenClient orosSpecimenClient;
    private final RitoSafetyClient ritoSafetyClient;
    // ── Wave 4 §12/§13 — PACU discharge decision + theatre→inpatient continuity ──
    private final ProcedurePostopRecordRepository postopRepository;
    private final AdmissionService admissionService;
    private final ObjectMapper objectMapper;

    public TheatreService(ProcedureEpisodeRepository episodeRepository,
                          ProcedureReadinessCheckRepository readinessRepository,
                          ProcedureNoteRepository noteRepository,
                          ProcedureSafetyEventRepository safetyRepository,
                          ProcedureChecklistItemRepository checklistRepository,
                          EventOutboxRepository outboxRepository,
                          ProcedureEpisodeService episodeService,
                          OrosOrderClient orosOrderClient,
                          TheatreReadinessClient readinessClient,
                          TheatreDeathClient deathClient,
                          ButanoProcedureClient butanoClient,
                          ProcedureBloodLinkRepository bloodLinkRepository,
                          ProcedureTransportRepository transportRepository,
                          ProcedureSpecimenRepository specimenRepository,
                          ProcedureCountRepository countRepository,
                          MadiBloodClient madiBloodClient,
                          NhumeTransportClient nhumeTransportClient,
                          OrosSpecimenClient orosSpecimenClient,
                          RitoSafetyClient ritoSafetyClient,
                          ProcedurePostopRecordRepository postopRepository,
                          AdmissionService admissionService,
                          ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.readinessRepository = readinessRepository;
        this.noteRepository = noteRepository;
        this.safetyRepository = safetyRepository;
        this.checklistRepository = checklistRepository;
        this.outboxRepository = outboxRepository;
        this.episodeService = episodeService;
        this.orosOrderClient = orosOrderClient;
        this.readinessClient = readinessClient;
        this.deathClient = deathClient;
        this.butanoClient = butanoClient;
        this.bloodLinkRepository = bloodLinkRepository;
        this.transportRepository = transportRepository;
        this.specimenRepository = specimenRepository;
        this.countRepository = countRepository;
        this.madiBloodClient = madiBloodClient;
        this.nhumeTransportClient = nhumeTransportClient;
        this.orosSpecimenClient = orosSpecimenClient;
        this.ritoSafetyClient = ritoSafetyClient;
        this.postopRepository = postopRepository;
        this.admissionService = admissionService;
        this.objectMapper = objectMapper;
    }

    private UUID tenant() { return TrustContextHolder.require().tenantId(); }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null && !a.isBlank() ? a : "system";
        } catch (IllegalStateException e) { return "system"; }
    }

    private String facilityRef() {
        try {
            UUID f = TrustContextHolder.require().facilityId();
            return f != null ? f.toString() : null;
        } catch (IllegalStateException e) { return null; }
    }

    // ── 1. OROS PROCEDURE intake → theatre case ──────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> intakeFromOrosOrder(Map<String, Object> body) {
        String cpid = ClinicalPayloadMapper.str(body, "patientId", "patient_id", "subjectCpid", "subject_cpid");
        if (cpid == null || cpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        String procedureName = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "procedureName", "procedure_name"), "Procedure");
        String procedureCode = ClinicalPayloadMapper.str(body, "procedureCode", "procedure_code");
        String encounterRef = ClinicalPayloadMapper.str(body, "encounterId", "encounter_id", "encounterRef");
        String priority = normalisePriority(ClinicalPayloadMapper.str(body, "triagePriority", "triage_priority", "priority"));
        String orosPriority = orosPriorityFor(priority);

        // Place / link the canonical OROS PROCEDURE order (OROS is the SoR for the order intent).
        String orosOrderId = ClinicalPayloadMapper.str(body, "orosOrderId", "oros_order_id");
        if (orosOrderId == null || orosOrderId.isBlank()) {
            List<Map<String, Object>> items = List.of(buildOrderItem(procedureName, procedureCode));
            orosOrderId = orosOrderClient.placeOrder("PROCEDURE", orosPriority, cpid, encounterRef,
                    "Theatre case: " + procedureName, items);
        }

        // Idempotency: one theatre case per OROS order.
        if (orosOrderId != null) {
            Optional<ProcedureEpisodeEntity> existing =
                    episodeRepository.findByTenantIdAndOrosOrderId(tenant(), orosOrderId);
            if (existing.isPresent()) {
                return episodeService.getEpisode(existing.get().getEpisodeId());
            }
        }

        // Build the episode via the canonical pipeline (seeds WHO checklist etc.), then stamp theatre fields.
        Map<String, Object> created = episodeService.createEpisode(body);
        UUID episodeId = UUID.fromString(String.valueOf(created.get("id")));
        ProcedureEpisodeEntity e = episodeRepository.findById(episodeId).orElseThrow();
        e.setOrosOrderId(orosOrderId);
        e.setTriagePriority(priority);
        episodeRepository.save(e);

        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.intake", Map.of(
                "episode_id", episodeId.toString(),
                "patient_id", cpid,
                "oros_order_id", orosOrderId != null ? orosOrderId : "",
                "triage_priority", priority));
        return episodeService.getEpisode(episodeId);
    }

    @Transactional
    public Map<String, Object> setTriage(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String priority = normalisePriority(ClinicalPayloadMapper.str(body, "triagePriority", "triage_priority", "priority"));
        e.setTriagePriority(priority);
        episodeRepository.save(e);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.triaged",
                Map.of("episode_id", episodeId.toString(), "triage_priority", priority));
        return episodeService.getEpisode(episodeId);
    }

    public List<Map<String, Object>> triageQueue() {
        return episodeRepository.findByTenantIdAndStatusInOrderByScheduledAtAsc(tenant(), ACTIVE_STATUSES)
                .stream()
                .sorted(Comparator.comparingInt(e -> triageRank(e.getTriagePriority())))
                .map(this::queueRow).toList();
    }

    // ── 2/3. Booking readiness — owner-routed, fails safely with blockers ─────────────────────────
    @Transactional
    public Map<String, Object> evaluateReadiness(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        readinessRepository.findByEpisodeIdOrderByCheckedAtDesc(episodeId); // touch (no-op read; results re-snapshotted)
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, String>> allBlockers = new ArrayList<>();

        // ROOM — explicit theatre room must be assigned (Tuso service-point id stamped on episode/body).
        UUID roomId = ClinicalPayloadMapper.uuid(body, "theatreRoomId", "theatre_room_id");
        if (roomId != null) { e.setTheatreRoomId(roomId); episodeRepository.save(e); }
        if (e.getTheatreRoomId() == null) {
            results.add(record(episodeId, "ROOM", "inpatient", "BLOCKED", null,
                    List.of(Map.of("code", "NO_ROOM", "message", "No theatre room assigned")), Map.of()));
            allBlockers.add(Map.of("code", "NO_ROOM", "message", "No theatre room assigned"));
        } else {
            results.add(record(episodeId, "ROOM", "tuso", "READY", e.getTheatreRoomId().toString(),
                    List.of(), Map.of("theatre_room_id", e.getTheatreRoomId().toString())));
        }

        // TEAM — surgeon scope (varapi) + roster availability (vashandi).
        // Wave 1 fix: the varapi domain scope value is "SURGERY" (V002: GENERAL_PRACTICE/SURGERY/PRESCRIBE),
        // not "SURGEON" — the previous literal never matched a seeded privilege.
        ReadinessResult surgeon = readinessClient.checkProviderScope(e.getSurgeonId(), "SURGERY");
        results.add(record(episodeId, "TEAM", "varapi", surgeon.status(), surgeon.ownerRef(),
                surgeon.blockers(), Map.of("role", "SURGEON", "provider_id", nullSafe(e.getSurgeonId()))));
        collectBlockers(allBlockers, surgeon, "surgeon");

        ReadinessResult roster = readinessClient.checkTeamAvailability(facilityRef());
        results.add(record(episodeId, "TEAM", "vashandi", roster.status(), roster.ownerRef(),
                roster.blockers(), roster.detail()));
        collectBlockers(allBlockers, roster, "team");

        // EQUIPMENT — asset-registry readiness.
        ReadinessResult equip = readinessClient.checkEquipment(facilityRef());
        results.add(record(episodeId, "EQUIPMENT", "asset-registry", equip.status(), equip.ownerRef(),
                equip.blockers(), equip.detail()));
        collectBlockers(allBlockers, equip, "equipment");

        // PACK / stock — must have at least one reserved consumable (Dura ledger ref) or be flagged not-checked.
        ReadinessResult anaesthesia = anaesthesiaReadiness(episodeId);
        results.add(record(episodeId, "ANAESTHESIA", "inpatient", anaesthesia.status(), anaesthesia.ownerRef(),
                anaesthesia.blockers(), anaesthesia.detail()));
        collectBlockers(allBlockers, anaesthesia, "anaesthesia");

        // ══ Wave 1 clinical-safety: BLOOD readiness gate rewrite ═════════════════════════════════════
        // WAS a false-ready: merely PLACING an OROS BLOOD_BANK order returned READY. Now the gate
        // requires REAL MADI truth — a live reservation (reservation_status=RESERVED) backed by a
        // COMPATIBLE crossmatch — read through MadiBloodClient.getOrderDetail. Anything short of that
        // is BLOCKED with the real MADI blocker. bloodRequired=false → NOT_REQUIRED (gate skipped, both
        // ±transfusion cases reach COMPLETED). Coordinator: this whole block is the reconciled BLOOD gate.
        boolean bloodRequired = Boolean.TRUE.equals(body.get("bloodRequired"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("blood_required", "false")))
                || bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)
                        .map(ProcedureBloodLinkEntity::isRequired).orElse(false);
        if (bloodRequired) {
            ProcedureBloodLinkEntity link = bloodLinkRepository
                    .findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId).orElse(null);
            if (link == null || link.getMadiOrderId() == null) {
                results.add(record(episodeId, "BLOOD", "madi", "BLOCKED", null,
                        List.of(Map.of("code", "NO_BLOOD_REQUEST",
                                "message", "Blood required but no MADI blood request placed (call requestBlood)")), Map.of()));
                allBlockers.add(Map.of("code", "NO_BLOOD_REQUEST", "message", "Blood not requested from MADI"));
            } else {
                MadiBloodClient.BloodOrderView view = madiBloodClient.getOrderDetail(link.getMadiOrderId());
                // Project MADI truth back onto the link (read-through; MADI stays SoR).
                if (view.available()) {
                    link.setReservationStatus(view.reservationStatus());
                    link.setCrossmatchStatus(view.crossmatchStatus());
                    bloodLinkRepository.save(link);
                }
                boolean reserved = view.reserved() && "COMPATIBLE".equals(view.crossmatchStatus());
                if (!view.available()) {
                    results.add(record(episodeId, "BLOOD", "madi", "UNAVAILABLE", link.getMadiOrderId(),
                            List.of(Map.of("code", "MADI_UNAVAILABLE",
                                    "message", "MADI unavailable — blood reservation could not be confirmed")), Map.of()));
                    allBlockers.add(Map.of("code", "MADI_UNAVAILABLE", "message", "Blood readiness unconfirmed (MADI down)"));
                } else if (reserved) {
                    results.add(record(episodeId, "BLOOD", "madi", "READY", "madi:" + link.getMadiOrderId(),
                            List.of(), Map.of("madi_order_id", link.getMadiOrderId(),
                                    "reservation_status", "RESERVED", "crossmatch_status", "COMPATIBLE")));
                } else {
                    results.add(record(episodeId, "BLOOD", "madi", "BLOCKED", link.getMadiOrderId(),
                            List.of(Map.of("code", "BLOOD_NOT_RESERVED",
                                    "message", "MADI order " + view.status()
                                            + " — needs RESERVED + COMPATIBLE crossmatch before theatre")),
                            Map.of("madi_status", nullSafe(view.status()),
                                    "reservation_status", nullSafe(view.reservationStatus()),
                                    "crossmatch_status", nullSafe(view.crossmatchStatus()))));
                    allBlockers.add(Map.of("code", "BLOOD_NOT_RESERVED",
                            "message", "Blood not yet reserved/crossmatched by MADI"));
                }
            }
        } else {
            // Explicitly NOT_REQUIRED — recorded so the projection is honest, gate contributes no blocker.
            results.add(record(episodeId, "BLOOD", "madi", "NOT_REQUIRED", null, List.of(),
                    Map.of("note", "No blood required for this case")));
        }
        // ═════════════════════════════════════════════════════════════════════════════════════════════

        boolean bookable = allBlockers.isEmpty();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode_id", episodeId.toString());
        out.put("bookable", bookable);
        out.put("checks", results);
        out.put("blockers", allBlockers);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.readiness.evaluated", Map.of(
                "episode_id", episodeId.toString(), "bookable", bookable, "blocker_count", allBlockers.size()));
        return out;
    }

    public List<Map<String, Object>> listReadiness(UUID episodeId) {
        requireEpisode(episodeId);
        return readinessRepository.findByEpisodeIdOrderByCheckedAtDesc(episodeId).stream()
                .map(this::readinessRow).toList();
    }

    /** Confirm a booking — only succeeds when readiness has no blockers; otherwise 409 with blockers. */
    @Transactional
    public Map<String, Object> confirmBooking(UUID episodeId, Map<String, Object> body) {
        Map<String, Object> readiness = evaluateReadiness(episodeId, body);
        boolean bookable = Boolean.TRUE.equals(readiness.get("bookable"));
        boolean override = Boolean.TRUE.equals(body.get("emergencyOverride"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("emergency_override", "false")));
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        if (!bookable && !override) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "BOOKING_BLOCKED");
            err.put("blockers", readiness.get("blockers"));
            err.put("checks", readiness.get("checks"));
            throw new BookingBlockedException(err);
        }
        if (!bookable) {
            String reason = ClinicalPayloadMapper.str(body, "emergencyOverrideReason", "emergency_override_reason", "reason");
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "emergencyOverrideReason is required to override booking blockers");
            }
            e.setEmergencyOverride(true);
            e.setEmergencyOverrideReason(reason);
        }
        if ("BOOKED".equals(e.getStatus()) || e.getStatus() == null) {
            // already booked at intake; mark scheduled time if provided
        }
        String scheduled = ClinicalPayloadMapper.str(body, "scheduledAt", "scheduled_at");
        if (scheduled != null) e.setScheduledAt(OffsetDateTime.parse(scheduled));
        episodeRepository.save(e);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.booked", Map.of(
                "episode_id", episodeId.toString(), "override", !bookable,
                "triage_priority", e.getTriagePriority()));
        Map<String, Object> out = new LinkedHashMap<>(episodeService.getEpisode(episodeId));
        out.put("booking_override", !bookable);
        return out;
    }

    private ReadinessResult anaesthesiaReadiness(UUID episodeId) {
        // Anaesthesia readiness = an ANAESTHESIA preop assessment cleared for theatre exists.
        Map<String, Object> detail = episodeService.getEpisode(episodeId);
        Object preops = detail.get("preop_assessments");
        boolean cleared = false;
        if (preops instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof Map<?, ?> m && "ANAESTHESIA".equals(m.get("assessment_type"))
                        && Boolean.TRUE.equals(m.get("cleared_for_theatre"))) {
                    cleared = true; break;
                }
            }
        }
        return cleared ? ReadinessResult.ready("inpatient:anaesthesia", Map.of("cleared", true))
                : ReadinessResult.blocked("ANAESTHESIA_NOT_CLEARED",
                "No anaesthesia assessment cleared for theatre", Map.of());
    }

    // ── 11. WHO checklist start-gating + emergency override ───────────────────────────────────────
    @Transactional
    public Map<String, Object> startWithChecklistGate(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        boolean override = Boolean.TRUE.equals(body.get("emergencyOverride"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("emergency_override", "false")));
        boolean signInComplete = phaseComplete(episodeId, "SIGN_IN");
        if (!signInComplete) {
            if (!override) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "WHO Sign-In checklist incomplete — cannot start theatre (use emergencyOverride with reason)");
            }
            String reason = ClinicalPayloadMapper.str(body, "emergencyOverrideReason", "emergency_override_reason", "reason");
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "emergencyOverrideReason is required to override the WHO checklist");
            }
            e.setEmergencyOverride(true);
            e.setEmergencyOverrideReason(reason);
            episodeRepository.save(e);
            appendOutbox("SAFETY", episodeId.toString(), "theatre.checklist.override", Map.of(
                    "episode_id", episodeId.toString(), "reason", reason, "actor", actor()));
        }
        // Delegate the actual start (consent gate etc.) to the pipeline.
        Map<String, Object> result = episodeService.startProcedure(episodeId, body);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.started", Map.of(
                "episode_id", episodeId.toString(), "override", override && !signInComplete));
        return result;
    }

    // ── 12. Signable operative / procedure note (Butano-linked) ───────────────────────────────────
    @Transactional
    public Map<String, Object> draftNote(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        ProcedureNoteEntity note = noteRepository.findByEpisodeId(episodeId).orElseGet(ProcedureNoteEntity::new);
        if ("SIGNED".equals(note.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operative note already signed; create an amendment");
        }
        note.setEpisodeId(episodeId);
        note.setStatus("DRAFT");
        note.setPerformedProcedure(ClinicalPayloadMapper.str(body, "performedProcedure", "performed_procedure"));
        note.setFindings(ClinicalPayloadMapper.str(body, "findings"));
        note.setSpecimens(ClinicalPayloadMapper.str(body, "specimens"));
        note.setImplants(ClinicalPayloadMapper.str(body, "implants"));
        note.setEstimatedBloodLossMl(ClinicalPayloadMapper.integer(body, "estimatedBloodLossMl", "estimated_blood_loss_ml"));
        note.setComplications(ClinicalPayloadMapper.str(body, "complications"));
        Object counts = body.getOrDefault("countsCorrect", body.get("counts_correct"));
        if (counts != null) note.setCountsCorrect(Boolean.parseBoolean(String.valueOf(counts)));
        note.setPostopPlan(ClinicalPayloadMapper.str(body, "postopPlan", "postop_plan"));
        note.setCreatedBy(actor());
        try {
            note.setNoteJson(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            note.setNoteJson(String.valueOf(body));
        }
        note = noteRepository.save(note);
        return noteRow(note);
    }

    @Transactional
    public Map<String, Object> signNote(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureNoteEntity note = noteRepository.findByEpisodeId(episodeId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No draft operative note to sign"));
        String providerId = ClinicalPayloadMapper.str(body, "signedProviderId", "signed_provider_id", "providerId");
        if (providerId == null || providerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signedProviderId is required to sign an operative note");
        }
        // Authorise the signer's surgical scope through Varapi (owner of provider scope).
        // Wave 1 fix: domain scope value is "SURGERY" (not "SURGEON").
        ReadinessResult scope = readinessClient.checkProviderScope(providerId, "SURGERY");
        if ("BLOCKED".equals(scope.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Signer " + providerId + " lacks an active SURGERY scope to sign the operative note");
        }
        note.setStatus("SIGNED");
        note.setSignedBy(actor());
        note.setSignedProviderId(providerId);
        note.setSignedAt(OffsetDateTime.now());

        // Write the clinical record to Butano (Procedure + DocumentReference). Best-effort linkage.
        String procRef = butanoClient.writeProcedure(e.getSubjectCpid(),
                e.getEncounterId() != null ? e.getEncounterId().toString() : null,
                e.getProcedureName(), e.getProcedureCode(), note);
        if (procRef != null) note.setButanoProcedureRef(procRef);
        String docRef = butanoClient.writeOperativeNoteDocument(e.getSubjectCpid(),
                e.getEncounterId() != null ? e.getEncounterId().toString() : null, note);
        if (docRef != null) note.setButanoDocumentRef(docRef);
        note = noteRepository.save(note);

        // Wave 1 clinical-safety: parse note specimens → OROS orders + NHUME dispatch + procedure_specimen.
        processSpecimensFromNote(e, note);

        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.note.signed", Map.of(
                "episode_id", episodeId.toString(), "signed_provider_id", providerId,
                "butano_procedure_ref", nullSafe(note.getButanoProcedureRef())));
        return noteRow(note);
    }

    public Map<String, Object> getNote(UUID episodeId) {
        requireEpisode(episodeId);
        return noteRepository.findByEpisodeId(episodeId).map(this::noteRow).orElse(Map.of("status", "NONE"));
    }

    // ── 14. PACU + 10. transfer destination incl. death pathway ──────────────────────────────────
    @Transactional
    public Map<String, Object> recordPacuDisposition(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String disposition = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "disposition"), "WARD").toUpperCase();
        if ("DEATH".equals(disposition) || "MORTUARY".equals(disposition) || "DECEASED".equals(disposition)) {
            return routeDeathInTheatre(episodeId, body);
        }
        Map<String, Object> result = episodeService.completePostop(episodeId, body);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.pacu.disposition", Map.of(
                "episode_id", episodeId.toString(), "disposition", disposition));
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════════════════════════
    // ── Wave 4 §12/§13 — PACU depth: time-series obs, discharge-readiness gate, escalation,
    //    unplanned-ICU / return-to-theatre, and a governed PACU discharge decision with a REAL NHUME
    //    movement + theatre→inpatient admission link. The PACU stay is its own tracked case phase.
    // ══════════════════════════════════════════════════════════════════════════════════════════════

    /** Record one time-series PACU observation (delegates to the episode service, then emits the event). */
    @Transactional
    public Map<String, Object> recordPacuObservation(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        Map<String, Object> row = episodeService.recordPacuObservation(episodeId, body);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.pacu.observation", Map.of(
                "episode_id", episodeId.toString(),
                "seq", row.getOrDefault("seq", 0),
                "aldrete_score", row.get("aldrete_score") != null ? row.get("aldrete_score") : -1,
                "discharge_readiness_band", nullSafe((String) row.get("discharge_readiness_band"))));
        return row;
    }

    public List<Map<String, Object>> listPacuObservations(UUID episodeId) {
        return episodeService.listPacuObservations(episodeId);
    }

    public Map<String, Object> dischargeReadiness(UUID episodeId) {
        return episodeService.computeDischargeReadiness(episodeId);
    }

    /**
     * Escalate a deteriorating recovery patient to the anaesthetist/surgeon. Routes a best-effort
     * RITO quality/safety signal (RITO owns quality) and emits a SAFETY event that khuluma/rito
     * consume — never fabricating an owner record. The escalation is stamped on the postop record.
     */
    @Transactional
    public Map<String, Object> escalatePacu(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String target = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "escalateTo", "escalate_to", "target"), "ANAESTHETIST").toUpperCase();
        String reason = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "reason", "description"), "PACU deterioration — clinician escalation");
        String severity = normaliseCaseSeverity(ClinicalPayloadMapper.str(body, "severity"));
        String idem = idempotency(episodeId, "pacu-escalate-" + target);
        String caseRef = ritoSafetyClient.createCase("CLINICAL_INCIDENT",
                "PACU escalation to " + target, reason, severity, "PERIOPERATIVE",
                e.getSubjectCpid(), Map.of("episode_id", episodeId.toString(), "escalate_to", target), idem);

        ProcedureSafetyEventEntity ev = new ProcedureSafetyEventEntity();
        ev.setEpisodeId(episodeId);
        ev.setCategory("PACU_ESCALATION");
        ev.setSeverity(severity);
        ev.setDescription(reason);
        ev.setRoutedOwner("rito");
        ev.setOwnerRef(caseRef);
        ev.setRoutedStatus(caseRef != null ? "ROUTED" : "OWNER_UNAVAILABLE");
        ev.setReportedBy(actor());
        safetyRepository.save(ev);

        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId)
                .orElseGet(ProcedurePostopRecordEntity::new);
        postop.setEpisodeId(episodeId);
        postop.setEscalatedTo(target);
        postop.setEscalationRef(caseRef);
        postop.setEscalatedAt(OffsetDateTime.now());
        postopRepository.save(postop);

        appendOutbox("SAFETY", episodeId.toString(), "theatre.pacu.escalated", Map.of(
                "episode_id", episodeId.toString(), "escalate_to", target, "severity", severity,
                "reason", reason, "patient_id", e.getSubjectCpid(),
                "rito_case_ref", nullSafe(caseRef),
                "routed_status", caseRef != null ? "ROUTED" : "OWNER_UNAVAILABLE"));
        return safetyRow(ev);
    }

    /** Return-to-theatre: a deteriorating PACU patient goes back to a re-operative state (same episode). */
    @Transactional
    public Map<String, Object> returnToTheatre(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        Map<String, Object> result = episodeService.returnToTheatre(episodeId, body);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.returned-to-theatre", Map.of(
                "episode_id", episodeId.toString(),
                "reason", nullSafe(ClinicalPayloadMapper.str(body, "reason", "description"))));
        return result;
    }

    /**
     * The PACU discharge decision (§12) → destination + a REAL NHUME movement + (for WARD/ICU/HDU) a
     * create-or-link inpatient admission so postop orders/observations/outcome tie back to the case.
     * Aldrete discharge-readiness gates a ward discharge unless explicitly overridden with justification.
     */
    @Transactional
    public Map<String, Object> pacuDischargeDecision(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String destination = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "destination", "disposition"), "WARD").toUpperCase();
        if (List.of("DEATH", "MORTUARY", "DECEASED").contains(destination)) {
            return routeDeathInTheatre(episodeId, body);
        }

        Map<String, Object> readiness = episodeService.computeDischargeReadiness(episodeId);
        boolean ready = Boolean.TRUE.equals(readiness.get("ready"));
        boolean override = Boolean.TRUE.equals(body.get("readinessOverride"))
                || Boolean.TRUE.equals(body.get("readiness_override"));
        if (List.of("WARD", "DISCHARGE").contains(destination) && !ready && !override) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PACU discharge blocked — Aldrete discharge-readiness not met (band "
                    + readiness.get("band") + "). Record a ready observation or set readinessOverride with justification.");
        }

        Map<String, Object> result = episodeService.completePostop(episodeId, body);
        ProcedurePostopRecordEntity postop = postopRepository.findByEpisodeId(episodeId).orElseThrow();
        postop.setDestination(destination);
        postop.setDischargeDecidedAt(OffsetDateTime.now());
        postop.setDischargeDecidedBy(actor());
        boolean unplannedIcu = "ICU".equals(destination)
                && (Boolean.TRUE.equals(body.get("unplanned")) || Boolean.TRUE.equals(body.get("unplannedIcu")));
        postop.setUnplannedIcu(unplannedIcu);

        String transportLeg = null;
        if (List.of("WARD", "ICU", "HDU").contains(destination)) {
            UUID admissionRef = ensureAdmissionLink(e, destination, body);
            if (admissionRef != null) {
                e.setAdmissionRef(admissionRef);
                episodeRepository.save(e);
                postop.setLinkedAdmissionRef(admissionRef);
            }
            transportLeg = List.of("ICU", "HDU").contains(destination) ? "PACU_TO_ICU" : "PACU_TO_WARD";
        }
        if (transportLeg != null) {
            Map<String, Object> leg = createTransportLeg(e, "PATIENT_MOVEMENT", transportLeg, "PATIENT",
                    e.getSubjectCpid(), null);
            Object deliveryId = leg.get("nhume_delivery_id");
            if (deliveryId != null) postop.setNhumeDeliveryId(String.valueOf(deliveryId));
        }
        postopRepository.save(postop);

        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.pacu.discharge-decision", Map.of(
                "episode_id", episodeId.toString(), "destination", destination,
                "unplanned_icu", unplannedIcu, "ready", ready,
                "readiness_band", nullSafe(String.valueOf(readiness.getOrDefault("band", ""))),
                "admission_ref", nullSafe(e.getAdmissionRef() != null ? e.getAdmissionRef().toString() : null),
                "nhume_delivery_id", nullSafe(postop.getNhumeDeliveryId())));

        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("destination", destination);
        out.put("admission_ref", e.getAdmissionRef() != null ? e.getAdmissionRef().toString() : null);
        out.put("nhume_delivery_id", postop.getNhumeDeliveryId());
        out.put("unplanned_icu", unplannedIcu);
        return out;
    }

    /**
     * Continuity link: prefer an existing active admission for the patient at this facility, else create a
     * post-operative census admission (inpatient owns the ward census; theatre only references it).
     * Best-effort — a census failure never blocks the PACU discharge decision.
     */
    private UUID ensureAdmissionLink(ProcedureEpisodeEntity e, String destination, Map<String, Object> body) {
        if (e.getAdmissionRef() != null) return e.getAdmissionRef();
        UUID facilityId;
        try {
            facilityId = TrustContextHolder.require().facilityId();
        } catch (IllegalStateException ex) {
            facilityId = null;
        }
        if (facilityId == null) {
            log.warn("INPATIENT-THEATRE: no facility context — cannot link postop admission for episode {}", e.getEpisodeId());
            return null;
        }
        try {
            List<AdmissionEntity> active =
                    admissionService.findActiveAdmissionsForPatientAtFacility(e.getSubjectCpid(), facilityId);
            if (!active.isEmpty()) return active.get(0).getAdmissionRef();

            CreateAdmissionRequest req = new CreateAdmissionRequest();
            req.setTenantId(tenant());
            req.setSubjectCpid(e.getSubjectCpid());
            req.setEncounterId(e.getEncounterId() != null ? e.getEncounterId()
                    : UUID.nameUUIDFromBytes(("theatre-episode:" + e.getEpisodeId()).getBytes()));
            req.setFacilityId(facilityId);
            req.setWardId(ClinicalPayloadMapper.uuid(body, "wardId", "ward_id"));
            req.setBedId(ClinicalPayloadMapper.uuid(body, "bedId", "bed_id"));
            req.setAdmittingDiagnosis("Post-operative — " + Objects.requireNonNullElse(e.getProcedureName(), "procedure"));
            req.setAdmissionType("POST_OPERATIVE");
            if (List.of("ICU", "HDU").contains(destination)) req.setActivityLevel("CRITICAL_CARE");
            AdmissionEntity a = admissionService.createAdmission(req);
            return a.getAdmissionRef();
        } catch (RuntimeException ex) {
            log.warn("INPATIENT-THEATRE: postop admission link best-effort failed for episode {}: {}",
                    e.getEpisodeId(), ex.getMessage());
            return null;
        }
    }

    private static String normaliseCaseSeverity(String raw) {
        if (raw == null) return "HIGH";
        String s = raw.toUpperCase();
        return List.of("LOW", "MODERATE", "HIGH", "CRITICAL").contains(s) ? s : "HIGH";
    }

    // ── 16. Cancellation — release owner reservations, audit ─────────────────────────────────────
    @Transactional
    public Map<String, Object> cancel(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        if (List.of("COMPLETED", "RECOVERED", "CANCELLED").contains(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel a " + e.getStatus() + " case");
        }
        String reason = ClinicalPayloadMapper.str(body, "reason", "cancellationReason", "cancellation_reason");
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cancellation reason is required");
        }
        e.setStatus("CANCELLED");
        e.setCancellationReason(reason);
        e.setCancelledBy(actor());
        e.setCancelledAt(OffsetDateTime.now());
        episodeRepository.save(e);

        // Release the OROS PROCEDURE order (owner releases its own reservation/state).
        if (e.getOrosOrderId() != null) {
            orosOrderClient.cancelOrder(e.getOrosOrderId(), "Theatre case cancelled: " + reason);
        }
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.case.cancelled", Map.of(
                "episode_id", episodeId.toString(), "reason", reason, "cancelled_by", actor()));
        return episodeService.getEpisode(episodeId);
    }

    // ── 17. Safety event → owner routing (Rito/Madi/asset-registry/PCT-death) ────────────────────
    @Transactional
    public Map<String, Object> reportSafetyEvent(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String category = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "category"), "ADVERSE_EVENT").toUpperCase();
        if ("DEATH".equals(category)) {
            return routeDeathInTheatre(episodeId, body);
        }
        String description = Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "description"),
                "Theatre safety event");
        String owner = ownerFor(category);
        ProcedureSafetyEventEntity ev = new ProcedureSafetyEventEntity();
        ev.setEpisodeId(episodeId);
        ev.setCategory(category);
        ev.setSeverity(ClinicalPayloadMapper.str(body, "severity"));
        ev.setDescription(description);
        ev.setRoutedOwner(owner);
        ev.setReportedBy(actor());
        // Link to the owner. We emit an outbox event the owner consumes; the owner remains the SoR for
        // the investigation. (No fake owner record is created here.)
        ev.setRoutedStatus("ROUTED");
        ev = safetyRepository.save(ev);
        appendOutbox("SAFETY", episodeId.toString(), "theatre.safety." + category.toLowerCase(), Map.of(
                "episode_id", episodeId.toString(), "category", category, "routed_owner", owner,
                "severity", nullSafe(ev.getSeverity()), "description", description,
                "patient_id", e.getSubjectCpid()));
        return safetyRow(ev);
    }

    public List<Map<String, Object>> listSafetyEvents(UUID episodeId) {
        requireEpisode(episodeId);
        return safetyRepository.findByEpisodeIdOrderByReportedAtDesc(episodeId).stream()
                .map(this::safetyRow).toList();
    }

    @Transactional
    public Map<String, Object> routeDeathInTheatre(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        boolean resus = Boolean.TRUE.equals(body.get("resuscitationAttempted"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("resuscitation_attempted", "true")));
        String manner = ClinicalPayloadMapper.str(body, "suspectedManner", "suspected_manner");
        OffsetDateTime when = OffsetDateTime.now();
        String deathCaseRef = deathClient.confirmDeathInTheatre(e.getSubjectCpid(),
                e.getEncounterId() != null ? e.getEncounterId().toString() : null, when, resus, manner);

        ProcedureSafetyEventEntity ev = new ProcedureSafetyEventEntity();
        ev.setEpisodeId(episodeId);
        ev.setCategory("DEATH");
        ev.setSeverity("SENTINEL");
        ev.setDescription(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "description"),
                "Death in theatre"));
        ev.setRoutedOwner("pct-death");
        ev.setOwnerRef(deathCaseRef);
        ev.setRoutedStatus(deathCaseRef != null ? "ROUTED" : "OWNER_UNAVAILABLE");
        ev.setReportedBy(actor());
        safetyRepository.save(ev);

        e.setStatus("DECEASED");
        e.setDeathCaseRef(deathCaseRef);
        e.setCompletedAt(when);
        episodeRepository.save(e);

        appendOutbox("SAFETY", episodeId.toString(), "theatre.death.routed", Map.of(
                "episode_id", episodeId.toString(), "patient_id", e.getSubjectCpid(),
                "death_case_ref", nullSafe(deathCaseRef),
                "routed_status", deathCaseRef != null ? "ROUTED" : "OWNER_UNAVAILABLE"));
        Map<String, Object> out = new LinkedHashMap<>(episodeService.getEpisode(episodeId));
        out.put("death_case_ref", deathCaseRef);
        out.put("death_routed", deathCaseRef != null);
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────
    private Map<String, Object> record(UUID episodeId, String domain, String owner, String status, String ownerRef,
                                       List<Map<String, String>> blockers, Map<String, Object> detail) {
        ProcedureReadinessCheckEntity r = new ProcedureReadinessCheckEntity();
        r.setEpisodeId(episodeId);
        r.setDomain(domain);
        r.setOwnerService(owner);
        r.setStatus(status);
        r.setOwnerRef(ownerRef);
        r.setCheckedBy(actor());
        try {
            r.setBlockersJson(objectMapper.writeValueAsString(blockers));
            r.setDetailJson(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            r.setBlockersJson("[]");
        }
        readinessRepository.save(r);
        return readinessRow(r);
    }

    private void collectBlockers(List<Map<String, String>> all, ReadinessResult res, String label) {
        if (!res.ready()) {
            if (res.blockers().isEmpty()) {
                all.add(Map.of("code", label.toUpperCase() + "_NOT_READY", "message", label + " not ready"));
            } else {
                all.addAll(res.blockers());
            }
        }
    }

    private boolean phaseComplete(UUID episodeId, String phase) {
        List<ProcedureChecklistItemEntity> items =
                checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, phase);
        return !items.isEmpty() && items.stream().allMatch(ProcedureChecklistItemEntity::isCompleted);
    }

    private Map<String, Object> buildOrderItem(String name, String code) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code != null ? code : "PROC");
        item.put("displayName", name);
        item.put("quantity", 1);
        if (code != null) item.put("procedureCode", code);
        return item;
    }

    private static String normalisePriority(String raw) {
        if (raw == null) return "ELECTIVE";
        String p = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return TRIAGE_PRIORITIES.contains(p) ? p : "ELECTIVE";
    }

    private static String orosPriorityFor(String triage) {
        return switch (triage) {
            case "IMMEDIATE", "EMERGENCY" -> "STAT";
            case "URGENT" -> "URGENT";
            default -> "ROUTINE";
        };
    }

    private static int triageRank(String triage) {
        return switch (triage == null ? "ELECTIVE" : triage) {
            case "IMMEDIATE" -> 0;
            case "EMERGENCY" -> 1;
            case "URGENT" -> 2;
            case "ELECTIVE" -> 3;
            case "DAY_CASE" -> 4;
            default -> 5;
        };
    }

    private static String ownerFor(String category) {
        return switch (category) {
            case "BLOOD_REACTION" -> "madi";
            case "DEVICE_INCIDENT" -> "asset-registry";
            case "MEDICATION" -> "dura";
            case "DEATH" -> "pct-death";
            default -> "rito";   // ADVERSE_EVENT, NEAR_MISS, RETAINED_ITEM → quality/safety
        };
    }

    private static String nullSafe(String s) { return s != null ? s : ""; }

    private ProcedureEpisodeEntity requireEpisode(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedure episode not found"));
    }

    private Map<String, Object> queueRow(ProcedureEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEpisodeId().toString());
        m.put("patient_id", e.getSubjectCpid());
        m.put("procedure_name", e.getProcedureName());
        m.put("status", e.getStatus());
        m.put("triage_priority", e.getTriagePriority());
        m.put("scheduled_at", e.getScheduledAt());
        m.put("theatre_room_id", e.getTheatreRoomId() != null ? e.getTheatreRoomId().toString() : null);
        m.put("surgeon_id", e.getSurgeonId());
        m.put("oros_order_id", e.getOrosOrderId());
        return m;
    }

    private Map<String, Object> readinessRow(ProcedureReadinessCheckEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getReadinessId().toString());
        m.put("domain", r.getDomain());
        m.put("owner_service", r.getOwnerService());
        m.put("status", r.getStatus());
        m.put("owner_ref", r.getOwnerRef());
        m.put("blockers_json", r.getBlockersJson());
        m.put("detail_json", r.getDetailJson());
        m.put("checked_by", r.getCheckedBy());
        m.put("checked_at", r.getCheckedAt());
        return m;
    }

    private Map<String, Object> noteRow(ProcedureNoteEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getNoteId().toString());
        m.put("episode_id", n.getEpisodeId().toString());
        m.put("status", n.getStatus());
        m.put("performed_procedure", n.getPerformedProcedure());
        m.put("findings", n.getFindings());
        m.put("specimens", n.getSpecimens());
        m.put("implants", n.getImplants());
        m.put("estimated_blood_loss_ml", n.getEstimatedBloodLossMl());
        m.put("complications", n.getComplications());
        m.put("counts_correct", n.getCountsCorrect());
        m.put("postop_plan", n.getPostopPlan());
        m.put("signed_by", n.getSignedBy());
        m.put("signed_provider_id", n.getSignedProviderId());
        m.put("signed_at", n.getSignedAt());
        m.put("butano_procedure_ref", n.getButanoProcedureRef());
        m.put("butano_document_ref", n.getButanoDocumentRef());
        return m;
    }

    private Map<String, Object> safetyRow(ProcedureSafetyEventEntity ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ev.getSafetyEventId().toString());
        m.put("category", ev.getCategory());
        m.put("severity", ev.getSeverity());
        m.put("description", ev.getDescription());
        m.put("routed_owner", ev.getRoutedOwner());
        m.put("owner_ref", ev.getOwnerRef());
        m.put("routed_status", ev.getRoutedStatus());
        m.put("reported_by", ev.getReportedBy());
        m.put("reported_at", ev.getReportedAt());
        return m;
    }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
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
            throw new RuntimeException("Failed to serialize theatre outbox payload", ex);
        }
        outboxRepository.save(outbox);
    }

    // ══════════════════════════════════════════════════════════════════════════════════════════════
    // ── Wave 1 clinical-safety ──────────────────────────────────────────────────────────────────────
    // Blood / transport / specimen / count orchestration-by-reference. Peers (MADI/NHUME/OROS/RITO/
    // Butano) remain the SoR; the *_link rows below hold peer ids + a status projection, written in the
    // SAME transaction as the episode FSM mutation. Peer calls carry an Idempotency-Key. HAZARD: never
    // put null values in an outbox payload map (EventEnvelope Map.copyOf NPEs on null).
    // ══════════════════════════════════════════════════════════════════════════════════════════════

    private static final List<String> BLOOD_TERMINAL = List.of("TRANSFUSED", "RECONCILED", "RETURNED", "NOT_REQUIRED");

    // ── 1. Blood: request → issue&transport → administer → resolve ────────────────────────────────
    @Transactional
    public Map<String, Object> requestBlood(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureBloodLinkEntity link = bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)
                .filter(l -> l.getMadiOrderId() == null)
                .orElseGet(() -> newBloodLink(episodeId));
        link.setRequired(true);
        link.setUnitsRequested(ClinicalPayloadMapper.integer(body, "units", "units_requested"));
        link.setBloodGroup(ClinicalPayloadMapper.str(body, "bloodGroup", "blood_group"));
        link.setComponentType(ClinicalPayloadMapper.str(body, "componentType", "component_type"));
        String idem = idempotency(episodeId, "blood-request");
        link.setIdempotencyKey(idem);
        String madiOrderId = madiBloodClient.requestBlood(e.getSubjectCpid(), link.getBloodGroup(),
                link.getComponentType(), link.getUnitsRequested(),
                e.getOrosOrderId(), e.getSurgeonId(), idem);
        link.setMadiOrderId(madiOrderId);
        link.setStatus(madiOrderId != null ? "GROUP_SCREEN" : "UNAVAILABLE");
        link.setCreatedBy(actor());
        bloodLinkRepository.save(link);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.blood.requested", Map.of(
                "episode_id", episodeId.toString(), "madi_order_id", nullSafe(madiOrderId),
                "status", link.getStatus()));
        return bloodLinkRow(link);
    }

    @Transactional
    public Map<String, Object> issueAndTransport(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        ProcedureBloodLinkEntity link = requireBloodLink(episodeId);
        MadiBloodClient.BloodOrderView view = madiBloodClient.getOrderDetail(link.getMadiOrderId());
        if (view.available()) {
            link.setReservationStatus(view.reservationStatus());
            link.setCrossmatchStatus(view.crossmatchStatus());
        }
        if (!view.reserved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Blood order " + nullSafe(view.status()) + " — MADI reservation (RESERVED + COMPATIBLE) required before issue/transport");
        }
        // Blood is reserved/issued in MADI: route the physical unit through NHUME (BLOOD_PRODUCT).
        String idem = idempotency(episodeId, "blood-transport");
        NhumeTransportClient.DeliveryView delivery = nhumeTransportClient.createDelivery(
                "BLOOD_PRODUCT", "BLOOD_BANK_TO_THEATRE", link.getMadiOrderId(),
                episodeId.toString(), facilityRef(), idem);
        link.setNhumeDeliveryId(delivery.deliveryId());
        link.setStatus(delivery.deliveryId() != null ? "IN_TRANSIT" : "ISSUED");
        bloodLinkRepository.save(link);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.blood.issued", Map.of(
                "episode_id", episodeId.toString(), "madi_order_id", nullSafe(link.getMadiOrderId()),
                "nhume_delivery_id", nullSafe(link.getNhumeDeliveryId()), "status", link.getStatus()));
        return bloodLinkRow(link);
    }

    @Transactional
    public Map<String, Object> administerBlood(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureBloodLinkEntity link = requireBloodLink(episodeId);
        String idem = idempotency(episodeId, "blood-administer");
        String transfusionEpisodeId = link.getMadiTransfusionEpisodeId();
        if (transfusionEpisodeId == null) {
            transfusionEpisodeId = madiBloodClient.startTransfusion(link.getMadiOrderId(), e.getSubjectCpid(), actor(), idem);
            link.setMadiTransfusionEpisodeId(transfusionEpisodeId);
        }
        // Bedside two-step verify (patient + unit) — never skip the identity check.
        boolean verified = madiBloodClient.preVerify(transfusionEpisodeId, e.getSubjectCpid(),
                ClinicalPayloadMapper.str(body, "bloodUnitId", "blood_unit_id"),
                ClinicalPayloadMapper.str(body, "patientMethod", "patient_method"),
                ClinicalPayloadMapper.str(body, "unitMethod", "unit_method"), actor(), idem);
        madiBloodClient.recordObservation(transfusionEpisodeId, "BASELINE",
                ClinicalPayloadMapper.str(body, "baselineObservation", "baseline_observation"), actor(), idem);
        link.setStatus("TRANSFUSING");
        bloodLinkRepository.save(link);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.blood.administered", Map.of(
                "episode_id", episodeId.toString(),
                "madi_transfusion_episode_id", nullSafe(transfusionEpisodeId),
                "bedside_verified", verified, "status", link.getStatus()));
        return bloodLinkRow(link);
    }

    @Transactional
    public Map<String, Object> resolveBlood(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        ProcedureBloodLinkEntity link = requireBloodLink(episodeId);
        String outcome = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "outcome", "outcome_status"), "COMPLETED").toUpperCase();
        boolean reaction = "REACTION".equals(outcome) || Boolean.TRUE.equals(body.get("reaction"));
        String idem = idempotency(episodeId, "blood-resolve");
        if (reaction) {
            madiBloodClient.recordObservation(link.getMadiTransfusionEpisodeId(), "REACTION",
                    ClinicalPayloadMapper.str(body, "reactionNotes", "reaction_notes"), actor(), idem);
        }
        madiBloodClient.completeTransfusion(link.getMadiTransfusionEpisodeId(),
                reaction ? "REACTION" : "COMPLETED",
                ClinicalPayloadMapper.str(body, "outcomeNotes", "outcome_notes"), actor(), idem);
        boolean returned = Boolean.TRUE.equals(body.get("unitsReturned")) || Boolean.TRUE.equals(body.get("returned"));
        link.setStatus(reaction ? "REACTION" : returned ? "RETURNED" : "TRANSFUSED");
        // Reconciled once the transfusion is closed and (if reaction) routed to Madi haemovigilance.
        if (!reaction && !returned) link.setStatus("RECONCILED");
        bloodLinkRepository.save(link);
        if (reaction) {
            // A transfusion reaction is a safety event routed to Madi (owner of haemovigilance).
            reportSafetyEvent(episodeId, Map.of("category", "BLOOD_REACTION", "severity", "SEVERE",
                    "description", "Transfusion reaction during theatre case"));
        }
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.blood.reconciled", Map.of(
                "episode_id", episodeId.toString(), "outcome", outcome, "status", link.getStatus(),
                "madi_transfusion_episode_id", nullSafe(link.getMadiTransfusionEpisodeId())));
        return bloodLinkRow(link);
    }

    public List<Map<String, Object>> listBloodLinks(UUID episodeId) {
        requireEpisode(episodeId);
        return bloodLinkRepository.findByEpisodeIdOrderByCreatedAtAsc(episodeId).stream().map(this::bloodLinkRow).toList();
    }

    // ── 2. Transport: patient movement + specimen legs (NHUME) ────────────────────────────────────
    @Transactional
    public Map<String, Object> requestPatientMovement(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String leg = normaliseLeg(ClinicalPayloadMapper.str(body, "leg"));
        if (!List.of("WARD_TO_THEATRE", "THEATRE_TO_PACU", "THEATRE_TO_WARD").contains(leg)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "leg must be WARD_TO_THEATRE | THEATRE_TO_PACU | THEATRE_TO_WARD");
        }
        return createTransportLeg(e, "PATIENT_MOVEMENT", leg, "PATIENT", e.getSubjectCpid(), null);
    }

    @Transactional
    public Map<String, Object> requestSpecimenTransport(UUID episodeId, String specimenRef, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        return createTransportLeg(e, "SPECIMEN", "THEATRE_TO_LAB", "LAB_SAMPLE_PICKUP", specimenRef, specimenRef);
    }

    /** Theatre-governed PACU entry: advance the FSM then auto-request the THEATRE_TO_PACU movement. */
    @Transactional
    public Map<String, Object> enterPacu(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        boolean wasInProgress = "IN_PROGRESS".equals(e.getStatus());
        Map<String, Object> result = episodeService.enterPacu(episodeId, body);
        if (wasInProgress) {
            // Auto-request THEATRE_TO_PACU on IN_PROGRESS → PACU (no silent hand-off).
            createTransportLeg(e, "PATIENT_MOVEMENT", "THEATRE_TO_PACU", "PATIENT", e.getSubjectCpid(), null);
        }
        return result;
    }

    private Map<String, Object> createTransportLeg(ProcedureEpisodeEntity e, String kind, String leg,
                                                   String deliveryType, String subjectRef, String specimenRef) {
        UUID episodeId = e.getEpisodeId();
        int seq = transportRepository.countByEpisodeIdAndLeg(episodeId, leg) + 1;
        ProcedureTransportEntity t = new ProcedureTransportEntity();
        t.setTenantId(tenant());
        t.setEpisodeId(episodeId);
        t.setKind(kind);
        t.setLeg(leg);
        t.setSeq(seq);
        t.setNhumeDeliveryType(deliveryType);
        t.setOrosSpecimenRef(specimenRef);
        t.setCreatedBy(actor());
        String idem = idempotency(episodeId, "transport-" + leg + "-" + seq);
        t.setIdempotencyKey(idem);
        NhumeTransportClient.DeliveryView delivery = nhumeTransportClient.createDelivery(
                deliveryType, leg, subjectRef, episodeId.toString(), facilityRef(), idem);
        t.setNhumeDeliveryId(delivery.deliveryId());
        t.setStatus(delivery.deliveryId() != null ? mapNhumeStatus(delivery.status()) : "UNAVAILABLE");
        t.setSlaDueAt(delivery.slaDueAt());
        transportRepository.save(t);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.transport.requested", Map.of(
                "episode_id", episodeId.toString(), "leg", leg, "kind", kind,
                "nhume_delivery_id", nullSafe(delivery.deliveryId()), "status", t.getStatus()));
        return transportRow(t);
    }

    public List<Map<String, Object>> listTransport(UUID episodeId) {
        requireEpisode(episodeId);
        return transportRepository.findByEpisodeIdOrderByCreatedAtAsc(episodeId).stream().map(this::transportRow).toList();
    }

    // ── 3. Specimens: parse note → OROS order → NHUME dispatch → result → critical → acknowledge ──
    /** Parse the operative-note specimens and open the OROS specimen orders + transport legs. */
    @Transactional
    public void processSpecimensFromNote(ProcedureEpisodeEntity e, ProcedureNoteEntity note) {
        String raw = note.getSpecimens();
        if (raw == null || raw.isBlank()) return;
        String encounterRef = e.getEncounterId() != null ? e.getEncounterId().toString() : null;
        for (String label : raw.split("[;,\\n]")) {
            String specimenLabel = label.trim();
            if (specimenLabel.isEmpty()) continue;
            int seq = specimenRepository.countByEpisodeId(e.getEpisodeId()) + 1;
            ProcedureSpecimenEntity s = new ProcedureSpecimenEntity();
            s.setTenantId(tenant());
            s.setEpisodeId(e.getEpisodeId());
            s.setSeq(seq);
            s.setSpecimenLabel(specimenLabel);
            s.setSpecimenType("HISTOPATH");
            s.setBodySite(specimenLabel);
            s.setCreatedBy(actor());
            String idem = idempotency(e.getEpisodeId(), "specimen-" + seq);
            s.setIdempotencyKey(idem);
            String orosOrderId = orosSpecimenClient.placeLabOrder(e.getSubjectCpid(), encounterRef,
                    specimenLabel, "HISTOPATH", specimenLabel, idem);
            s.setOrosOrderId(orosOrderId);
            if (orosOrderId != null) {
                OrosSpecimenClient.SpecimenView sv = orosSpecimenClient.collect(orosOrderId, "HISTOPATH", specimenLabel, idem);
                s.setOrosSpecimenId(sv.specimenId());
                s.setStatus(sv.specimenId() != null ? "COLLECTED" : "ORDERED");
            } else {
                s.setStatus("PARSED");
            }
            specimenRepository.save(s);
            // NHUME leg to lab, linked back to the OROS specimen ref.
            if (s.getOrosSpecimenId() != null) {
                Map<String, Object> leg = createTransportLeg(e, "SPECIMEN", "THEATRE_TO_LAB",
                        "LAB_SAMPLE_PICKUP", s.getOrosSpecimenId(), s.getOrosSpecimenId());
                Object tid = leg.get("id");
                if (tid != null) s.setTransportId(UUID.fromString(tid.toString()));
                orosSpecimenClient.dispatch(s.getOrosSpecimenId(), idem);
                s.setStatus("DISPATCHED");
                specimenRepository.save(s);
            }
            appendOutbox("PROCEDURE", e.getEpisodeId().toString(), "theatre.specimen.ordered", Map.of(
                    "episode_id", e.getEpisodeId().toString(), "specimen_label", specimenLabel,
                    "oros_order_id", nullSafe(orosOrderId), "status", s.getStatus()));
        }
    }

    @Transactional
    public Map<String, Object> acknowledgeCritical(UUID episodeId, UUID specimenId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        ProcedureSpecimenEntity s = specimenRepository.findById(specimenId)
                .filter(x -> x.getEpisodeId().equals(episodeId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Specimen not found for episode"));
        s.setAcknowledgedAt(OffsetDateTime.now());
        s.setAcknowledgedBy(actor());
        s.setStatus("ACKNOWLEDGED");
        // Attach the pathology to the clinical record (Butano owns the record).
        String docRef = butanoClient.attachPathology(e.getSubjectCpid(),
                e.getEncounterId() != null ? e.getEncounterId().toString() : null,
                s.getSpecimenLabel(), s.getOrosResultId(),
                ClinicalPayloadMapper.str(body, "summary"));
        if (docRef != null) s.setButanoDocumentRef(docRef);
        specimenRepository.save(s);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.specimen.acknowledged", Map.of(
                "episode_id", episodeId.toString(), "specimen_id", specimenId.toString(),
                "butano_document_ref", nullSafe(s.getButanoDocumentRef())));
        return specimenRow(s);
    }

    public List<Map<String, Object>> listSpecimens(UUID episodeId) {
        requireEpisode(episodeId);
        return specimenRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream().map(this::specimenRow).toList();
    }

    // ── 4. Surgical counts (swab/instrument/needle) + RITO RETAINED_ITEM sentinel on discrepancy ──
    @Transactional
    public Map<String, Object> recordCount(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String kind = Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "kind", "countType", "count_type"), "")
                .trim().toUpperCase();
        if (!List.of("SWAB", "INSTRUMENT", "NEEDLE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be SWAB | INSTRUMENT | NEEDLE");
        }
        Integer baseline = ClinicalPayloadMapper.integer(body, "baselineCount", "baseline_count");
        Integer closing = ClinicalPayloadMapper.integer(body, "closingCount", "closing_count");
        int seq = countRepository.countByEpisodeIdAndKind(episodeId, kind) + 1;
        ProcedureCountEntity c = countRepository
                .findByTenantIdAndEpisodeIdAndKindAndSeq(tenant(), episodeId, kind, seq)
                .orElseGet(ProcedureCountEntity::new);
        c.setTenantId(tenant());
        c.setEpisodeId(episodeId);
        c.setKind(kind);
        c.setSeq(seq);
        if (baseline != null) c.setBaselineCount(baseline);
        if (closing != null) c.setClosingCount(closing);
        c.setRecordedBy(actor());
        c.setIdempotencyKey(idempotency(episodeId, "count-" + kind + "-" + seq));
        boolean bothSides = c.getBaselineCount() != null && c.getClosingCount() != null;
        int discrepancy = bothSides ? c.getBaselineCount() - c.getClosingCount() : 0;
        c.setDiscrepancy(bothSides ? discrepancy : null);
        c.setReconciled(bothSides && discrepancy == 0);
        countRepository.save(c);

        String event = "theatre.count.recorded";
        if (bothSides && discrepancy != 0) {
            // RETAINED-ITEM RISK — raise a RITO sentinel incident (RITO owns the safety case).
            // RITO case severity must be one of RITO's LOW|MODERATE|HIGH|CRITICAL — a retained item is
            // CRITICAL (previously sent the invalid "SEVERE", which RITO 400-rejected so no case opened).
            // Each RITO call carries a DISTINCT idempotency key: reusing one key made the 2nd/3rd call
            // 409 on RITO's idempotency filter (same key, different body).
            String idemBase = idempotency(episodeId, "rito-count-" + kind + "-" + seq);
            String caseId = ritoSafetyClient.createCase("SAFETY_INCIDENT",
                    "Surgical count discrepancy (" + kind + ")",
                    "Theatre count discrepancy of " + discrepancy + " " + kind + "(s) at Sign-Out",
                    "CRITICAL", "PATIENT_SAFETY", e.getSubjectCpid(),
                    Map.of("episode_id", episodeId.toString(), "count_kind", kind, "discrepancy", discrepancy),
                    idemBase + ":case");
            if (caseId != null) {
                c.setRitoCaseRef(caseId);
                countRepository.save(c);
                ritoSafetyClient.recordSafetyIncident(caseId, "RETAINED_ITEM", "CRITICAL",
                        "Reconcile count and perform imaging per retained-item protocol", true, idemBase + ":incident");
            }
            ritoSafetyClient.ingestQualitySignal("CHECKLIST_NONCOMPLIANCE",
                    "episode:" + episodeId, "HIGH", facilityRef(),
                    "Surgical " + kind + " count discrepancy of " + discrepancy, null, idemBase + ":signal");
            // Route through the existing procedure_safety_event ownerFor path (RETAINED_ITEM → rito).
            routeSafetyEventInternal(episodeId, "RETAINED_ITEM", "SENTINEL",
                    kind + " count discrepancy of " + discrepancy, caseId);
            event = "theatre.count.discrepancy";
        }
        appendOutbox(event.equals("theatre.count.discrepancy") ? "SAFETY" : "PROCEDURE",
                episodeId.toString(), event, Map.of(
                        "episode_id", episodeId.toString(), "kind", kind,
                        "discrepancy", c.getDiscrepancy() != null ? c.getDiscrepancy() : 0,
                        "reconciled", c.isReconciled(), "rito_case_ref", nullSafe(c.getRitoCaseRef())));
        return countRow(c);
    }

    public List<Map<String, Object>> listCounts(UUID episodeId) {
        requireEpisode(episodeId);
        return countRepository.findByEpisodeIdOrderByKindAscSeqAsc(episodeId).stream().map(this::countRow).toList();
    }

    // ── Reconciler read-through hooks (called by TheatreProjectionReconciler; idempotent) ─────────
    @Transactional
    public void reconcileBloodLink(ProcedureBloodLinkEntity link) {
        if (link.getMadiOrderId() == null) return;
        MadiBloodClient.BloodOrderView view = madiBloodClient.getOrderDetail(link.getMadiOrderId());
        if (!view.available()) return;
        link.setReservationStatus(view.reservationStatus());
        link.setCrossmatchStatus(view.crossmatchStatus());
        if (view.reserved() && List.of("REQUESTED", "GROUP_SCREEN").contains(link.getStatus())) {
            link.setStatus("RESERVED");
        }
        bloodLinkRepository.save(link);
    }

    @Transactional
    public void reconcileTransport(ProcedureTransportEntity t) {
        if (t.getNhumeDeliveryId() != null) {
            NhumeTransportClient.DeliveryView view = nhumeTransportClient.getDelivery(t.getNhumeDeliveryId());
            if (view.available() && view.status() != null) {
                t.setStatus(mapNhumeStatus(view.status()));
                if (view.slaDueAt() != null) t.setSlaDueAt(view.slaDueAt());
            }
        }
        // Overdue detection (SLA breach) → SAFETY signal, once.
        if (!t.isOverdue() && t.getSlaDueAt() != null && OffsetDateTime.now().isAfter(t.getSlaDueAt())
                && !List.of("DELIVERED", "FAILED", "CANCELLED").contains(t.getStatus())) {
            t.setOverdue(true);
            t.setStatus("OVERDUE");
            appendOutbox("SAFETY", t.getEpisodeId().toString(), "theatre.transport.overdue", Map.of(
                    "episode_id", t.getEpisodeId().toString(), "leg", t.getLeg(),
                    "nhume_delivery_id", nullSafe(t.getNhumeDeliveryId()),
                    "sla_due_at", t.getSlaDueAt().toString()));
        }
        transportRepository.save(t);
    }

    @Transactional
    public void reconcileSpecimen(ProcedureSpecimenEntity s) {
        if (s.getOrosOrderId() == null) return;
        List<OrosSpecimenClient.ResultView> results = orosSpecimenClient.getResults(s.getOrosOrderId());
        if (results.isEmpty()) return;
        OrosSpecimenClient.ResultView r = results.get(results.size() - 1);
        s.setOrosResultId(r.resultId());
        boolean wasCritical = s.isCritical();
        if (r.critical()) {
            s.setCritical(true);
            if (!"CRITICAL".equals(s.getStatus()) && !"ACKNOWLEDGED".equals(s.getStatus())) {
                s.setStatus("CRITICAL");
            }
        } else if (!"ACKNOWLEDGED".equals(s.getStatus())) {
            s.setStatus("RESULTED");
        }
        specimenRepository.save(s);
        if (r.critical() && !wasCritical) {
            // Mirror OROS critical escalation to a SAFETY outbox (once).
            appendOutbox("SAFETY", s.getEpisodeId().toString(), "theatre.specimen.critical", Map.of(
                    "episode_id", s.getEpisodeId().toString(), "specimen_id", s.getSpecimenId().toString(),
                    "oros_result_id", nullSafe(r.resultId()), "specimen_label", nullSafe(s.getSpecimenLabel())));
        }
    }

    public List<ProcedureBloodLinkEntity> openBloodLinks() {
        return bloodLinkRepository.findByTenantIdAndStatusNotIn(tenant(), BLOOD_TERMINAL);
    }
    public List<ProcedureTransportEntity> openTransportLegs() {
        return transportRepository.findByTenantIdAndStatusNotIn(tenant(), List.of("DELIVERED", "FAILED", "CANCELLED"));
    }
    public List<ProcedureSpecimenEntity> pendingSpecimens() {
        return specimenRepository.findByTenantIdAndStatusNotIn(tenant(), List.of("RESULTED", "ACKNOWLEDGED", "REJECTED"));
    }

    // ── Wave 1 helpers ────────────────────────────────────────────────────────────────────────────
    private ProcedureBloodLinkEntity newBloodLink(UUID episodeId) {
        ProcedureBloodLinkEntity link = new ProcedureBloodLinkEntity();
        link.setTenantId(tenant());
        link.setEpisodeId(episodeId);
        link.setKind("BLOOD");
        link.setSeq(1);
        return link;
    }

    private ProcedureBloodLinkEntity requireBloodLink(UUID episodeId) {
        return bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)
                .filter(l -> l.getMadiOrderId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No MADI blood request placed for this episode (call requestBlood first)"));
    }

    private void routeSafetyEventInternal(UUID episodeId, String category, String severity,
                                          String description, String ownerRef) {
        ProcedureSafetyEventEntity ev = new ProcedureSafetyEventEntity();
        ev.setEpisodeId(episodeId);
        ev.setCategory(category);
        ev.setSeverity(severity);
        ev.setDescription(description);
        ev.setRoutedOwner(ownerFor(category));
        ev.setOwnerRef(ownerRef);
        ev.setRoutedStatus(ownerRef != null ? "ROUTED" : "OWNER_UNAVAILABLE");
        ev.setReportedBy(actor());
        safetyRepository.save(ev);
    }

    private String idempotency(UUID episodeId, String op) {
        return "inpatient-theatre:" + episodeId + ":" + op;
    }

    private static String normaliseLeg(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private static String mapNhumeStatus(String nhume) {
        if (nhume == null) return "REQUESTED";
        return switch (nhume.toUpperCase()) {
            case "CREATED", "DRAFT", "SUBMITTED", "PENDING", "APPROVED" -> "REQUESTED";
            case "ASSIGNED", "ACCEPTED" -> "ASSIGNED";
            case "IN_TRANSIT", "PICKED_UP", "STARTED", "EN_ROUTE" -> "IN_TRANSIT";
            case "DELIVERED", "COMPLETED", "ARRIVED" -> "DELIVERED";
            case "FAILED", "RETURNED" -> "FAILED";
            case "CANCELLED" -> "CANCELLED";
            default -> "IN_TRANSIT";
        };
    }

    private Map<String, Object> bloodLinkRow(ProcedureBloodLinkEntity l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getLinkId().toString());
        m.put("episode_id", l.getEpisodeId().toString());
        m.put("status", l.getStatus());
        m.put("required", l.isRequired());
        m.put("madi_order_id", l.getMadiOrderId());
        m.put("madi_transfusion_episode_id", l.getMadiTransfusionEpisodeId());
        m.put("nhume_delivery_id", l.getNhumeDeliveryId());
        m.put("reservation_status", l.getReservationStatus());
        m.put("crossmatch_status", l.getCrossmatchStatus());
        m.put("units_requested", l.getUnitsRequested());
        m.put("blood_group", l.getBloodGroup());
        m.put("component_type", l.getComponentType());
        m.put("updated_at", l.getUpdatedAt());
        return m;
    }

    private Map<String, Object> transportRow(ProcedureTransportEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getTransportId().toString());
        m.put("kind", t.getKind());
        m.put("leg", t.getLeg());
        m.put("seq", t.getSeq());
        m.put("status", t.getStatus());
        m.put("overdue", t.isOverdue());
        m.put("nhume_delivery_id", t.getNhumeDeliveryId());
        m.put("nhume_delivery_type", t.getNhumeDeliveryType());
        m.put("oros_specimen_ref", t.getOrosSpecimenRef());
        m.put("sla_due_at", t.getSlaDueAt());
        m.put("updated_at", t.getUpdatedAt());
        return m;
    }

    private Map<String, Object> specimenRow(ProcedureSpecimenEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getSpecimenId().toString());
        m.put("seq", s.getSeq());
        m.put("specimen_label", s.getSpecimenLabel());
        m.put("specimen_type", s.getSpecimenType());
        m.put("body_site", s.getBodySite());
        m.put("status", s.getStatus());
        m.put("is_critical", s.isCritical());
        m.put("oros_order_id", s.getOrosOrderId());
        m.put("oros_specimen_id", s.getOrosSpecimenId());
        m.put("oros_result_id", s.getOrosResultId());
        m.put("transport_id", s.getTransportId() != null ? s.getTransportId().toString() : null);
        m.put("acknowledged_at", s.getAcknowledgedAt());
        m.put("acknowledged_by", s.getAcknowledgedBy());
        m.put("butano_document_ref", s.getButanoDocumentRef());
        return m;
    }

    private Map<String, Object> countRow(ProcedureCountEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getCountId().toString());
        m.put("kind", c.getKind());
        m.put("seq", c.getSeq());
        m.put("baseline_count", c.getBaselineCount());
        m.put("closing_count", c.getClosingCount());
        m.put("discrepancy", c.getDiscrepancy());
        m.put("reconciled", c.isReconciled());
        m.put("override", c.isOverride());
        m.put("override_reason", c.getOverrideReason());
        m.put("rito_case_ref", c.getRitoCaseRef());
        m.put("recorded_by", c.getRecordedBy());
        return m;
    }

    /** 409 carrier for booking blockers (owner-specific), so the controller returns the blocker list. */
    public static class BookingBlockedException extends RuntimeException {
        private final transient Map<String, Object> detail;
        public BookingBlockedException(Map<String, Object> detail) { this.detail = detail; }
        public Map<String, Object> detail() { return detail; }
    }
}
