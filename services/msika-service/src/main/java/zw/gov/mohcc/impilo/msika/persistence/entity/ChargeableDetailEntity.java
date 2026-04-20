package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "msika_chargeable_details")
public class ChargeableDetailEntity {

    @Id
    @Column(name = "item_id", length = 26)
    private String itemId;

    @Column(name = "target_kind", length = 10)
    private String targetKind;

    @Column(name = "target_item_id", length = 26)
    private String targetItemId;

    @Column(name = "tariff_code", length = 100)
    private String tariffCode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "pricing_refs", columnDefinition = "jsonb")
    private String pricingRefs;

    @Column(name = "cost_method_ref", length = 100)
    private String costMethodRef;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "billable_rules", columnDefinition = "jsonb")
    private String billableRules;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getTargetKind() { return targetKind; }
    public void setTargetKind(String targetKind) { this.targetKind = targetKind; }
    public String getTargetItemId() { return targetItemId; }
    public void setTargetItemId(String targetItemId) { this.targetItemId = targetItemId; }
    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }
    public String getPricingRefs() { return pricingRefs; }
    public void setPricingRefs(String pricingRefs) { this.pricingRefs = pricingRefs; }
    public String getCostMethodRef() { return costMethodRef; }
    public void setCostMethodRef(String costMethodRef) { this.costMethodRef = costMethodRef; }
    public String getBillableRules() { return billableRules; }
    public void setBillableRules(String billableRules) { this.billableRules = billableRules; }
}
