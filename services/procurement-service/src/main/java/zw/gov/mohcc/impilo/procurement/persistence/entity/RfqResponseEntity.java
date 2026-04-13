package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rfq_responses", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class RfqResponseEntity {
    @Id
    private UUID responseId;
    private UUID rfqId;
    private UUID supplierId;
    private BigDecimal totalPrice;
    private Integer deliveryDays;
    @Column(columnDefinition = "TEXT")
    private String terms;
    private OffsetDateTime submittedAt;
    private boolean awarded;

    @PrePersist
    void pre() {
        if (responseId == null) {
            responseId = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }
}
