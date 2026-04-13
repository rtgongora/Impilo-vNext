package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "asset_fixed_details")
public class AssetFixedDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private java.util.UUID assetId;

    private LocalDate acquisitionDate;
    private BigDecimal acquisitionCost;
    private Integer usefulLifeMonths;
    private BigDecimal salvageValue;
    @Column(length = 16)
    private String depreciationMethod = "STRAIGHT_LINE";
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;
    private BigDecimal netBookValue;
    @Column(length = 20)
    private String glAssetAccount;
    @Column(length = 20)
    private String glDepreciationAccount;
    @Column(length = 20)
    private String glExpenseAccount;
    private LocalDate disposalDate;
    private BigDecimal disposalAmount;
    @Column(columnDefinition = "TEXT")
    private String disposalReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public java.util.UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(java.util.UUID assetId) {
        this.assetId = assetId;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public void setAcquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = acquisitionDate;
    }

    public BigDecimal getAcquisitionCost() {
        return acquisitionCost;
    }

    public void setAcquisitionCost(BigDecimal acquisitionCost) {
        this.acquisitionCost = acquisitionCost;
    }

    public Integer getUsefulLifeMonths() {
        return usefulLifeMonths;
    }

    public void setUsefulLifeMonths(Integer usefulLifeMonths) {
        this.usefulLifeMonths = usefulLifeMonths;
    }

    public BigDecimal getSalvageValue() {
        return salvageValue;
    }

    public void setSalvageValue(BigDecimal salvageValue) {
        this.salvageValue = salvageValue;
    }

    public String getDepreciationMethod() {
        return depreciationMethod;
    }

    public void setDepreciationMethod(String depreciationMethod) {
        this.depreciationMethod = depreciationMethod;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) {
        this.accumulatedDepreciation = accumulatedDepreciation;
    }

    public BigDecimal getNetBookValue() {
        return netBookValue;
    }

    public void setNetBookValue(BigDecimal netBookValue) {
        this.netBookValue = netBookValue;
    }

    public String getGlAssetAccount() {
        return glAssetAccount;
    }

    public void setGlAssetAccount(String glAssetAccount) {
        this.glAssetAccount = glAssetAccount;
    }

    public String getGlDepreciationAccount() {
        return glDepreciationAccount;
    }

    public void setGlDepreciationAccount(String glDepreciationAccount) {
        this.glDepreciationAccount = glDepreciationAccount;
    }

    public String getGlExpenseAccount() {
        return glExpenseAccount;
    }

    public void setGlExpenseAccount(String glExpenseAccount) {
        this.glExpenseAccount = glExpenseAccount;
    }

    public LocalDate getDisposalDate() {
        return disposalDate;
    }

    public void setDisposalDate(LocalDate disposalDate) {
        this.disposalDate = disposalDate;
    }

    public BigDecimal getDisposalAmount() {
        return disposalAmount;
    }

    public void setDisposalAmount(BigDecimal disposalAmount) {
        this.disposalAmount = disposalAmount;
    }

    public String getDisposalReason() {
        return disposalReason;
    }

    public void setDisposalReason(String disposalReason) {
        this.disposalReason = disposalReason;
    }
}
