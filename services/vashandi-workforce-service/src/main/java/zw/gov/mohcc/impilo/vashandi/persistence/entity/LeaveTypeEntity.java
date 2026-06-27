package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Leave-type catalog (Annual, Sick, …) — the reference config that seeds leave balances.
 * Part of the workforce leave picture, so owned by Vashandi (relocated from hr-payroll).
 */
@Entity
@Table(name = "vsh_leave_type", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class LeaveTypeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private int annualEntitlement;

    @Column(nullable = false)
    private int carryOverMax;

    @Column(nullable = false)
    private boolean requiresApproval = true;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
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
