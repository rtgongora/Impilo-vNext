package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Prehospital electronic patient care record (ePCR) for an EMS mission. Captured by the responder
 * crew on the mobile provider-app en route. One ePCR per mission (UNIQUE(tenant, mission)); the
 * timed observations live in {@link EmsEpcrEventEntity}. Binds to the canonical trauma episode.
 */
@Entity
@Table(name = "dai_ems_epcr", schema = "daidzai",
        uniqueConstraints = @UniqueConstraint(name = "uq_dai_ems_epcr_mission",
                columnNames = {"tenant_id", "mission_id"}))
public class EmsEpcrEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "mission_id", nullable = false) private UUID missionId;
    @Column(name = "trauma_episode_id") private UUID traumaEpisodeId;
    @Column(name = "patient_health_id", length = 128) private String patientHealthId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "primary_survey_json", columnDefinition = "jsonb")
    private String primarySurveyJson;

    @Column(name = "narrative", columnDefinition = "TEXT") private String narrative;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getMissionId() { return missionId; }
    public void setMissionId(UUID v) { this.missionId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public String getPatientHealthId() { return patientHealthId; }
    public void setPatientHealthId(String v) { this.patientHealthId = v; }
    public String getPrimarySurveyJson() { return primarySurveyJson; }
    public void setPrimarySurveyJson(String v) { this.primarySurveyJson = v; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String v) { this.narrative = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
