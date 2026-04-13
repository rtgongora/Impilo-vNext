package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "leave_types", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class LeaveTypeEntity {
    @Id
    private UUID leaveTypeId;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 64)
    private String name;
    private int annualEntitlement;
    private int carryOverMax;
    private boolean requiresApproval = true;

    @PrePersist
    void pre() {
        if (leaveTypeId == null) {
            leaveTypeId = UUID.randomUUID();
        }
    }
}
