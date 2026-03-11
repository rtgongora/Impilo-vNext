package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "registry_providers")
public class RegistryProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "provider_number", nullable = false)
    private String providerNumber;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    private String qualification;

    private String speciality;

    @Column(name = "facility_id")
    private UUID facilityId;

    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RegistryProvider() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getProviderNumber() { return providerNumber; }
    public String getGivenName() { return givenName; }
    public String getFamilyName() { return familyName; }
    public String getQualification() { return qualification; }
    public String getSpeciality() { return speciality; }
    public UUID getFacilityId() { return facilityId; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
