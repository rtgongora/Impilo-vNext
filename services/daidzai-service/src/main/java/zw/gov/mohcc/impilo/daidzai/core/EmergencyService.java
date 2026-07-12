package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.*;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Emergency spine. Owns the SOS request -> incident -> triage -> dispatch-need -> mission-tracking
 * -> clinical-handoff -> resource-request -> close lifecycle. Every state change emits an audited
 * outbox event. Owner systems (Nhume/Ndila/PCT/Khuluma) are reached via {@link OwnerRoutedGateway}
 * and referenced by id — never duplicated here. No payment ever gates an emergency.
 */
@Service
public class EmergencyService {

    /** Requester type for a guest emergency request captured through the public gateway lane. */
    public static final String REQUESTER_PUBLIC_ANONYMOUS = "PUBLIC_ANONYMOUS";
    /** Lifecycle state of a public-anonymous request held pending dispatcher callback (PD-3). */
    public static final String STATUS_AWAITING_CALLBACK = "AWAITING_CALLBACK";

    private final EmergencyRequestRepository requestRepo;
    private final EmergencyIncidentRepository incidentRepo;
    private final MissionEventRepository missionRepo;
    private final ResourceRequestRepository resourceRepo;
    private final TriageClassifier triage;
    private final ReferenceGenerator refs;
    private final OwnerRoutedGateway gateway;
    private final DaidzaiEventEmitter emitter;
    private final zw.gov.mohcc.impilo.daidzai.integration.NdilaCatchmentClient catchment;

    public EmergencyService(EmergencyRequestRepository requestRepo, EmergencyIncidentRepository incidentRepo,
                            MissionEventRepository missionRepo, ResourceRequestRepository resourceRepo,
                            TriageClassifier triage, ReferenceGenerator refs,
                            OwnerRoutedGateway gateway, DaidzaiEventEmitter emitter,
                            zw.gov.mohcc.impilo.daidzai.integration.NdilaCatchmentClient catchment) {
        this.requestRepo = requestRepo;
        this.incidentRepo = incidentRepo;
        this.missionRepo = missionRepo;
        this.resourceRepo = resourceRepo;
        this.triage = triage;
        this.refs = refs;
        this.gateway = gateway;
        this.emitter = emitter;
        this.catchment = catchment;
    }

    // ---- 1. SOS request intake (self / caregiver / provider / facility / bystander) ----

    @Transactional
    public EmergencyRequestEntity createRequest(UUID tenantId, String requesterType, String requesterActorId,
                                                String subjectIdentityMode, String subjectHealthId,
                                                String subjectTempRef, String subjectLabel,
                                                String category, String reportedSeverity, String description,
                                                Double lat, Double lng, String locationDescription,
                                                String attachmentsRef, String channel) {
        return createRequest(tenantId, requesterType, requesterActorId, subjectIdentityMode, subjectHealthId,
                subjectTempRef, subjectLabel, category, reportedSeverity, description, lat, lng,
                locationDescription, attachmentsRef, channel, null);
    }

