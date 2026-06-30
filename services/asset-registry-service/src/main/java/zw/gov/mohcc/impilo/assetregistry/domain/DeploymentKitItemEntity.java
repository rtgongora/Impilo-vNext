package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** An equipment item assigned to a deployment kit, with return condition on reconcile. */
@Entity
@Table(name = "asr_deployment_kit_item")
public class DeploymentKitItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "kit_id", nullable = false)
    private UUID kitId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "return_condition", length = 24)
    private String returnCondition;

    @Column(name = "return_note", length = 512)
    private String returnNote;

    protected DeploymentKitItemEntity() {}

    public DeploymentKitItemEntity(UUID kitId, UUID equipmentId) {
        this.kitId = kitId;
        this.equipmentId = equipmentId;
    }

    public UUID getItemId() { return itemId; }
    public UUID getKitId() { return kitId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getReturnCondition() { return returnCondition; }
    public void setReturnCondition(String returnCondition) { this.returnCondition = returnCondition; }
    public String getReturnNote() { return returnNote; }
    public void setReturnNote(String returnNote) { this.returnNote = returnNote; }
}
