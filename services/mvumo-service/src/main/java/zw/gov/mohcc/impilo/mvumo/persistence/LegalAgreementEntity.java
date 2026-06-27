package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A subject's acceptance of a legal/platform agreement (Privacy Policy, Terms of Use).
 *
 * <p>Deliberately separate from {@link ConsentRequestEntity}: the subject here is an actor
 * (Health ID), not a {@code Patient/} FHIR ref, and acceptance is <strong>never</strong>
 * materialised as a FHIR Consent directive in tshepo-consent. Legal acceptance is a
 * session-admission concern, not a per-resource clinical-consent decision. It reuses Mvumo's
 * lifecycle vocabulary + outbox so it has a real, audited home (closes the BFF log-only gap).</p>
 */
@Entity
@Table(name = "legal_agreement", schema = "mvumo")
public class LegalAgreementEntity {

    /** Acceptance is recorded (GRANTED); a new version supersedes the prior; the subject may withdraw. */
    public enum State { GRANTED, WITHDRAWN, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_actor_id", nullable = false, length = 255)
    private String subjectActorId;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(nullable = false, length = 32)
    private String state;

    @Column(nullable = false, length = 32)
    private String channel = "WEB";

    @Column(length = 40)
    private String assurance;

    @Column(name = "actor_ref", length = 255)
    private String actorRef;

    @Column(name = "actor_relationship", length = 64)
    private String actorRelationship;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_metadata", columnDefinition = "jsonb")
    private String deviceMetadata;

    @Column(name = "proof_ref", length = 255)
    private String proofRef;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectActorId() { return subjectActorId; }
    public void setSubjectActorId(String subjectActorId) { this.subjectActorId = subjectActorId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getAssurance() { return assurance; }
    public void setAssurance(String assurance) { this.assurance = assurance; }

    public String getActorRef() { return actorRef; }
    public void setActorRef(String actorRef) { this.actorRef = actorRef; }

    public String getActorRelationship() { return actorRelationship; }
    public void setActorRelationship(String actorRelationship) { this.actorRelationship = actorRelationship; }

    public String getDeviceMetadata() { return deviceMetadata; }
    public void setDeviceMetadata(String deviceMetadata) { this.deviceMetadata = deviceMetadata; }

    public String getProofRef() { return proofRef; }
    public void setProofRef(String proofRef) { this.proofRef = proofRef; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
