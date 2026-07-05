package zw.gov.mohcc.impilo.live.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LiveDtos {

    private LiveDtos() {}

    public record CreateEventRequest(
            String title,
            String description,
            String mode,
            String owningService,
            String owningEntityId,
            String eventType,
            String contextType,
            String audienceType,
            String visibility,
            String organiserType,
            String organiserId,
            String facilityId,
            String districtId,
            String provinceId,
            String programmeId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Boolean registrationRequired,
            Integer maxParticipants,
            Boolean recordingAllowed,
            Boolean replayAllowed,
            Boolean cpdEnabled,
            BigDecimal cpdPoints,
            Integer attendanceThresholdMinutes,
            String agenda,
            String objectives,
            UUID linkedFundoCourseId,
            UUID linkedMadiDriveId
    ) {}

    public record EventResponse(
            UUID id,
            UUID tenantId,
            String title,
            String description,
            String mode,
            String owningService,
            String owningEntityId,
            String eventType,
            String contextType,
            String status,
            String facilityId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            boolean cpdEnabled,
            BigDecimal cpdPoints,
            Integer attendanceThresholdMinutes,
            boolean chatEnabled,
            boolean qnaEnabled,
            boolean pollsEnabled
    ) {}

    public record RegisterRequest(
            String participantId,
            String participantType,
            String inviteSource
    ) {}

    public record RegistrationResponse(
            UUID id,
            UUID eventId,
            String participantId,
            String registrationStatus,
            boolean saved,
            OffsetDateTime registeredAt
    ) {}

    public record JoinRoomRequest(
            String participantId,
            String participantType,
            String role,
            Boolean consentGranted
    ) {}

    public record RoomTokenRequest(
            String participantId,
            String role
    ) {}

    public record RoomTokenResponse(
            String roomId,
            String roomUrl,
            String accessToken,
            String provider,
            String channel,
            /** Server-resolved effective role the token was minted for (LIVE_EVENT modes). */
            String role
    ) {}

    /** Audience→stage promotion request (LIVE_EVENT modes). */
    public record StageRequestPayload(
            String participantId,
            String participantType
    ) {}

    /** Host/producer broadcast message; rides the chat vocabulary as kind=ANNOUNCEMENT. */
    public record AnnouncementRequest(
            String participantId,
            String participantType,
            String message
    ) {}

    public record QuestionRequest(
            String participantId,
            String participantType,
            String questionText,
            boolean anonymousAllowed
    ) {}

    public record ChatRequest(
            String participantId,
            String participantType,
            String message
    ) {}

    public record PollRequest(
            String question,
            List<String> options,
            String createdBy
    ) {}

    public record PollResponseRequest(
            String participantId,
            String selectedOption
    ) {}

    public record ModerationRequest(
            String action,
            String moderatedBy
    ) {}

    public record AttendanceResponse(
            UUID id,
            UUID eventId,
            String participantId,
            int liveWatchMinutes,
            int totalWatchMinutes,
            boolean eligibleForCertificate,
            boolean eligibleForCpd,
            String completionStatus
    ) {}

    public record CertificateResponse(
            UUID id,
            UUID eventId,
            String participantId,
            String certificateType,
            String verificationCode,
            OffsetDateTime issuedAt
    ) {}

    public record AnalyticsDashboard(
            UUID eventId,
            Map<String, Object> metrics
    ) {}

    public record DiscoverQuery(
            String contextType,
            String facilityId,
            String userId,
            String role
    ) {}

    // ---- Typed scheduling contracts (doctrine §7) ----

    /** Telemedicine / PCT clinical live session. Requires consent + clinical context. */
    public record ScheduleClinicalSessionRequest(
            String clinicalContextRef,
            String clientId,
            String providerId,
            String facilityId,
            String title,
            String description,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String privacyLevel,
            String consentState,
            String recordingPolicy,
            Boolean emergencyFlag,
            String visibility,
            List<String> additionalProviderIds,
            List<String> interpreterIds
    ) {}

    /** Fundo course-linked webinar / CPD session. */
    public record ScheduleFundoWebinarRequest(
            UUID courseId,
            String learningActivityId,
            String title,
            String description,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            List<String> trainerIds,
            String learnerAudience,
            Boolean cpdEnabled,
            BigDecimal cpdPoints,
            Integer attendanceThresholdMinutes,
            Boolean replayAllowed,
            String visibility
    ) {}

    /** Public Health / Citizen Engagement public broadcast or emergency briefing. */
    public record SchedulePublicBroadcastRequest(
            String campaignId,
            String publicHealthEventId,
            String title,
            String description,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            List<String> officialSpeakerIds,
            List<String> moderatorIds,
            String audienceScope,
            String commentModerationPolicy,
            Boolean replayAllowed,
            String notificationPolicy,
            String language,
            Boolean emergencyBriefing
    ) {}

    /** Response for the typed scheduling endpoints; returns governed metadata only. */
    public record ScheduledEventResponse(
            UUID id,
            String mode,
            String owningService,
            String owningEntityId,
            String status,
            String visibility,
            String recordingPolicy,
            String moderationPolicy,
            String consentPolicy,
            boolean speakerVerificationRequired,
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {}
}
