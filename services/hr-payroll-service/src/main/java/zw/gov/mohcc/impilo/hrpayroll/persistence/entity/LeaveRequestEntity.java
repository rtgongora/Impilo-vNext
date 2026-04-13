package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_requests", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequestEntity {
    @Id
    private UUID requestId;
    @Column(nullable = false)
    private UUID employeeId;
    @Column(nullable = false)
    private UUID leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private int days;
    @Column(columnDefinition = "TEXT")
    private String reason;
    private String status = "PENDING";
    private String approvedBy;
    private OffsetDateTime approvedAt;

    @PrePersist
    void pre() {
        if (requestId == null) {
            requestId = UUID.randomUUID();
        }
    }
}
