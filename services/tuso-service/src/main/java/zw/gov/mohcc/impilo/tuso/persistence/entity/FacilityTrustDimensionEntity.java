package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One facility trust dimension (Place Journey Doctrine §4, D-L8): trust is
 * multidimensional — a facility can be definitely real, accurately mapped and
 * legally recognised while temporarily closed or prohibited from a service.
 * Rows are materialised by {@code FacilityTrustDimensionService};
 * {@code facility_source_legitimacy} remains the platform-access gate.
 */
@Entity
@Table(name = "facility_trust_dimension", schema = "tuso")
public class FacilityTrustDimensionEntity {

    public static final List<String> DIMENSIONS = List.of(
            "EXISTENCE", "GEOSPATIAL", "AUTHORITY", "LEGAL_IDENTITY", "REGULATORY",
            "CAPABILITY", "OPERATIONAL", "COMPLIANCE", "PUBLIC_DISCLOSURE");

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_uuid", nullable = false)
    private UUID facilityUuid;

    @Column(name = "dimension", nullable = false, length = 32)
    private String dimension;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_UNKNOWN;

    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityUuid() { return facilityUuid; }
    public void setFacilityUuid(UUID facilityUuid) { this.facilityUuid = facilityUuid; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
