package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "requisitions", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class RequisitionEntity {
    @Id
    private UUID requisitionId;
    private UUID tenantId;
    private UUID facilityId;
    private String departmentId;
    private String requestedBy;
    private String urgency;
    private String status = "DRAFT";
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private BigDecimal totalEstimated;
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (requisitionId == null) {
            requisitionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
