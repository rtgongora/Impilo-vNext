package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An academic term/calendar window with its registration period (V019). */
@Entity
@Table(name = "lrn_academic_term")
public class AcademicTermEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_id")
    private UUID programId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "academic_year", length = 16)
    private String academicYear;

    @Column(name = "term_no")
    private Integer termNo;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "registration_opens_at")
    private OffsetDateTime registrationOpensAt;

    @Column(name = "registration_closes_at")
    private OffsetDateTime registrationClosesAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PLANNED";

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public Integer getTermNo() { return termNo; }
    public void setTermNo(Integer termNo) { this.termNo = termNo; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    public OffsetDateTime getRegistrationOpensAt() { return registrationOpensAt; }
    public void setRegistrationOpensAt(OffsetDateTime registrationOpensAt) { this.registrationOpensAt = registrationOpensAt; }
    public OffsetDateTime getRegistrationClosesAt() { return registrationClosesAt; }
    public void setRegistrationClosesAt(OffsetDateTime registrationClosesAt) { this.registrationClosesAt = registrationClosesAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
