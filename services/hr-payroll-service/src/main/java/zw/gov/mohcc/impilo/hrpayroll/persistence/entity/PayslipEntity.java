package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payslips", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class PayslipEntity {
    @Id
    private UUID payslipId;
    @Column(nullable = false)
    private UUID runId;
    @Column(nullable = false)
    private UUID employeeId;
    private BigDecimal basicSalary;
    @Column(columnDefinition = "jsonb")
    private String allowancesJson;
    @Column(columnDefinition = "jsonb")
    private String deductionsJson;
    private BigDecimal grossPay;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal pension = BigDecimal.ZERO;
    private BigDecimal otherDeductions = BigDecimal.ZERO;
    private BigDecimal netPay;
    private String paymentMethod;
    private String paymentRef;

    @PrePersist
    void pre() {
        if (payslipId == null) {
            payslipId = UUID.randomUUID();
        }
    }
}
