package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class SupplierEntity {
    @Id
    private UUID supplierId;
    private UUID tenantId;
    private String name;
    private String registrationNumber;
    private String taxNumber;
    private String contactPerson;
    private String phone;
    private String email;
    @Column(columnDefinition = "TEXT")
    private String address;
    private String category;
    private String paymentTerms;
    private String bankAccount;
    private String bankName;
    private String status = "ACTIVE";
    private BigDecimal rating;
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
