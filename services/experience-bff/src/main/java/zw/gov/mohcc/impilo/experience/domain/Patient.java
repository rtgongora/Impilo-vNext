package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String cpid;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String sex;

    @Column(name = "national_id")
    private String nationalId;

    private String phone;

    private String status;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Patient() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCpid() { return cpid; }
    public String getGivenName() { return givenName; }
    public String getFamilyName() { return familyName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getSex() { return sex; }
    public String getNationalId() { return nationalId; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public UUID getFacilityId() { return facilityId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
