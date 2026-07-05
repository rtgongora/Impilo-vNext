package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "org_registry_organization", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 512)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private OrgType orgType;

    private UUID countryOperationRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String registrationIdentifiers;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    /** NATIVE = created in this registry; WGV_MIRROR = one-way import from wgv_organisation. */
    @Column(nullable = false, length = 32)
    private String source = "NATIVE";

    /** Back-pointer to the origin record (wgv_organisation.id for WGV_MIRROR rows). */
    @Column(length = 255)
    private String sourceRef;

    @Column(length = 128)
    private String verifiedBy;

    private OffsetDateTime verifiedAt;

    // V003 — raw wgv mirror attributes
    @Column(length = 64)
    private String wgvOrgTypeRaw;

    @Column(length = 32)
    private String wgvStatusRaw;

    @Column(length = 255)
    private String wgvParentSourceRef;

    private OffsetDateTime mirroredAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
