package zw.gov.mohcc.impilo.tshepo.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only audit event entity.
 * Each entry is linked to the previous via SHA-256 hash chain.
 * Database triggers prevent UPDATE and DELETE operations.
 */
@Entity
@Table(name = "audit_event", schema = "tshepo_audit")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(name = "actor_id", nullable = false, length = 255, updatable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 32, updatable = false)
    private String actorType;

    @Column(name = "subject_ref", length = 255, updatable = false)
    private String subjectRef;

    @Column(name = "resource_type", length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 255, updatable = false)
    private String resourceId;

    @Column(name = "action", nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private String outcome;

    @Column(name = "purpose_of_use", length = 32, updatable = false)
    private String purposeOfUse;

    @Column(name = "facility_id", updatable = false)
    private UUID facilityId;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "detail", columnDefinition = "jsonb", updatable = false)
    private String detail;

    @Column(name = "previous_hash", length = 64, updatable = false)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, length = 64, updatable = false)
    private String entryHash;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Long sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditEventEntity() {
        // JPA requires default constructor
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public void setSubjectRef(String subjectRef) {
        this.subjectRef = subjectRef;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getPurposeOfUse() {
        return purposeOfUse;
    }

    public void setPurposeOfUse(String purposeOfUse) {
        this.purposeOfUse = purposeOfUse;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public void setEntryHash(String entryHash) {
        this.entryHash = entryHash;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
