package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Native Fundo learning certificate metadata (Phase 5A).
 *
 * <p>This is intentionally ordinary native LMS certificate metadata —
 * <strong>not</strong> a signed/verifiable credential. Signed credentials,
 * regulator-verifiable certificate documents and certificate PDFs are out
 * of scope for Phase 5 and remain the responsibility of
 * {@code credential-verification-service} in a later phase.</p>
 */
@Entity
@Table(
        name = "lrn_certificate",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_certificate_number", columnNames = {"tenant_id", "certificate_number"}),
                @UniqueConstraint(name = "uq_lrn_certificate_enrolment", columnNames = {"enrolment_id"})
        })
public class CertificateEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "certificate_number", nullable = false, length = 64)
    private String certificateNumber;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ISSUED";

    @Column(name = "cpd_eligible", nullable = false)
    private boolean cpdEligible;

    @Column(name = "cpd_points")
    private Integer cpdPoints;

    /** SHA-256 hex digest of canonical metadata — tamper-evident, not PKI-signed. */
    @Column(name = "verification_digest", length = 128)
    private String verificationDigest;

    /** Set when the scheduled lifecycle sweep emits the pre-expiry refresher signal (dedupe marker). */
    @Column(name = "refresher_notified_at")
    private OffsetDateTime refresherNotifiedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (issuedAt == null) issuedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(OffsetDateTime validUntil) { this.validUntil = validUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isCpdEligible() { return cpdEligible; }
    public void setCpdEligible(boolean cpdEligible) { this.cpdEligible = cpdEligible; }
    public Integer getCpdPoints() { return cpdPoints; }
    public void setCpdPoints(Integer cpdPoints) { this.cpdPoints = cpdPoints; }
    public String getVerificationDigest() { return verificationDigest; }
    public void setVerificationDigest(String verificationDigest) { this.verificationDigest = verificationDigest; }
    public OffsetDateTime getRefresherNotifiedAt() { return refresherNotifiedAt; }
    public void setRefresherNotifiedAt(OffsetDateTime refresherNotifiedAt) { this.refresherNotifiedAt = refresherNotifiedAt; }
}
