package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contracts", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class ContractEntity {
    @Id
    private UUID contractId;
    @Column(nullable = false)
    private UUID employeeId;
    private String contractType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal basicSalary;
    private String currency = "ZWL";
    @JdbcTypeCode(SqlTypes.JSON)

    @Column(columnDefinition = "jsonb")
    private String allowancesJson;
    @JdbcTypeCode(SqlTypes.JSON)

    @Column(columnDefinition = "jsonb")
    private String deductionsJson;
    private String status = "ACTIVE";

    @PrePersist
    void pre() {
        if (contractId == null) {
            contractId = UUID.randomUUID();
        }
    }
}
