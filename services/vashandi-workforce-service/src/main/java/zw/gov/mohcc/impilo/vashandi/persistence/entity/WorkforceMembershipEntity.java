package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vsh_workforce_membership", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class WorkforceMembershipEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID workforceProfileId;

    @Column(nullable = false)
    private UUID organisationId;

    @Column(nullable = false, length = 64)
    private String organisationType;

    @Column(nullable = false, length = 64)
    private String membershipType;

    @Column(nullable = false, length = 32)
    private String status = "active";

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDate expiryDate;
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String auditMetadataJson;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
