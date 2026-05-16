package zw.gov.mohcc.impilo.msikaapps.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ma_publishers")
public class PublisherEntity {
    @Id
    private String id;
    @Column(nullable = false) private String name;
    @Column(name = "legal_name", nullable = false) private String legalName;
    @Column(name = "website_url") private String websiteUrl;
    @Column(name = "technical_contact_name")  private String technicalContactName;
    @Column(name = "technical_contact_email") private String technicalContactEmail;
    @Column(name = "technical_contact_phone") private String technicalContactPhone;
    @Column(name = "data_protection_email")   private String dataProtectionEmail;
    @Column(name = "support_email")           private String supportEmail;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "verified_at") private OffsetDateTime verifiedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getLegalName() { return legalName; } public void setLegalName(String s) { this.legalName = s; }
    public String getWebsiteUrl() { return websiteUrl; } public void setWebsiteUrl(String s) { this.websiteUrl = s; }
    public String getTechnicalContactName() { return technicalContactName; } public void setTechnicalContactName(String s) { this.technicalContactName = s; }
    public String getTechnicalContactEmail() { return technicalContactEmail; } public void setTechnicalContactEmail(String s) { this.technicalContactEmail = s; }
    public String getTechnicalContactPhone() { return technicalContactPhone; } public void setTechnicalContactPhone(String s) { this.technicalContactPhone = s; }
    public String getDataProtectionEmail() { return dataProtectionEmail; } public void setDataProtectionEmail(String s) { this.dataProtectionEmail = s; }
    public String getSupportEmail() { return supportEmail; } public void setSupportEmail(String s) { this.supportEmail = s; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; } public void setVerifiedAt(OffsetDateTime t) { this.verifiedAt = t; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
}
