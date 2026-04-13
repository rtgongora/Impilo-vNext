package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "requisition_lines", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class RequisitionLineEntity {
    @Id
    private UUID lineId;
    private UUID requisitionId;
    private String itemCode;
    private String description;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal estimatedUnitPrice;

    @PrePersist
    void pre() {
        if (lineId == null) {
            lineId = UUID.randomUUID();
        }
    }
}
