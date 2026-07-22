package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A first-class restriction on a register entry (ROM-W3, R4) — replaces restriction_summary strings. */
@Entity
@Table(name = "register_entry_restrictions", schema = "varapi")
public class RegisterEntryRestrictionEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "registration_record_id", nullable = false) private Long registrationRecordId;
    @Column(name = "restriction_type", nullable = false, length = 32) private String restrictionType;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "imposed_by", length = 128) private String imposedBy;
    @Column(name = "decision_ref", length = 255) private String decisionRef;
    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "source", nullable = false, length = 24) private String source = "NATIVE";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getRegistrationRecordId() { return registrationRecordId; }
    public void setRegistrationRecordId(Long r) { this.registrationRecordId = r; }
    public String getRestrictionType() { return restrictionType; } public void setRestrictionType(String r) { this.restrictionType = r; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getImposedBy() { return imposedBy; } public void setImposedBy(String i) { this.imposedBy = i; }
    public String getDecisionRef() { return decisionRef; } public void setDecisionRef(String d) { this.decisionRef = d; }
    public LocalDate getValidFrom() { return validFrom; } public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public LocalDate getValidTo() { return validTo; } public void setValidTo(LocalDate v) { this.validTo = v; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getSource() { return source; } public void setSource(String s) { this.source = s; }
}
