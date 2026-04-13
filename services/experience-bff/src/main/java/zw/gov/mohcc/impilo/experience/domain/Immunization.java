package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "immunizations")
public class Immunization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "vaccine_name")
    private String vaccineName;

    @Column(name = "vaccine_code")
    private String vaccineCode;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "dose_sequence")
    private String doseSequence;

    @Column(name = "lot_number")
    private String lotNumber;

    private String site;

    private String route;

    private String status;

    @Column(name = "administered_by")
    private String administeredBy;

    @Column(name = "administered_at")
    private OffsetDateTime administeredAt;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Immunization() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getVaccineName() { return vaccineName; }
    public String getVaccineCode() { return vaccineCode; }
    public Integer getDoseNumber() { return doseNumber; }
    public String getDoseSequence() { return doseSequence; }
    public String getLotNumber() { return lotNumber; }
    public String getSite() { return site; }
    public String getRoute() { return route; }
    public String getStatus() { return status; }
    public String getAdministeredBy() { return administeredBy; }
    public OffsetDateTime getAdministeredAt() { return administeredAt; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