    /**
     * SOS request intake. A {@code PUBLIC_ANONYMOUS} request carrying a callback number is captured
     * immediately (never gated on sign-in) but held in {@link #STATUS_AWAITING_CALLBACK}: dispatch
     * cannot proceed until a dispatcher/responder verifies the callback (PD-3). Every other requester
     * (authenticated citizen, provider, facility, bystander) keeps the {@code RECEIVED} default —
     * existing SOS behaviour is unchanged.
     */
    @Transactional
    public EmergencyRequestEntity createRequest(UUID tenantId, String requesterType, String requesterActorId,
                                                String subjectIdentityMode, String subjectHealthId,
                                                String subjectTempRef, String subjectLabel,
                                                String category, String reportedSeverity, String description,
                                                Double lat, Double lng, String locationDescription,
                                                String attachmentsRef, String channel, String callbackNumber) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("emergencyCategory is required");
        }
        String mode = subjectIdentityMode != null ? subjectIdentityMode.toUpperCase() : "UNKNOWN";
        String type = requesterType != null ? requesterType.toUpperCase() : "CITIZEN";
        String normalizedCallback = callbackNumber != null && !callbackNumber.isBlank()
                ? callbackNumber.trim() : null;
        EmergencyRequestEntity r = new EmergencyRequestEntity();
        r.setTenantId(tenantId);
        r.setRequestReference(refs.requestReference());
        r.setRequesterType(type);
        r.setRequesterActorId(requesterActorId);
        r.setSubjectIdentityMode(mode);
        r.setSubjectHealthId("KNOWN".equals(mode) ? subjectHealthId : null);
        r.setSubjectTempRef(subjectTempRef);
        r.setSubjectLabel(subjectLabel);
        r.setEmergencyCategory(category.toUpperCase());
        r.setSensitive(triage.isSensitiveCategory(category));
        r.setSeverity(reportedSeverity != null ? reportedSeverity.toUpperCase() : "UNKNOWN");
        r.setDescription(description);
        r.setLocationLat(lat);
        r.setLocationLng(lng);
        r.setLocationDescription(locationDescription);
        r.setAttachmentsRef(attachmentsRef);
        r.setChannel(channel != null ? channel.toUpperCase() : "WEB");
        r.setCallbackNumber(normalizedCallback);
        // PD-3: a public-anonymous request with a callback is held for verification; dispatch is gated.
        boolean awaitingCallback = REQUESTER_PUBLIC_ANONYMOUS.equals(type) && normalizedCallback != null;
        r.setStatus(awaitingCallback ? STATUS_AWAITING_CALLBACK : "RECEIVED");
        requestRepo.save(r);

        emitter.emit("EMERGENCY_REQUEST", r.getId().toString(), "daidzai.request.received",
                "EMERGENCY_REQUEST", r.getId().toString(),
                Map.of("category", r.getEmergencyCategory(), "channel", r.getChannel(),
                        "requesterType", r.getRequesterType(), "sensitive", r.getSensitive(),
                        "status", r.getStatus()), tenantId);

        // SOS acknowledgement (owner-routed: Khuluma delivers). Request-only, never faked.
        gateway.requestNotification(tenantId, r.getChannel(), "REQUESTER",
                "daidzai.sos.acknowledged", Map.of("requestReference", r.getRequestReference()));
        return r;
    }

    @Transactional(readOnly = true)
    public EmergencyRequestEntity getRequest(UUID tenantId, UUID id) {
        return requestRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Emergency request not found: " + id));
    }

    /**
     * Dispatcher/responder verifies that they reached the caller on the callback number, releasing
     * the PD-3 dispatch gate. This rides the authenticated daidzai lane (operator action, audited);
     * it moves an {@link #STATUS_AWAITING_CALLBACK} request to {@code RECEIVED} so it can be triaged.
     * Idempotent: verifying an already-verified or already-released request is a no-op that returns
     * the current row.
     */
    @Transactional
    public EmergencyRequestEntity verifyCallback(UUID tenantId, UUID requestId, String actorId) {
        EmergencyRequestEntity r = getRequest(tenantId, requestId);
        if (Boolean.TRUE.equals(r.getCallbackVerified())) {
            return r;
        }
        if (r.getCallbackNumber() == null || r.getCallbackNumber().isBlank()) {
            throw new IllegalArgumentException("request has no callback number to verify");
        }
        r.setCallbackVerified(Boolean.TRUE);
        r.setCallbackVerifiedAt(OffsetDateTime.now());
        r.setCallbackVerifiedBy(actorId);
        if (STATUS_AWAITING_CALLBACK.equals(r.getStatus())) {
            r.setStatus("RECEIVED");
        }
        requestRepo.save(r);

        emitter.emit("EMERGENCY_REQUEST", r.getId().toString(), "daidzai.callback.verified",
                "EMERGENCY_REQUEST", r.getId().toString(),
                Map.of("requestReference", r.getRequestReference(),
                        "verifiedBy", actorId == null ? "" : actorId,
                        "status", r.getStatus()), tenantId);
        return r;
    }

    // ---- 2 + 3. Triage a request into an incident (severity classification + dispatch need) ----

    @Transactional
    public EmergencyIncidentEntity triageRequestToIncident(UUID tenantId, UUID requestId, String actorId) {
        EmergencyRequestEntity r = getRequest(tenantId, requestId);
        // PD-3 dispatch gate: a public-anonymous request captured pending callback cannot become an
        // incident (and therefore cannot dispatch) until a dispatcher/responder has reached the
        // caller and verified the callback number. This is the real gate — not a flag nothing reads.
        if (STATUS_AWAITING_CALLBACK.equals(r.getStatus()) && !Boolean.TRUE.equals(r.getCallbackVerified())) {
            throw new CallbackVerificationRequiredException(
                    "callback verification required before dispatch");
        }
        String triageCat = triage.classify(r.getEmergencyCategory(), r.getSeverity());
        String severity = triage.severityForTriage(triageCat);

        EmergencyIncidentEntity inc = new EmergencyIncidentEntity();
        inc.setTenantId(tenantId);
        inc.setIncidentReference(refs.incidentReference());
        inc.setIncidentType("INDIVIDUAL");
        inc.setEmergencyCategory(r.getEmergencyCategory());
        inc.setTriageCategory(triageCat);
        inc.setSeverity(severity);
        inc.setSensitive(Boolean.TRUE.equals(r.getSensitive()));
        inc.setStatus("TRIAGED");
        inc.setTitle(r.getEmergencyCategory() + " emergency");
        inc.setDescription(r.getDescription());
        inc.setSubjectIdentityMode(r.getSubjectIdentityMode());
        inc.setSubjectHealthId(r.getSubjectHealthId());
        inc.setSubjectTempRef(r.getSubjectTempRef());
        inc.setLocationLat(r.getLocationLat());
        inc.setLocationLng(r.getLocationLng());
        inc.setLocationDescription(r.getLocationDescription());
        incidentRepo.save(inc);

        r.setIncidentId(inc.getId());
        r.setSeverity(severity);
        r.setStatus("LINKED");
        requestRepo.save(r);

        emitter.emit("EMERGENCY_INCIDENT", inc.getId().toString(), "daidzai.incident.triaged",
                "EMERGENCY_INCIDENT", inc.getId().toString(),
                Map.of("triageCategory", triageCat, "severity", severity,
                        "category", inc.getEmergencyCategory(), "fromRequest", requestId.toString()), tenantId);
        return inc;
    }

    @Transactional(readOnly = true)
    public EmergencyIncidentEntity getIncident(UUID tenantId, UUID id) {
        return incidentRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EmergencyIncidentEntity> listIncidents(UUID tenantId, String type, String status) {
        if (type != null) return incidentRepo.findByTenantIdAndIncidentTypeOrderByOpenedAtDesc(tenantId, type.toUpperCase());
        if (status != null) return incidentRepo.findByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, status.toUpperCase());
        return incidentRepo.findByTenantIdOrderByOpenedAtDesc(tenantId);
    }

    // ---- 4. Dispatch need + mission status tracking (Nhume executes; Daidzai tracks) ----

    @Transactional
    public MissionEventEntity requestDispatch(UUID tenantId, UUID incidentId, String note, String actorId) {
        EmergencyIncidentEntity inc = getIncident(tenantId, incidentId);
        inc.setStatus("DISPATCH_REQUESTED");
        incidentRepo.save(inc);

        gateway.requestDispatch(tenantId, incidentId, Map.of(
                "category", inc.getEmergencyCategory(), "severity", inc.getSeverity(),
                "lat", inc.getLocationLat() == null ? "" : inc.getLocationLat(),
                "lng", inc.getLocationLng() == null ? "" : inc.getLocationLng()));

        MissionEventEntity ev = recordMissionEvent(tenantId, incidentId, "DISPATCH_REQUESTED",
                null, note, actorId, "DISPATCHER");
        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.dispatch.requested",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("severity", inc.getSeverity(), "triageCategory",
                        inc.getTriageCategory() == null ? "" : inc.getTriageCategory()), tenantId);
        return ev;
    }

    @Transactional
    public MissionEventEntity recordMissionEvent(UUID tenantId, UUID incidentId, String status,
                                                 String nhumeMissionRef, String note,
                                                 String actorId, String actorType) {
        EmergencyIncidentEntity inc = getIncident(tenantId, incidentId);
        MissionEventEntity ev = new MissionEventEntity();
        ev.setTenantId(tenantId);
        ev.setIncidentId(incidentId);
        ev.setStatus(status.toUpperCase());
        ev.setNhumeMissionRef(nhumeMissionRef);
        ev.setNote(note);
        ev.setActorId(actorId);
        ev.setActorType(actorType);
        ev.setOccurredAt(OffsetDateTime.now());
        missionRepo.save(ev);

        // Advance the incident lifecycle to mirror the latest mission status (tracking only).
        Set<String> incidentStatuses = Set.of("RESPONDING", "ON_SCENE", "TRANSPORTING", "HANDOVER");
        if (incidentStatuses.contains(ev.getStatus())) {
            inc.setStatus(ev.getStatus());
        }
        if (nhumeMissionRef != null) {
            inc.setNhumeMissionRef(nhumeMissionRef);
        }
        incidentRepo.save(inc);

        emitter.emit("MISSION", ev.getId().toString(), "daidzai.mission.status",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("status", ev.getStatus(), "missionRef", nhumeMissionRef == null ? "" : nhumeMissionRef), tenantId);
        return ev;
    }

    @Transactional(readOnly = true)
    public List<MissionEventEntity> missionTimeline(UUID tenantId, UUID incidentId) {
        getIncident(tenantId, incidentId);
        return missionRepo.findByIncidentIdOrderByOccurredAtAsc(incidentId);
    }

    // ---- 5. Clinical handoff to PCT (PCT owns the encounter; we hold the link) ----

    @Transactional
    public EmergencyIncidentEntity recordClinicalHandoff(UUID tenantId, UUID incidentId,
                                                         String pctEncounterRef, String actorId) {
        if (pctEncounterRef == null || pctEncounterRef.isBlank()) {
            throw new IllegalArgumentException("pctEncounterRef is required for a clinical handoff");
        }
        EmergencyIncidentEntity inc = getIncident(tenantId, incidentId);
        inc.setPctEncounterRef(pctEncounterRef);
        inc.setStatus("HANDOVER");
        incidentRepo.save(inc);
        recordMissionEvent(tenantId, incidentId, "HANDOVER", inc.getNhumeMissionRef(),
                "Clinical handoff to PCT encounter " + pctEncounterRef, actorId, "RESPONDER");
        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.handoff.recorded",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("pctEncounterRef", pctEncounterRef), tenantId);
        return inc;
    }

    // ---- 5b. Death outcome → Death & Post-Death Pathway (WS#7 → WS#8 seam) ----

    /**
     * Record that an emergency incident ended in death and route it to the Death &amp; Post-Death
     * Pathway. PCT owns the DeathCase; Daidzai signals the death with the subject identity (health id
     * or temporary reference for an unidentified casualty) and incident provenance. We never duplicate
     * the death record — the death pathway consumes {@code daidzai.death.routed} and opens the case.
     *
     * @param placeContext death context to seed the pathway, e.g. COMMUNITY|IN_TRANSIT|BROUGHT_IN_DEAD
     */
    @Transactional
    public EmergencyIncidentEntity recordDeathOutcome(UUID tenantId, UUID incidentId,
                                                      String placeContext, String actorId) {
        EmergencyIncidentEntity inc = getIncident(tenantId, incidentId);
        inc.setStatus("DECEASED");
        incidentRepo.save(inc);
        recordMissionEvent(tenantId, incidentId, "DECEASED", inc.getNhumeMissionRef(),
                "Emergency outcome: death — routed to the Death & Post-Death Pathway", actorId, "RESPONDER");
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("incidentId", incidentId.toString());
        payload.put("incidentReference", inc.getIncidentReference());
        payload.put("subjectHealthId", inc.getSubjectHealthId());
        payload.put("subjectTempRef", inc.getSubjectTempRef());
        payload.put("facilityId", inc.getFacilityId() != null ? inc.getFacilityId().toString() : null);
        payload.put("placeOfDeathContext", placeContext != null ? placeContext : "IN_TRANSIT");
        payload.put("sourceContext", "EMERGENCY_INCIDENT");
        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.death.routed",
                "EMERGENCY_INCIDENT", incidentId.toString(), payload, tenantId);
        return inc;
    }

    // ---- 6. Resource requests (Daidzai raises the need; owner executes) ----

    @Transactional
    public ResourceRequestEntity requestResource(UUID tenantId, UUID incidentId, String resourceType,
                                                 String resourceOwner, Integer quantity, String detail,
                                                 String requestedBy) {
        getIncident(tenantId, incidentId);
        ResourceRequestEntity rr = new ResourceRequestEntity();
        rr.setTenantId(tenantId);
        rr.setIncidentId(incidentId);
        rr.setResourceType(resourceType != null ? resourceType.toUpperCase() : "OTHER");
        rr.setResourceOwner(resourceOwner != null ? resourceOwner.toUpperCase() : "NHUME");
        rr.setQuantity(quantity != null ? quantity : 1);
        rr.setDetail(detail);
        rr.setRequestedBy(requestedBy);
        rr.setStatus("REQUESTED");
        resourceRepo.save(rr);
        emitter.emit("RESOURCE_REQUEST", rr.getId().toString(), "daidzai.resource.requested",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("resourceType", rr.getResourceType(), "owner", rr.getResourceOwner(),
                        "quantity", rr.getQuantity()), tenantId);
        return rr;
    }

    @Transactional(readOnly = true)
    public List<ResourceRequestEntity> resources(UUID tenantId, UUID incidentId) {
        getIncident(tenantId, incidentId);
        return resourceRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    // ---- Provider/facility-triggered escalation: create an incident directly ----

    @Transactional
    public EmergencyIncidentEntity escalateToIncident(UUID tenantId, String category, String reportedSeverity,
                                                      String description, UUID facilityId,
                                                      String subjectIdentityMode, String subjectHealthId,
                                                      Double lat, Double lng, String locationDescription,
                                                      String actorId) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("emergencyCategory is required");
        }
        String triageCat = triage.classify(category, reportedSeverity);
        EmergencyIncidentEntity inc = new EmergencyIncidentEntity();
        inc.setTenantId(tenantId);
        inc.setIncidentReference(refs.incidentReference());
        inc.setIncidentType("INDIVIDUAL");
        inc.setEmergencyCategory(category.toUpperCase());
        inc.setTriageCategory(triageCat);
        inc.setSeverity(triage.severityForTriage(triageCat));
        inc.setSensitive(triage.isSensitiveCategory(category));
        inc.setStatus("TRIAGED");
        inc.setTitle(category.toUpperCase() + " escalation");
        inc.setDescription(description);
        inc.setFacilityId(facilityId);
        inc.setSubjectIdentityMode(subjectIdentityMode != null ? subjectIdentityMode.toUpperCase() : "UNKNOWN");
        inc.setSubjectHealthId(subjectHealthId);
        inc.setLocationLat(lat);
        inc.setLocationLng(lng);
        inc.setLocationDescription(locationDescription);
        incidentRepo.save(inc);
        emitter.emit("EMERGENCY_INCIDENT", inc.getId().toString(), "daidzai.incident.escalated",
                "EMERGENCY_INCIDENT", inc.getId().toString(),
                Map.of("triageCategory", triageCat, "category", inc.getEmergencyCategory(),
                        "facilityId", facilityId == null ? "" : facilityId.toString()), tenantId);
        alertCatchmentFacilities(tenantId, inc, lat, lng);
        return inc;
    }

    /**
     * Facilities are surveillance nodes: an incident in their vicinity must reach
     * them. Resolves the nearest registered facilities via Ndila and emits a
     * catchment alert per facility (consumable by notification/control-tower).
     * Degraded location service never blocks the incident — it simply carries
     * no catchment set, which the events stream makes visible.
     */
    private void alertCatchmentFacilities(UUID tenantId, EmergencyIncidentEntity inc, Double lat, Double lng) {
        if (lat == null || lng == null) {
            return;
        }
        try {
            var facilities = catchment.nearestFacilities(tenantId, lat, lng, 3);
            for (var facility : facilities) {
                emitter.emit("EMERGENCY_INCIDENT", inc.getId().toString(),
                        "daidzai.facility.catchment_alert",
                        "FACILITY_LOCATION", facility.locationId(),
                        Map.of(
                                "incidentId", inc.getId().toString(),
                                "incidentReference", inc.getIncidentReference(),
                                "triageCategory", inc.getTriageCategory(),
                                "category", inc.getEmergencyCategory(),
                                "facilityName", facility.name() == null ? "" : facility.name(),
                                "distanceMeters", String.valueOf(Math.round(facility.distanceMeters()))),
                        tenantId);
            }
        } catch (Exception e) {
            // Never let catchment alerting break emergency intake.
        }
    }
}
