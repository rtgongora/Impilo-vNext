package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_invoices", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoiceEntity {
    @Id
    private UUID invoiceId;
    private UUID tenantId;
    private String invoiceNumber;
    private UUID supplierId;
    private UUID poId;
    private UUID grnId;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate dueDate;
    private String status = "PENDING";
    @Column(name = "three_way_match")
    private boolean threeWayMatch;
    private OffsetDateTime matchedAt;
    private OffsetDateTime paidAt;

    @PrePersist
    void pre() {
        if (invoiceId == null) {
            invoiceId = UUID.randomUUID();
        }
    }
}
