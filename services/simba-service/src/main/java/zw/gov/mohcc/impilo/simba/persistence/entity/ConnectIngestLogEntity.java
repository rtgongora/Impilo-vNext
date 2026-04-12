package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "connect_ingest_log", schema = "simba")
public class ConnectIngestLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "data_type", nullable = false, length = 64)
    private String dataType;

    @Column(name = "source_id", nullable = false, length = 255)
    private String sourceId;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @PrePersist
    protected void onCreate() {
        if (ingestedAt == null) {
            ingestedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public void setPersonCpid(String personCpid) {
        this.personCpid = personCpid;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public OffsetDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(OffsetDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
