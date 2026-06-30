package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A single equipment-type requirement within a readiness profile. */
@Entity
@Table(name = "asr_readiness_requirement")
public class ReadinessRequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "requirement_id")
    private UUID requirementId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "equipment_type", nullable = false, length = 64)
    private String equipmentType;

    @Column(name = "min_count", nullable = false)
    private int minCount = 1;

    @Column(name = "must_be_critical", nullable = false)
    private boolean mustBeCritical = false;

    @Column(name = "note", length = 512)
    private String note;

    protected ReadinessRequirementEntity() {}

    public ReadinessRequirementEntity(UUID profileId, String equipmentType, int minCount) {
        this.profileId = profileId;
        this.equipmentType = equipmentType;
        this.minCount = minCount;
    }

    public UUID getRequirementId() { return requirementId; }
    public UUID getProfileId() { return profileId; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public int getMinCount() { return minCount; }
    public void setMinCount(int minCount) { this.minCount = minCount; }
    public boolean isMustBeCritical() { return mustBeCritical; }
    public void setMustBeCritical(boolean mustBeCritical) { this.mustBeCritical = mustBeCritical; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
