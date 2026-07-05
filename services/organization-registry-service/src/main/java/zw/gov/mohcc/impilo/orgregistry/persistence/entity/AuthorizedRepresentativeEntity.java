package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "org_registry_authorized_representative", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class AuthorizedRepresentativeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 128)
    private String personHealthId;

    @Column(length = 255)
    private String roleTitle;

    @Column(length = 512)
    private String evidenceRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    private LocalDate validFrom;
    private LocalDate validTo;

    @Column(length = 128)
    private String appointedBy;

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
