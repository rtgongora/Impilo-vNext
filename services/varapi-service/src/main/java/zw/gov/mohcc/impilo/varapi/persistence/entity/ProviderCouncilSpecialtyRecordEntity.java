package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_council_specialty_records", schema = "varapi")
public class ProviderCouncilSpecialtyRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "recognition_type", nullable = false, length = 60) private String recognitionType;
    @Column(name = "specialty_or_category_code", nullable = false, length = 80) private String specialtyOrCategoryCode;
    @Column(name = "recognition_date") private LocalDate recognitionDate;
    @Column(name = "status", nullable = false, length = 40) private String status = "ACTIVE";
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void pc() { Instant n = Instant.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void pu() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getRecognitionType() { return recognitionType; } public void setRecognitionType(String recognitionType) { this.recognitionType = recognitionType; }
    public String getSpecialtyOrCategoryCode() { return specialtyOrCategoryCode; } public void setSpecialtyOrCategoryCode(String specialtyOrCategoryCode) { this.specialtyOrCategoryCode = specialtyOrCategoryCode; }
    public LocalDate getRecognitionDate() { return recognitionDate; } public void setRecognitionDate(LocalDate recognitionDate) { this.recognitionDate = recognitionDate; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public LocalDate getExpiryDate() { return expiryDate; } public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
