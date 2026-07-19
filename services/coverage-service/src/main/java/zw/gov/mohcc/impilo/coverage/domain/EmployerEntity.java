package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Employer / group sponsor (spec §24). */
@Entity
@Table(name = "cv_employers")
public class EmployerEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "employer_code", nullable = false, length = 64) private String employerCode;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "contract_number", length = 128) private String contractNumber;
    @Column(name = "payer_ref") private UUID payerRef;
    @Column(name = "status", nullable = false, length = 16) private String status = "ACTIVE";
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom = LocalDate.now();
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public EmployerEntity() {}

    public static EmployerEntity create(UUID tenantId, String podId, String employerCode, String name) {
        EmployerEntity e = new EmployerEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.podId = podId != null ? podId : "national-spine";
        e.employerCode = employerCode;
        e.name = name;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getEmployerCode() { return employerCode; }
    public String getName() { return name; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String v) { this.contractNumber = v; }
    public UUID getPayerRef() { return payerRef; }
    public void setPayerRef(UUID v) { this.payerRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
}
