package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_events", schema = "live")
public class LiveEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "context_type", nullable = false, length = 32)
    private String contextType;

    @Column(name = "audience_type", nullable = false, length = 64)
    private String audienceType;

    @Column(nullable = false, length = 32)
    private String visibility;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "organiser_type", nullable = false, length = 32)
    private String organiserType;

    @Column(name = "organiser_id", nullable = false)
    private String organiserId;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "district_id")
    private String districtId;

    @Column(name = "province_id")
    private String provinceId;

    @Column(name = "programme_id")
    private String programmeId;

    @Column(name = "linked_service_id")
    private String linkedServiceId;

    @Column(name = "linked_campaign_id")
    private String linkedCampaignId;

    @Column(name = "linked_fundo_course_id")
    private UUID linkedFundoCourseId;

    @Column(name = "linked_madi_drive_id")
    private UUID linkedMadiDriveId;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(length = 64)
    private String timezone;

    @Column(length = 16)
    private String language;

    @Column(name = "accessibility_options", columnDefinition = "jsonb")
    private String accessibilityOptions;

    @Column(name = "registration_required", nullable = false)
    private boolean registrationRequired = true;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "recording_allowed", nullable = false)
    private boolean recordingAllowed;

    @Column(name = "replay_allowed", nullable = false)
    private boolean replayAllowed;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled = true;

    @Column(name = "qna_enabled", nullable = false)
    private boolean qnaEnabled = true;

    @Column(name = "polls_enabled", nullable = false)
    private boolean pollsEnabled = true;

    @Column(name = "cpd_enabled", nullable = false)
    private boolean cpdEnabled;

    @Column(name = "cpd_points", precision = 6, scale = 2)
    private BigDecimal cpdPoints;

    @Column(name = "attendance_threshold_minutes")
    private Integer attendanceThresholdMinutes;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(name = "moderation_settings", columnDefinition = "jsonb")
    private String moderationSettings;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getContextType() { return contextType; }
    public void setContextType(String contextType) { this.contextType = contextType; }
    public String getAudienceType() { return audienceType; }
    public void setAudienceType(String audienceType) { this.audienceType = audienceType; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOrganiserType() { return organiserType; }
    public void setOrganiserType(String organiserType) { this.organiserType = organiserType; }
    public String getOrganiserId() { return organiserId; }
    public void setOrganiserId(String organiserId) { this.organiserId = organiserId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public String getDistrictId() { return districtId; }
    public void setDistrictId(String districtId) { this.districtId = districtId; }
    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }
    public String getProgrammeId() { return programmeId; }
    public void setProgrammeId(String programmeId) { this.programmeId = programmeId; }
    public String getLinkedServiceId() { return linkedServiceId; }
    public void setLinkedServiceId(String linkedServiceId) { this.linkedServiceId = linkedServiceId; }
    public String getLinkedCampaignId() { return linkedCampaignId; }
    public void setLinkedCampaignId(String linkedCampaignId) { this.linkedCampaignId = linkedCampaignId; }
    public UUID getLinkedFundoCourseId() { return linkedFundoCourseId; }
    public void setLinkedFundoCourseId(UUID linkedFundoCourseId) { this.linkedFundoCourseId = linkedFundoCourseId; }
    public UUID getLinkedMadiDriveId() { return linkedMadiDriveId; }
    public void setLinkedMadiDriveId(UUID linkedMadiDriveId) { this.linkedMadiDriveId = linkedMadiDriveId; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getAccessibilityOptions() { return accessibilityOptions; }
    public void setAccessibilityOptions(String accessibilityOptions) { this.accessibilityOptions = accessibilityOptions; }
    public boolean isRegistrationRequired() { return registrationRequired; }
    public void setRegistrationRequired(boolean registrationRequired) { this.registrationRequired = registrationRequired; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    public boolean isRecordingAllowed() { return recordingAllowed; }
    public void setRecordingAllowed(boolean recordingAllowed) { this.recordingAllowed = recordingAllowed; }
    public boolean isReplayAllowed() { return replayAllowed; }
    public void setReplayAllowed(boolean replayAllowed) { this.replayAllowed = replayAllowed; }
    public boolean isConsentRequired() { return consentRequired; }
    public void setConsentRequired(boolean consentRequired) { this.consentRequired = consentRequired; }
    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }
    public boolean isQnaEnabled() { return qnaEnabled; }
    public void setQnaEnabled(boolean qnaEnabled) { this.qnaEnabled = qnaEnabled; }
    public boolean isPollsEnabled() { return pollsEnabled; }
    public void setPollsEnabled(boolean pollsEnabled) { this.pollsEnabled = pollsEnabled; }
    public boolean isCpdEnabled() { return cpdEnabled; }
    public void setCpdEnabled(boolean cpdEnabled) { this.cpdEnabled = cpdEnabled; }
    public BigDecimal getCpdPoints() { return cpdPoints; }
    public void setCpdPoints(BigDecimal cpdPoints) { this.cpdPoints = cpdPoints; }
    public Integer getAttendanceThresholdMinutes() { return attendanceThresholdMinutes; }
    public void setAttendanceThresholdMinutes(Integer attendanceThresholdMinutes) { this.attendanceThresholdMinutes = attendanceThresholdMinutes; }
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    public String getObjectives() { return objectives; }
    public void setObjectives(String objectives) { this.objectives = objectives; }
    public String getModerationSettings() { return moderationSettings; }
    public void setModerationSettings(String moderationSettings) { this.moderationSettings = moderationSettings; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
