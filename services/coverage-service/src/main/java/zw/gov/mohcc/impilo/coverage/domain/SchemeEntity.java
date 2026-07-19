package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Scheme — the broad financing arrangement within a payer (spec §9). */
@Entity
@Table(name = "cv_schemes")
public class SchemeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "payer_ref", nullable = false)
    private UUID payerRef;

    @Column(name = "scheme_code", nullable = false, length = 64)
    private String schemeCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "coverage_type", nullable = false, length = 32)
    private String coverageType;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public SchemeEntity() {}

    public static SchemeEntity create(UUID tenantId, String podId, UUID payerRef,
                                      String schemeCode, String name, String coverageType) {
        SchemeEntity s = new SchemeEntity();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId;
        s.podId = podId != null ? podId : "national-spine";
        s.payerRef = payerRef;
        s.schemeCode = schemeCode;
        s.name = name;
        s.coverageType = coverageType;
        return s;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getPayerRef() { return payerRef; }
    public String getSchemeCode() { return schemeCode; }
    public String getName() { return name; }
    public String getCoverageType() { return coverageType; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
}
