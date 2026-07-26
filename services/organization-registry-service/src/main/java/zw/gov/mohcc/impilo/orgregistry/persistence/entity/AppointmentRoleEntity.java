package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The closed appointment-role vocabulary (ROM-W1, read-only reference seeded in V006). */
@Entity
@Table(name = "org_registry_appointment_role", schema = "org_registry")
@Getter
@NoArgsConstructor
public class AppointmentRoleEntity {

    @Id
    @Column(name = "role_code", length = 48)
    private String roleCode;

    @Column(nullable = false, length = 128)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_committee_member", nullable = false)
    private boolean committeeMember;

    @Column(name = "is_oversight", nullable = false)
    private boolean oversight;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * This role administers the regulator on the platform rather than performing its statutory
     * work (V009). The bootstrap, succession and four-eyes invariants attach to this flag, so a
     * role added later inherits them by being marked rather than by being remembered.
     */
    @Column(name = "is_administrative", nullable = false)
    private boolean administrative;

    /**
     * Grantable only through the bootstrap rail, and only while the organisation has no active
     * administrator — so a founding role cannot be reused later as an ordinary appointment.
     */
    @Column(name = "is_bootstrap_only", nullable = false)
    private boolean bootstrapOnly;

    /** Upper bound on simultaneous ACTIVE holders at one organisation; null means unbounded. */
    @Column(name = "max_concurrent_per_org")
    private Integer maxConcurrentPerOrg;
}
