package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "deduction_types", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class DeductionTypeEntity {
    @Id
    private UUID typeId;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(nullable = false, length = 32)
    private String category;
    @Column(nullable = false, length = 16)
    private String calculationMethod;
    private BigDecimal rate;
    @Column(name = "is_statutory")
    private boolean statutory;

    @PrePersist
    void pre() {
        if (typeId == null) {
            typeId = UUID.randomUUID();
        }
    }
}
