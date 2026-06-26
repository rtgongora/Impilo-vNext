package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A drug or vaccine implicated in (or concomitant to) a safety report. */
@Entity
@Table(name = "ps_product_exposure")
public class ProductExposureEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "report_id", nullable = false)
    private UUID reportId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_role", nullable = false, length = 16)
    private String productRole = "SUSPECT";     // SUSPECT | CONCOMITANT
    @Column(name = "product_kind", nullable = false, length = 16)
    private String productKind = "DRUG";        // DRUG | VACCINE
    @Column(name = "product_id", length = 255)
    private String productId;
    @Column(name = "product_name", nullable = false, length = 512)
    private String productName;
    @Column(name = "manufacturer", length = 255)
    private String manufacturer;
    @Column(name = "batch_number", length = 128)
    private String batchNumber;
    @Column(name = "lot_number", length = 128)
    private String lotNumber;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "dose", length = 128)
    private String dose;
    @Column(name = "dose_unit", length = 64)
    private String doseUnit;
    @Column(name = "route", length = 128)
    private String route;
    @Column(name = "frequency", length = 128)
    private String frequency;
    @Column(name = "indication", length = 512)
    private String indication;
    @Column(name = "therapy_start_date")
    private LocalDate therapyStartDate;
    @Column(name = "therapy_end_date")
    private LocalDate therapyEndDate;
    @Column(name = "dechallenge", length = 16)
    private String dechallenge;
    @Column(name = "rechallenge", length = 16)
    private String rechallenge;
    @Column(name = "dose_number", length = 32)
    private String doseNumber;
    @Column(name = "vaccination_date")
    private LocalDate vaccinationDate;
    @Column(name = "vaccination_site", length = 128)
    private String vaccinationSite;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProductExposureEntity() {}

    public ProductExposureEntity(UUID reportId, UUID tenantId, String productName) {
        this.id = UUID.randomUUID();
        this.reportId = reportId;
        this.tenantId = tenantId;
        this.productName = productName;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public String getProductRole() { return productRole; }
    public void setProductRole(String v) { this.productRole = v; }
    public String getProductKind() { return productKind; }
    public void setProductKind(String v) { this.productKind = v; }
    public String getProductId() { return productId; }
    public void setProductId(String v) { this.productId = v; }
    public String getProductName() { return productName; }
    public void setProductName(String v) { this.productName = v; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String v) { this.manufacturer = v; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String v) { this.batchNumber = v; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String v) { this.lotNumber = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public String getDose() { return dose; }
    public void setDose(String v) { this.dose = v; }
    public String getDoseUnit() { return doseUnit; }
    public void setDoseUnit(String v) { this.doseUnit = v; }
    public String getRoute() { return route; }
    public void setRoute(String v) { this.route = v; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String v) { this.frequency = v; }
    public String getIndication() { return indication; }
    public void setIndication(String v) { this.indication = v; }
    public LocalDate getTherapyStartDate() { return therapyStartDate; }
    public void setTherapyStartDate(LocalDate v) { this.therapyStartDate = v; }
    public LocalDate getTherapyEndDate() { return therapyEndDate; }
    public void setTherapyEndDate(LocalDate v) { this.therapyEndDate = v; }
    public String getDechallenge() { return dechallenge; }
    public void setDechallenge(String v) { this.dechallenge = v; }
    public String getRechallenge() { return rechallenge; }
    public void setRechallenge(String v) { this.rechallenge = v; }
    public String getDoseNumber() { return doseNumber; }
    public void setDoseNumber(String v) { this.doseNumber = v; }
    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate v) { this.vaccinationDate = v; }
    public String getVaccinationSite() { return vaccinationSite; }
    public void setVaccinationSite(String v) { this.vaccinationSite = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
