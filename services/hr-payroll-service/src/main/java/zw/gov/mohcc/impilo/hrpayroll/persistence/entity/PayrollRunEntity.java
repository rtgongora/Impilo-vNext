package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_runs", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRunEntity {
    @Id
    private UUID runId;
    @Column(nullable = false)
    private UUID tenantId;
    private int periodMonth;
    private int periodYear;
    private String status = "DRAFT";
    private BigDecimal totalGross = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal totalNet = BigDecimal.ZERO;
    private String approvedBy;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (runId == null) {
            runId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
