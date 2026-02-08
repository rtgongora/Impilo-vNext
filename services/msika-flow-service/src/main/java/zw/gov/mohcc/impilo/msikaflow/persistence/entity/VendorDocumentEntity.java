package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorDocStatus;

/**
 * Tracks documents uploaded by a vendor during onboarding or compliance review,
 * linked to the Landela document service.
 * The id is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_vendor_documents")
public class VendorDocumentEntity {

    @Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "vendor_id", nullable = false)
    private String vendorId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "landela_doc_id")
    private String landelaDocId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VendorDocStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getLandelaDocId() {
        return landelaDocId;
    }

    public void setLandelaDocId(String landelaDocId) {
        this.landelaDocId = landelaDocId;
    }

    public VendorDocStatus getStatus() {
        return status;
    }

    public void setStatus(VendorDocStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
