package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_event_catalogue")
public class EventCatalogueEntryEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "topic", length = 256, nullable = false, unique = true)
    private String topic;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "classification", length = 32, nullable = false)
    private String classification;

    @Column(name = "sensitivity_tier", length = 16, nullable = false)
    private String sensitivityTier = "STANDARD";

    @Column(name = "owner_service_id", length = 128, nullable = false)
    private String ownerServiceId;

    @Column(name = "schema_ref", length = 512)
    private String schemaRef;

    @Column(name = "schema_version", length = 32)
    private String schemaVersion;

    @Column(name = "partner_publishable", nullable = false)
    private boolean partnerPublishable = false;

    @Column(name = "contains_pii", nullable = false)
    private boolean containsPii = false;

    @Column(name = "contains_phi", nullable = false)
    private boolean containsPhi = false;

    @Column(name = "deprecated", nullable = false)
    private boolean deprecated = false;

    @Column(name = "deprecated_from")
    private OffsetDateTime deprecatedFrom;

    @Column(name = "introduced_at", nullable = false)
    private OffsetDateTime introducedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        if (introducedAt == null) introducedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTopic() { return topic; }
    public void setTopic(String v) { this.topic = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getClassification() { return classification; }
    public void setClassification(String v) { this.classification = v; }
    public String getSensitivityTier() { return sensitivityTier; }
    public void setSensitivityTier(String v) { this.sensitivityTier = v; }
    public String getOwnerServiceId() { return ownerServiceId; }
    public void setOwnerServiceId(String v) { this.ownerServiceId = v; }
    public String getSchemaRef() { return schemaRef; }
    public void setSchemaRef(String v) { this.schemaRef = v; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String v) { this.schemaVersion = v; }
    public boolean isPartnerPublishable() { return partnerPublishable; }
    public void setPartnerPublishable(boolean v) { this.partnerPublishable = v; }
    public boolean isContainsPii() { return containsPii; }
    public void setContainsPii(boolean v) { this.containsPii = v; }
    public boolean isContainsPhi() { return containsPhi; }
    public void setContainsPhi(boolean v) { this.containsPhi = v; }
    public boolean isDeprecated() { return deprecated; }
    public void setDeprecated(boolean v) { this.deprecated = v; }
    public OffsetDateTime getDeprecatedFrom() { return deprecatedFrom; }
    public void setDeprecatedFrom(OffsetDateTime v) { this.deprecatedFrom = v; }
    public OffsetDateTime getIntroducedAt() { return introducedAt; }
    public void setIntroducedAt(OffsetDateTime v) { this.introducedAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
