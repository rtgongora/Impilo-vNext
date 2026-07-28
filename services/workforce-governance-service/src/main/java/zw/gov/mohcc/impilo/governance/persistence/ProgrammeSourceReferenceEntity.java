package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Traces a canonical programme back to the legacy free-text programme_id
 * value it was resolved from during the A5 migration/quarantine sweep.
 */
@Entity
@Table(name = "wgv_programme_source_reference")
public class ProgrammeSourceReferenceEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "source_service", nullable = false, length = 128)
    private String sourceService;

    @Column(name = "source_value", nullable = false, length = 255)
    private String sourceValue;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    protected ProgrammeSourceReferenceEntity() {}

    public ProgrammeSourceReferenceEntity(UUID id, UUID tenantId, UUID programmeId,
                                           String sourceService, String sourceValue, String resolvedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.programmeId = programmeId;
        this.sourceService = sourceService;
        this.sourceValue = sourceValue;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProgrammeId() { return programmeId; }
    public String getSourceService() { return sourceService; }
    public String getSourceValue() { return sourceValue; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
}
