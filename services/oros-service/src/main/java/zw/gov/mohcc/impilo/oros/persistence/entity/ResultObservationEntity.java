package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single structured laboratory observation (analyte/value/unit/reference-range/flags) belonging
 * to a {@link ResultEntity}. Enables a clinically useful lab table, per-analyte critical flagging,
 * and longitudinal trends (spec §7).
 */
@Entity
@Table(name = "oros_result_observations")
public class ResultObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "observation_id", nullable = false)
    private UUID observationId;

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "sequence", nullable = false)
    private int sequence = 0;

    @Column(name = "analyte_code", length = 64)
    private String analyteCode;

    @Column(name = "analyte_system", length = 255)
    private String analyteSystem;

    @Column(name = "analyte_name", nullable = false, length = 255)
    private String analyteName;

    @Column(name = "value_text", length = 255)
    private String valueText;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "value_type", length = 16)
    private String valueType;

    @Column(name = "unit", length = 48)
    private String unit;

    @Column(name = "ref_range_low")
    private BigDecimal refRangeLow;

    @Column(name = "ref_range_high")
    private BigDecimal refRangeHigh;

    @Column(name = "ref_range_text", length = 128)
    private String refRangeText;

    @Column(name = "abnormal_flag", length = 8)
    private String abnormalFlag;

    @Column(name = "critical_flag", nullable = false)
    private boolean criticalFlag = false;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getObservationId() { return observationId; }
    public void setObservationId(UUID observationId) { this.observationId = observationId; }

    public UUID getResultId() { return resultId; }
    public void setResultId(UUID resultId) { this.resultId = resultId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getAnalyteCode() { return analyteCode; }
    public void setAnalyteCode(String analyteCode) { this.analyteCode = analyteCode; }

    public String getAnalyteSystem() { return analyteSystem; }
    public void setAnalyteSystem(String analyteSystem) { this.analyteSystem = analyteSystem; }

    public String getAnalyteName() { return analyteName; }
    public void setAnalyteName(String analyteName) { this.analyteName = analyteName; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }

    public BigDecimal getValueNumeric() { return valueNumeric; }
    public void setValueNumeric(BigDecimal valueNumeric) { this.valueNumeric = valueNumeric; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getRefRangeLow() { return refRangeLow; }
    public void setRefRangeLow(BigDecimal refRangeLow) { this.refRangeLow = refRangeLow; }

    public BigDecimal getRefRangeHigh() { return refRangeHigh; }
    public void setRefRangeHigh(BigDecimal refRangeHigh) { this.refRangeHigh = refRangeHigh; }

    public String getRefRangeText() { return refRangeText; }
    public void setRefRangeText(String refRangeText) { this.refRangeText = refRangeText; }

    public String getAbnormalFlag() { return abnormalFlag; }
    public void setAbnormalFlag(String abnormalFlag) { this.abnormalFlag = abnormalFlag; }

    public boolean isCriticalFlag() { return criticalFlag; }
    public void setCriticalFlag(boolean criticalFlag) { this.criticalFlag = criticalFlag; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
