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
@Table(name = "vsh_attendance_event", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    private UUID shiftId;

    @Column(nullable = false)
    private UUID workforceProfileId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false)
    private OffsetDateTime eventTime;

    private String checkInMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String locationEvidenceJson;

    private String deviceId;
    private String supervisorConfirmedBy;

    // Ad-hoc check-in context (WHERE/WHAT) — captured when a worker checks in
    // against an active assignment / facility context rather than a rostered shift.
    private UUID assignmentId;
    private UUID facilityId;
    private String departmentId;
    private String unitId;
    private UUID workspaceId;
    private String contextScope;

    @Column(nullable = false)
    private boolean adhoc;

    @Column(nullable = false)
    private boolean offline;

    private OffsetDateTime reconciledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String auditMetadataJson;

    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (eventTime == null) {
            eventTime = OffsetDateTime.now();
        }
    }
}
