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
@Table(name = "vsh_workforce_profile", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class WorkforceProfileEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    private String healthId;
    private String providerWorkerId;
    private String keycloakUserId;
    private UUID primaryOrganisationId;
    private UUID primaryEmployerOrganisationId;
    private String workerType;
    private String profession;
    private String cadre;

    @Column(nullable = false, length = 32)
    private String currentStatus = "pending_appointment";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String professionalStatusSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String hscStatusSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String trainingStatusSummaryJson;

    @Column(nullable = false, length = 32)
    private String accessRiskStatus = "clear";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String provenanceMetadataJson;

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
