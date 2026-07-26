package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vsh_shift", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class ShiftEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID rosterId;

    @Column(nullable = false)
    private UUID workforceProfileId;

    private UUID assignmentId;

    @Column(nullable = false, length = 64)
    private String shiftType;

    @Column(nullable = false)
    private OffsetDateTime startTime;

    @Column(nullable = false)
    private OffsetDateTime endTime;

    private String locationType;
    private UUID facilityId;
    private String virtualPoolId;

    @Column(nullable = false, length = 32)
    private String status = "scheduled";

    /**
     * PRIMARY (first call) or BACKUP (second call) when this shift forms part of an on-call rota;
     * NULL for an ordinary rostered shift (V009). The on-call read is a projection over the rows
     * where this is set — a rota is not a second store.
     */
    @Column(length = 16)
    private String onCallRole;

    /**
     * Service line an on-call shift covers. Held here rather than parsed out of virtualPoolId,
     * which is a frozen TUSO routing-seam contract and must not become a display dependency.
     */
    @Column(length = 64)
    private String specialty;

    @Column(nullable = false)
    private boolean checkInRequired = true;

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
