package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shift_handover", schema = "inpatient")
public class ShiftHandoverEntity {

    @Id
    @Column(name = "handover_id")
    private UUID handoverId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "shift_id")
    private String shiftId;

    @Column(name = "outgoing_staff", nullable = false)
    private String outgoingStaff;

    @Column(name = "incoming_staff")
    private String incomingStaff;

    @Column(name = "situation")
    private String situation;

    @Column(name = "background")
    private String background;

    @Column(name = "assessment")
    private String assessment;

    @Column(name = "recommendation")
    private String recommendation;

    @Column(name = "general_notes")
    private String generalNotes;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "taken_over_at")
    private OffsetDateTime takenOverAt;

    @Column(name = "taken_over_by")
    private String takenOverBy;

    @PrePersist
    void onCreate() {
        if (handoverId == null) handoverId = UUID.randomUUID();
        if (submittedAt == null) submittedAt = OffsetDateTime.now();
    }

    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWardId() { return wardId; }
    public void setWardId(UUID wardId) { this.wardId = wardId; }
    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }
    public String getOutgoingStaff() { return outgoingStaff; }
    public void setOutgoingStaff(String outgoingStaff) { this.outgoingStaff = outgoingStaff; }
    public String getIncomingStaff() { return incomingStaff; }
    public void setIncomingStaff(String incomingStaff) { this.incomingStaff = incomingStaff; }
    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getGeneralNotes() { return generalNotes; }
    public void setGeneralNotes(String generalNotes) { this.generalNotes = generalNotes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getTakenOverAt() { return takenOverAt; }
    public void setTakenOverAt(OffsetDateTime takenOverAt) { this.takenOverAt = takenOverAt; }
    public String getTakenOverBy() { return takenOverBy; }
    public void setTakenOverBy(String takenOverBy) { this.takenOverBy = takenOverBy; }
}
