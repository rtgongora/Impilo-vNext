package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.vito.core.MatchDisposition;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "match_result", schema = "vito")
public class MatchResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_health_id", nullable = false)
    private UUID sourceHealthId;

    @Column(name = "candidate_health_id", nullable = false)
    private UUID candidateHealthId;

    @Column(name = "match_score", nullable = false)
    private BigDecimal matchScore;

    @Column(name = "match_algorithm", nullable = false)
    private String matchAlgorithm;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "match_fields", nullable = false, columnDefinition = "jsonb")
    private String matchFields;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", nullable = false)
    private MatchDisposition disposition = MatchDisposition.PENDING;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getSourceHealthId() { return sourceHealthId; }
    public void setSourceHealthId(UUID sourceHealthId) { this.sourceHealthId = sourceHealthId; }
    public UUID getCandidateHealthId() { return candidateHealthId; }
    public void setCandidateHealthId(UUID candidateHealthId) { this.candidateHealthId = candidateHealthId; }
    public BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }
    public String getMatchAlgorithm() { return matchAlgorithm; }
    public void setMatchAlgorithm(String matchAlgorithm) { this.matchAlgorithm = matchAlgorithm; }
    public String getMatchFields() { return matchFields; }
    public void setMatchFields(String matchFields) { this.matchFields = matchFields; }
    public MatchDisposition getDisposition() { return disposition; }
    public void setDisposition(MatchDisposition disposition) { this.disposition = disposition; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
