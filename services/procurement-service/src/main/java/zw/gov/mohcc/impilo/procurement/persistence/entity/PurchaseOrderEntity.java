package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderEntity {
    @Id
    private UUID poId;
    private UUID tenantId;
    private String poNumber;
    private UUID supplierId;
    private UUID requisitionId;
    private UUID facilityId;
    @Column(columnDefinition = "TEXT")
    private String deliveryAddress;
    private String paymentTerms;
    private BigDecimal totalAmount;
    private String currency = "ZWL";
    private String status = "DRAFT";
    private String createdBy;
    private String approvedBy;
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
