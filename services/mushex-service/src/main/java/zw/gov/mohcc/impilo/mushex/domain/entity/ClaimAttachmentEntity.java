package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mushex_claim_attachments")
public class ClaimAttachmentEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(name = "landela_doc_id", nullable = false)
    private String landelaDocId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getLandelaDocId() {
        return landelaDocId;
    }

    public void setLandelaDocId(String landelaDocId) {
        this.landelaDocId = landelaDocId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
