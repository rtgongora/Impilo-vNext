package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "leave_balances", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalanceEntity {
    @Id
    private UUID balanceId;
    @Column(nullable = false)
    private UUID employeeId;
    @Column(nullable = false)
    private UUID leaveTypeId;
    private int fiscalYear;
    private int entitlement;
    private int used;
    private int carriedOver;
    private int available;

    @PrePersist
    void pre() {
        if (balanceId == null) {
            balanceId = UUID.randomUUID();
        }
    }
}
