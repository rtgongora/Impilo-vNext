package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "msika_orderable_details")
public class OrderableDetailEntity {

    @Id
    @Column(name = "item_id", length = 26)
    private String itemId;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(name = "target_kind", length = 10)
    private String targetKind;

    @Column(name = "target_item_id", length = 26)
    private String targetItemId;

    @Column(name = "specimen_type", length = 100)
    private String specimenType;

    @Column(name = "body_site", length = 100)
    private String bodySite;

    @Column(name = "instructions_template", columnDefinition = "TEXT")
    private String instructionsTemplate;

    @Column(name = "critical_result_policy", columnDefinition = "jsonb")
    private String criticalResultPolicy;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getTargetKind() { return targetKind; }
    public void setTargetKind(String targetKind) { this.targetKind = targetKind; }
    public String getTargetItemId() { return targetItemId; }
    public void setTargetItemId(String targetItemId) { this.targetItemId = targetItemId; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }
    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }
    public String getInstructionsTemplate() { return instructionsTemplate; }
    public void setInstructionsTemplate(String instructionsTemplate) { this.instructionsTemplate = instructionsTemplate; }
    public String getCriticalResultPolicy() { return criticalResultPolicy; }
    public void setCriticalResultPolicy(String criticalResultPolicy) { this.criticalResultPolicy = criticalResultPolicy; }
}
