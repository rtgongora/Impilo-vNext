package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_custody_records")
public class CustodyRecordEntity {

    @Id
    @Column(name = "custody_id", nullable = false, length = 26)
    private String custodyId;

    @Column(name = "fulfillment_id", length = 26)
    private String fulfillmentId;

    @Column(name = "package_ref", length = 128)
    private String packageRef;

    @Column(name = "custodian_type", nullable = false, length = 40)
    private String custodianType;

    @Column(name = "custodian_ref", nullable = false, length = 255)
    private String custodianRef;

    @Column(name = "from_at", nullable = false)
    private OffsetDateTime fromAt;

    @Column(name = "to_at")
    private OffsetDateTime toAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_summary", columnDefinition = "jsonb")
    private String conditionSummaryJson = "{}";

    @Column(name = "seal_status", length = 40)
    private String sealStatus;

    @Column(name = "temperature_status", length = 40)
    private String temperatureStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @PrePersist
    protected void onCreate() {
        if (fromAt == null) fromAt = OffsetDateTime.now();
        if (conditionSummaryJson == null) conditionSummaryJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    public String getCustodyId() { return custodyId; }
    public void setCustodyId(String custodyId) { this.custodyId = custodyId; }
    public String getFulfillmentId() { return fulfillmentId; }
    public void setFulfillmentId(String fulfillmentId) { this.fulfillmentId = fulfillmentId; }
    public String getPackageRef() { return packageRef; }
    public void setPackageRef(String packageRef) { this.packageRef = packageRef; }
    public String getCustodianType() { return custodianType; }
    public void setCustodianType(String custodianType) { this.custodianType = custodianType; }
    public String getCustodianRef() { return custodianRef; }
    public void setCustodianRef(String custodianRef) { this.custodianRef = custodianRef; }
    public OffsetDateTime getFromAt() { return fromAt; }
    public void setFromAt(OffsetDateTime fromAt) { this.fromAt = fromAt; }
    public OffsetDateTime getToAt() { return toAt; }
    public void setToAt(OffsetDateTime toAt) { this.toAt = toAt; }
    public String getConditionSummaryJson() { return conditionSummaryJson; }
    public void setConditionSummaryJson(String conditionSummaryJson) { this.conditionSummaryJson = conditionSummaryJson; }
    public String getSealStatus() { return sealStatus; }
    public void setSealStatus(String sealStatus) { this.sealStatus = sealStatus; }
    public String getTemperatureStatus() { return temperatureStatus; }
    public void setTemperatureStatus(String temperatureStatus) { this.temperatureStatus = temperatureStatus; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

