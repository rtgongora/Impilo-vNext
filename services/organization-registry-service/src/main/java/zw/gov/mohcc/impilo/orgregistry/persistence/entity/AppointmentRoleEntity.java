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
}
