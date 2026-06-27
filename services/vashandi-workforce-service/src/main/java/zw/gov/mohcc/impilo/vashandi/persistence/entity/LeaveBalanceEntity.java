package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Annual leave entitlement/balance for a worker — relocated from hr-payroll so Vashandi
 * owns the full leave picture (windows + entitlement). Keyed by workforce profile.
 */
@Entity
@Table(name = "vsh_leave_balance", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalanceEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID workforceProfileId;

    @Column(nullable = false, length = 64)
    private String leaveType;

    @Column(nullable = false)
    private int fiscalYear;

    @Column(nullable = false)
    private int entitlement;

    @Column(nullable = false)
    private int used;

    @Column(nullable = false)
    private int carriedOver;

    @Column(nullable = false)
    private int available;

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
