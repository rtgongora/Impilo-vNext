package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A statutory professional register within a council (ROM-W3, R4). */
@Entity
@Table(name = "professional_registers", schema = "varapi")
public class ProfessionalRegisterEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "council_id", nullable = false) private Long councilId;
    @Column(name = "register_code", nullable = false, length = 64) private String registerCode;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getCouncilId() { return councilId; } public void setCouncilId(Long c) { this.councilId = c; }
    public String getRegisterCode() { return registerCode; } public void setRegisterCode(String c) { this.registerCode = c; }
    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
}
