package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A service/pathway code a programme declares it covers. */
@Entity
@Table(name = "wgv_programme_service")
public class ProgrammeServiceEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "service_code", nullable = false, length = 128)
    private String serviceCode;

    @Column(name = "service_label", length = 255)
    private String serviceLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProgrammeServiceEntity() {}

    public ProgrammeServiceEntity(UUID id, UUID tenantId, UUID programmeId, String serviceCode, String serviceLabel) {
        this.id = id;
        this.tenantId = tenantId;
        this.programmeId = programmeId;
        this.serviceCode = serviceCode;
        this.serviceLabel = serviceLabel;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProgrammeId() { return programmeId; }
    public String getServiceCode() { return serviceCode; }
    public String getServiceLabel() { return serviceLabel; }
}
