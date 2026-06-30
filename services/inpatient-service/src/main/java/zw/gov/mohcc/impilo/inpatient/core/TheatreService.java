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
import zw.gov.mohcc.impilo.inpatient.integration.OrosOrderClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreDeathClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreReadinessClient;
import zw.gov.mohcc.impilo.inpatient.integration.TheatreReadinessClient.ReadinessResult;
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
        ReadinessResult surgeon = readinessClient.checkProviderScope(e.getSurgeonId(), "SURGEON");
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

        // BLOOD — readiness routed through OROS BLOOD_BANK → Madi (only when requested).
        boolean bloodRequired = Boolean.TRUE.equals(body.get("bloodRequired"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("blood_required", "false")));
        if (bloodRequired) {
            String bloodOrderId = orosOrderClient.placeOrder("BLOOD_BANK", "URGENT", e.getSubjectCpid(),
                    nullSafe(e.getEncounterId() != null ? e.getEncounterId().toString() : null),
                    "Theatre blood readiness", List.of(buildOrderItem("Crossmatch units", "BLOOD")));
            if (bloodOrderId != null) {
                results.add(record(episodeId, "BLOOD", "madi", "READY", "oros-blood:" + bloodOrderId,
                        List.of(), Map.of("oros_blood_order_id", bloodOrderId,
                                "note", "Blood request placed via OROS BLOOD_BANK → Madi")));
            } else {
                results.add(record(episodeId, "BLOOD", "madi", "UNAVAILABLE", null,
                        List.of(Map.of("code", "BLOOD_ROUTE_UNAVAILABLE",
                                "message", "Could not place OROS BLOOD_BANK order to Madi")), Map.of()));
                allBlockers.add(Map.of("code", "BLOOD_ROUTE_UNAVAILABLE",
                        "message", "Blood readiness could not be confirmed"));
            }
        }

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
        ReadinessResult scope = readinessClient.checkProviderScope(providerId, "SURGEON");
        if ("BLOCKED".equals(scope.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Signer " + providerId + " lacks an active SURGEON scope to sign the operative note");
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

    /** 409 carrier for booking blockers (owner-specific), so the controller returns the blocker list. */
    public static class BookingBlockedException extends RuntimeException {
        private final transient Map<String, Object> detail;
        public BookingBlockedException(Map<String, Object> detail) { this.detail = detail; }
        public Map<String, Object> detail() { return detail; }
    }
}
