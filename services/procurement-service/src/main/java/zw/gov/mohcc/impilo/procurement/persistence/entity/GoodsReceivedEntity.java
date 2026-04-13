package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "goods_received", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceivedEntity {
    @Id
    private UUID grnId;
    private UUID tenantId;
    private String grnNumber;
    private UUID poId;
    private UUID supplierId;
    private String receivedBy;
    private OffsetDateTime receivedAt;
    private String inspectionStatus = "PENDING";
    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void pre() {
        if (grnId == null) {
            grnId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }
}
