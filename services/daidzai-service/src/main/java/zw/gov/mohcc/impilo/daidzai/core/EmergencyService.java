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

    private final EmergencyRequestRepository requestRepo;
    private final EmergencyIncidentRepository incidentRepo;
    private final MissionEventRepository missionRepo;
    private final ResourceRequestRepository resourceRepo;
    private final TriageClassifier triage;
    private final ReferenceGenerator refs;
    private final OwnerRoutedGateway gateway;
    private final DaidzaiEventEmitter emitter;

    public EmergencyService(EmergencyRequestRepository requestRepo, EmergencyIncidentRepository incidentRepo,
                            MissionEventRepository missionRepo, ResourceRequestRepository resourceRepo,
                            TriageClassifier triage, ReferenceGenerator refs,
                            OwnerRoutedGateway gateway, DaidzaiEventEmitter emitter) {
        this.requestRepo = requestRepo;
        this.incidentRepo = incidentRepo;
        this.missionRepo = missionRepo;
        this.resourceRepo = resourceRepo;
        this.triage = triage;
        this.refs = refs;
        this.gateway = gateway;
        this.emitter = emitter;
    }

    // ---- 1. SOS request intake (self / caregiver / provider / facility / bystander) ----

    @Transactional
    public EmergencyRequestEntity createRequest(UUID tenantId, String requesterType, String requesterActorId,
                                                String subjectIdentityMode, String subjectHealthId,
                                                String subjectTempRef, String subjectLabel,
                                                String category, String reportedSeverity, String description,
                                                Double lat, Double lng, String locationDescription,
                                                String attachmentsRef, String channel) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("emergencyCategory is required");
        }
        String mode = subjectIdentityMode != null ? subjectIdentityMode.toUpperCase() : "UNKNOWN";
        EmergencyRequestEntity r = new EmergencyRequestEntity();
        r.setTenantId(tenantId);
        r.setRequestReference(refs.requestReference());
        r.setRequesterType(requesterType != null ? requesterType.toUpperCase() : "CITIZEN");
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
        r.setStatus("RECEIVED");
        requestRepo.save(r);

        emitter.emit("EMERGENCY_REQUEST", r.getId().toString(), "daidzai.request.received",
                "EMERGENCY_REQUEST", r.getId().toString(),
                Map.of("category", r.getEmergencyCategory(), "channel", r.getChannel(),
                        "requesterType", r.getRequesterType(), "sensitive", r.getSensitive()), tenantId);

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

    // ---- 2 + 3. Triage a request into an incident (severity classification + dispatch need) ----

    @Transactional
    public EmergencyIncidentEntity triageRequestToIncident(UUID tenantId, UUID requestId, String actorId) {
        EmergencyRequestEntity r = getRequest(tenantId, requestId);
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
        return inc;
    }
}
