package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "grn_lines", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class GrnLineEntity {
    @Id
    private UUID lineId;
    private UUID grnId;
    private UUID poLineId;
    private BigDecimal quantityReceived;
    private BigDecimal quantityRejected;
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    private String batchNumber;
    private LocalDate expiryDate;

    @PrePersist
    void pre() {
        if (lineId == null) {
            lineId = UUID.randomUUID();
        }
    }
}
