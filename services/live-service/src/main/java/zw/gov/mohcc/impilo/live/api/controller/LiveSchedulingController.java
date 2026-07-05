package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.LiveEventService;
import zw.gov.mohcc.impilo.live.core.LiveEventService.RoleAssignment;
import zw.gov.mohcc.impilo.live.domain.LiveMode;
import zw.gov.mohcc.impilo.live.domain.OwningService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Typed Impilo Live scheduling endpoints used by owning services (doctrine §7).
 *
 * <p>Each endpoint constructs a governed {@code LiveEvent} for its mode and delegates enforcement to
 * {@code LiveGovernanceGuard}. The owning service (Telemedicine / Fundo / Public Health) remains
 * responsible for the end-to-end workflow; Impilo Live only owns the live engagement capability.
 */
@RestController
@RequestMapping("/internal/v1/live")
public class LiveSchedulingController {

    private final LiveEventService eventService;

    public LiveSchedulingController(LiveEventService eventService) {
        this.eventService = eventService;
    }

    /** Telemedicine / PCT → clinical live session. No free-floating clinical rooms. */
    @PostMapping("/clinical-sessions")
    public ResponseEntity<LiveDtos.ScheduledEventResponse> scheduleClinicalLiveSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LiveDtos.ScheduleClinicalSessionRequest req) {

        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(tenantId);
        e.setMode(LiveMode.CLINICAL_SESSION.name());
        e.setOwningService(OwningService.TELEMEDICINE.name());
        e.setOwningEntityId(req.clinicalContextRef());
        e.setClinicalContextRef(req.clinicalContextRef());
        e.setClientId(req.clientId());
        e.setProviderId(req.providerId());
        e.setFacilityId(req.facilityId());
        e.setLinkedServiceId(req.clinicalContextRef());
        e.setTitle(req.title() != null ? req.title() : "Clinical session");
        e.setDescription(req.description());
        e.setEventType("CLINICAL_SESSION");
        e.setAudienceType("CLINICAL");
        e.setVisibility(req.visibility() != null ? req.visibility() : "RESTRICTED");
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setPrivacyLevel(req.privacyLevel() != null ? req.privacyLevel() : "PATIENT_IDENTIFIABLE");
        e.setConsentPolicy("REQUIRED");
        e.setConsentRequired(true);
        e.setRegistrationRequired(false);
        e.setEmergencyFlag(Boolean.TRUE.equals(req.emergencyFlag()));
        // Patient-identifiable recordings disabled by default unless explicitly permitted.
        boolean recordingPermitted = "ALLOWED".equalsIgnoreCase(req.recordingPolicy())
                || "POLICY_PERMITTED".equalsIgnoreCase(req.recordingPolicy());
        e.setRecordingPolicy(recordingPermitted ? "ALLOWED" : "DISABLED");
        e.setRecordingAllowed(recordingPermitted);
        e.setReplayPolicy("DISABLED");
        e.setReplayAllowed(false);
        e.setCreatedBy(actorId != null ? actorId : req.providerId());

        List<RoleAssignment> roles = new ArrayList<>();
        roles.add(new RoleAssignment(req.providerId(), "PROVIDER", "HOST"));
        roles.add(new RoleAssignment(req.clientId(), "CLIENT", "ATTENDEE"));
        addAll(roles, req.additionalProviderIds(), "PROVIDER", "PRESENTER");
        addAll(roles, req.interpreterIds(), "PROVIDER", "INTERPRETER");

        LiveEventEntity saved = eventService.createAndSchedule(e, roles, e.getCreatedBy(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Fundo → course-linked webinar / CPD session. */
    @PostMapping("/fundo-webinars")
    public ResponseEntity<LiveDtos.ScheduledEventResponse> scheduleFundoWebinar(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LiveDtos.ScheduleFundoWebinarRequest req) {

        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(tenantId);
        e.setMode(LiveMode.WEBINAR_CPD.name());
        e.setOwningService(OwningService.FUNDO.name());
        e.setOwningEntityId(req.courseId() != null ? req.courseId().toString() : req.learningActivityId());
        e.setLinkedFundoCourseId(req.courseId());
        e.setTitle(req.title() != null ? req.title() : "Fundo webinar");
        e.setDescription(req.description());
        e.setEventType("WEBINAR_CPD");
        e.setAudienceType(req.learnerAudience() != null ? req.learnerAudience() : "ROLE_BASED");
        e.setVisibility(req.visibility() != null ? req.visibility() : "ROLE_BASED");
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setRegistrationRequired(true);
        if (req.cpdEnabled() != null) e.setCpdEnabled(req.cpdEnabled());
        e.setCpdPoints(req.cpdPoints());
        e.setAttendanceThresholdMinutes(req.attendanceThresholdMinutes() != null
                ? req.attendanceThresholdMinutes() : 30);
        boolean replay = req.replayAllowed() == null || req.replayAllowed();
        e.setReplayPolicy(replay ? "ALLOWED" : "DISABLED");
        e.setReplayAllowed(replay);
        e.setRecordingPolicy(replay ? "ALLOWED" : "DISABLED");
        e.setRecordingAllowed(replay);
        e.setCreatedBy(actorId != null ? actorId : "fundo");
        // organiser_id is NOT NULL; service-to-service scheduling (Fundo) may
        // carry no actor — the lead trainer organises, else the caller, else
        // the owning service itself.
        String organiser = req.trainerIds() != null && !req.trainerIds().isEmpty()
                ? req.trainerIds().get(0)
                : (actorId != null ? actorId : "fundo");
        e.setOrganiserId(organiser);
        if (e.getOrganiserType() == null || e.getOrganiserType().isBlank()) {
            e.setOrganiserType(req.trainerIds() != null && !req.trainerIds().isEmpty()
                    ? "PROVIDER" : "SERVICE");
        }

        List<RoleAssignment> roles = new ArrayList<>();
        addAll(roles, req.trainerIds(), "PROVIDER", "PRESENTER");

        LiveEventEntity saved = eventService.createAndSchedule(e, roles, e.getCreatedBy(), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Public Health / Citizen Engagement → public broadcast or emergency briefing. */
    @PostMapping("/public-broadcasts")
    public ResponseEntity<LiveDtos.ScheduledEventResponse> schedulePublicBroadcast(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LiveDtos.SchedulePublicBroadcastRequest req) {

        boolean emergency = Boolean.TRUE.equals(req.emergencyBriefing());
        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(tenantId);
        e.setMode((emergency ? LiveMode.EMERGENCY_BRIEFING : LiveMode.PUBLIC_BROADCAST).name());
        e.setOwningService(OwningService.PUBLIC_HEALTH.name());
        String owningEntity = req.campaignId() != null ? req.campaignId() : req.publicHealthEventId();
        e.setOwningEntityId(owningEntity);
        e.setLinkedCampaignId(req.campaignId());
        e.setTitle(req.title() != null ? req.title() : "Public health broadcast");
        e.setDescription(req.description());
        e.setEventType(emergency ? "EMERGENCY_BRIEFING" : "PUBLIC_BROADCAST");
        e.setAudienceType(req.audienceScope() != null ? req.audienceScope() : "PUBLIC");
        e.setVisibility("PUBLIC");
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setLanguage(req.language() != null ? req.language() : "en");
        e.setRegistrationRequired(false);
        e.setModerationPolicy(req.commentModerationPolicy() != null
                ? req.commentModerationPolicy() : "MODERATED");
        e.setSpeakerVerificationRequired(true);
        e.setEmergencyFlag(emergency);
        boolean replay = req.replayAllowed() == null || req.replayAllowed();
        e.setReplayPolicy(replay ? "ALLOWED" : "DISABLED");
        e.setReplayAllowed(replay);
        e.setRecordingPolicy(replay ? "ALLOWED" : "DISABLED");
        e.setRecordingAllowed(replay);
        e.setCreatedBy(actorId != null ? actorId : "public-health");

        List<RoleAssignment> roles = new ArrayList<>();
        addAll(roles, req.officialSpeakerIds(), "PROVIDER", "PRESENTER");
        addAll(roles, req.moderatorIds(), "PROVIDER", "MODERATOR");

        LiveEventEntity saved = eventService.createAndSchedule(e, roles, e.getCreatedBy(), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    private void addAll(List<RoleAssignment> roles, List<String> ids, String participantType, String role) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                roles.add(new RoleAssignment(id, participantType, role));
            }
        }
    }

    private LiveDtos.ScheduledEventResponse toResponse(LiveEventEntity e) {
        return new LiveDtos.ScheduledEventResponse(
                e.getId(), e.getMode(), e.getOwningService(), e.getOwningEntityId(),
                e.getStatus(), e.getVisibility(), e.getRecordingPolicy(),
                e.getModerationPolicy(), e.getConsentPolicy(), e.isSpeakerVerificationRequired(),
                e.getStartTime(), e.getEndTime());
    }
}
