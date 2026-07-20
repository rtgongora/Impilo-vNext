package zw.gov.mohcc.impilo.ndr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A row in the NDR released / analytics zone — the genuinely de-identified counterpart
 * of a gold encounter. Materialised only from de-id engine output: the subject is a
 * per-dataset HMAC token (never a raw {@code patient_ref}), and {@code released_json}
 * carries the stripped + generalised + k-anon columns exactly as the release manifest
 * describes them. Bronze/gold stay untouched as the restricted operational zone.
 */
@Entity
@Table(name = "ndr_released_encounter")
public class ReleasedEncounterEntity {

    @Id
    @Column(name = "released_row_id", nullable = false)
    private UUID releasedRowId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_ref", nullable = false, length = 128)
    private String datasetRef;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "manifest_id")
    private UUID manifestId;

    @Column(name = "subject_token", nullable = false, length = 128)
    private String subjectToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "released_json", nullable = false, columnDefinition = "jsonb")
    private String releasedJson;

    @Column(name = "released_at", nullable = false)
    private OffsetDateTime releasedAt = OffsetDateTime.now();

    public ReleasedEncounterEntity() {}

    public ReleasedEncounterEntity(UUID tenantId, String datasetRef, UUID releaseId,
                                   UUID manifestId, String subjectToken, String releasedJson) {
        this.tenantId = tenantId;
        this.datasetRef = datasetRef;
        this.releaseId = releaseId;
        this.manifestId = manifestId;
        this.subjectToken = subjectToken;
        this.releasedJson = releasedJson;
    }

    public UUID getReleasedRowId() { return releasedRowId; }
    public UUID getTenantId() { return tenantId; }
    public String getDatasetRef() { return datasetRef; }
    public UUID getReleaseId() { return releaseId; }
    public UUID getManifestId() { return manifestId; }
    public String getSubjectToken() { return subjectToken; }
    public String getReleasedJson() { return releasedJson; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }

    public void setReleasedRowId(UUID releasedRowId) { this.releasedRowId = releasedRowId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setDatasetRef(String datasetRef) { this.datasetRef = datasetRef; }
    public void setReleaseId(UUID releaseId) { this.releaseId = releaseId; }
    public void setManifestId(UUID manifestId) { this.manifestId = manifestId; }
    public void setSubjectToken(String subjectToken) { this.subjectToken = subjectToken; }
    public void setReleasedJson(String releasedJson) { this.releasedJson = releasedJson; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }
}
