package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rfqs", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class RfqEntity {
    @Id
    private UUID rfqId;
    private UUID tenantId;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    private LocalDate deadline;
    private String status = "OPEN";
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (rfqId == null) {
            rfqId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
