package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "asset_depreciation_schedule")
public class AssetDepreciationScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    private LocalDate periodDate;
    private BigDecimal depreciationAmount;
    private BigDecimal accumulated;
    private BigDecimal netBookValue;
    private boolean postedToGl;
    @Column(length = 128)
    private String glEntryRef;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public LocalDate getPeriodDate() {
        return periodDate;
    }

    public void setPeriodDate(LocalDate periodDate) {
        this.periodDate = periodDate;
    }

    public BigDecimal getDepreciationAmount() {
        return depreciationAmount;
    }

    public void setDepreciationAmount(BigDecimal depreciationAmount) {
        this.depreciationAmount = depreciationAmount;
    }

    public BigDecimal getAccumulated() {
        return accumulated;
    }

    public void setAccumulated(BigDecimal accumulated) {
        this.accumulated = accumulated;
    }

    public BigDecimal getNetBookValue() {
        return netBookValue;
    }

    public void setNetBookValue(BigDecimal netBookValue) {
        this.netBookValue = netBookValue;
    }

    public boolean isPostedToGl() {
        return postedToGl;
    }

    public void setPostedToGl(boolean postedToGl) {
        this.postedToGl = postedToGl;
    }

    public String getGlEntryRef() {
        return glEntryRef;
    }

    public void setGlEntryRef(String glEntryRef) {
        this.glEntryRef = glEntryRef;
    }
}
