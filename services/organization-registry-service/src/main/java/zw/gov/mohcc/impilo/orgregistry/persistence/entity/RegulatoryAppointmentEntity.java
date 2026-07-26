package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A regulatory appointment (ROM-W1): a verified person bound to a regulatory organisation in a
 * closed-vocabulary role with a jurisdiction. This is the login-context anchor for regulatory
 * personnel (doctrine §2). Imports and invitations land {@code PENDING_VERIFICATION} — an
 * appointment grants no access until ACTIVE (grants-no-authority law).
 */
@Entity
@Table(name = "org_registry_regulatory_appointment", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class RegulatoryAppointmentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 128)
    private String personHealthId;

    @Column(nullable = false, length = 48)
    private String roleCode;

    @Column(nullable = false, length = 48)
    private String jurisdictionCode = "NATIONAL";

    private UUID committeeId;

    @Column(nullable = false, length = 32)
    private String status = "PENDING_VERIFICATION";

    @Column(nullable = false, length = 32)
    private String source = "NATIVE";

    @Column(length = 512)
    private String evidenceRef;

    @Column(length = 128)
    private String appointedBy;

    private LocalDate validFrom;
    private LocalDate validTo;

    @Column(length = 128)
    private String verifiedBy;
    private OffsetDateTime verifiedAt;

    /**
     * Stamped by {@code RegulatoryAppointmentExpirySweep} when this appointment was ended because
     * {@code validTo} had passed, rather than because a registrar ended it. Both outcomes are
     * ENDED; only this column tells the holder why their access stopped.
     */
    private OffsetDateTime expirySweptAt;

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
