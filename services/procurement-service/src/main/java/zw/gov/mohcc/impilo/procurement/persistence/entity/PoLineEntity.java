package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "po_lines", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class PoLineEntity {
    @Id
    private UUID lineId;
    private UUID poId;
    private String itemCode;
    private String description;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @PrePersist
    void pre() {
        if (lineId == null) {
            lineId = UUID.randomUUID();
        }
    }
}
