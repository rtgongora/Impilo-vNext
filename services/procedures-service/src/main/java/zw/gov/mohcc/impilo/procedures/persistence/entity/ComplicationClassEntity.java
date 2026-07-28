package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One complication type within a profile — a row, not a column (§18). */
@Entity
@Table(name = "complication_class", schema = "procedures")
public class ComplicationClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "complication_type", nullable = false, length = 48)
    private String complicationType;

    @Column(name = "typical_grade_code", length = 8)
    private String typicalGradeCode;

    @Column(name = "monitoring_required", nullable = false)
    private String monitoringRequired;

    @Column(name = "escalation_action", nullable = false)
    private String escalationAction;

    @Column(name = "is_never_event", nullable = false)
    private boolean neverEvent;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getId() { return id; }
    public UUID getProfileId() { return profileId; }
    public String getComplicationType() { return complicationType; }
    public String getTypicalGradeCode() { return typicalGradeCode; }
    public String getMonitoringRequired() { return monitoringRequired; }
    public String getEscalationAction() { return escalationAction; }
    public boolean isNeverEvent() { return neverEvent; }
    public Integer getDisplayOrder() { return displayOrder; }
}
