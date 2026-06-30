package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Custody transfer with optional approval + receiving-party confirmation. */
@Entity
@Table(name = "asr_equipment_transfer")
public class EquipmentTransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "from_facility", length = 255)
    private String fromFacility;

    @Column(name = "to_facility", nullable = false, length = 255)
    private String toFacility;

    @Column(name = "from_location", length = 512)
    private String fromLocation;

    @Column(name = "to_location", length = 512)
    private String toLocation;

    @Column(name = "to_custodian_ref", length = 255)
    private String toCustodianRef;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "received_by", length = 255)
    private String receivedBy;

    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt = OffsetDateTime.now();

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected EquipmentTransferEntity() {}

    public EquipmentTransferEntity(UUID tenantId, UUID equipmentId, String toFacility) {
        this.tenantId = tenantId;
        this.equipmentId = equipmentId;
        this.toFacility = toFacility;
        this.initiatedAt = OffsetDateTime.now();
    }

    public UUID getTransferId() { return transferId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getFromFacility() { return fromFacility; }
    public void setFromFacility(String fromFacility) { this.fromFacility = fromFacility; }
    public String getToFacility() { return toFacility; }
    public void setToFacility(String toFacility) { this.toFacility = toFacility; }
    public String getFromLocation() { return fromLocation; }
    public void setFromLocation(String fromLocation) { this.fromLocation = fromLocation; }
    public String getToLocation() { return toLocation; }
    public void setToLocation(String toLocation) { this.toLocation = toLocation; }
    public String getToCustodianRef() { return toCustodianRef; }
    public void setToCustodianRef(String toCustodianRef) { this.toCustodianRef = toCustodianRef; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public OffsetDateTime getInitiatedAt() { return initiatedAt; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
