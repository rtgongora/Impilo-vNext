package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCertificateRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing provider certificates.
 * Handles issuance and tracking of practising certificates, good standing, etc.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final ProviderCertificateRepository certificateRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public CertificateService(
            ProviderCertificateRepository certificateRepository,
            ProviderRepository providerRepository,
            EventOutboxRepository outboxRepository) {
        this.certificateRepository = certificateRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Issue a certificate to a provider.
     */
    @Transactional
    public ProviderCertificateEntity issueCertificate(
            Long providerId,
            String certificateType,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuedUnderAuthority,
            String digitalArtifactRef,
            Long supersedesCertificateId,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing certificate: providerId={}, type={}, actor={}",
                providerId, certificateType, ctx.actorId());

        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());

        // Mark previous active certificate of same type as superseded
        Optional<ProviderCertificateEntity> previousActive = certificateRepository
                .findByTenantIdAndProviderIdAndCertificateTypeAndStatus(
                        ctx.tenantId(),
                        providerId,
                        certificateType,
                        "ACTIVE");
        if (previousActive.isPresent()) {
            ProviderCertificateEntity previous = previousActive.get();
            previous.setStatus("SUPERSEDED");
            previous.setNotes("Superseded by new certificate");
            certificateRepository.save(previous);
        }

        ProviderCertificateEntity certificate = new ProviderCertificateEntity();
        certificate.setProvider(provider);
        certificate.setTenantId(ctx.tenantId());
        certificate.setCertificateType(certificateType);
        certificate.setIssueDate(issueDate != null ? issueDate : LocalDate.now());
        certificate.setExpiryDate(expiryDate);
        certificate.setStatus("ACTIVE");
        certificate.setIssuedUnderAuthority(issuedUnderAuthority);
        certificate.setDigitalArtifactRef(digitalArtifactRef);
        certificate.setSupersedesCertificateId(supersedesCertificateId);
        certificate.setNotes(notes);
        certificate.setVersion(1);

        certificate = certificateRepository.save(certificate);

        log.info("Certificate issued: id={}, providerId={}, type={}",
                certificate.getId(), providerId, certificateType);
        publishEvent("PROVIDER_CERTIFICATE", certificate.getId().toString(),
                "varapi.certificate.issued",
                String.format("{\"certificateId\":%d,\"providerId\":%d,\"type\":\"%s\",\"issuedBy\":\"%s\"}",
                        certificate.getId(), providerId, certificateType, issuedUnderAuthority));

        return certificate;
    }

    /**
     * Get certificate by ID.
     */
    @Transactional(readOnly = true)
    public ProviderCertificateEntity getCertificate(Long certificateId) {
        TrustContext ctx = TrustContextHolder.require();
        return requireCertificate(certificateId, ctx.tenantId());
    }

    /**
     * Get all certificates for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getCertificatesByProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return certificateRepository.findByTenantIdAndProviderId(ctx.tenantId(), providerId);
    }

    /**
     * Get active certificates for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getActiveCertificates(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return certificateRepository.findByTenantIdAndProviderIdAndStatus(ctx.tenantId(), providerId, "ACTIVE");
    }

    /**
     * Get active certificate of a specific type.
     */
    @Transactional(readOnly = true)
    public Optional<ProviderCertificateEntity> getActiveCertificate(Long providerId, String certificateType) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return certificateRepository.findByTenantIdAndProviderIdAndCertificateTypeAndStatus(
                ctx.tenantId(),
                providerId,
                certificateType,
                "ACTIVE");
    }

    /**
     * Get certificates expiring before a date.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiringCertificates(LocalDate beforeDate) {
        TrustContext ctx = TrustContextHolder.require();
        return certificateRepository.findByTenantIdAndExpiryDateBefore(ctx.tenantId(), beforeDate);
    }

    /**
     * Check if provider has valid practising certificate.
     */
    @Transactional(readOnly = true)
    public boolean hasValidPractisingCertificate(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        Optional<ProviderCertificateEntity> cert = certificateRepository
                .findByTenantIdAndProviderIdAndCertificateTypeAndStatus(
                        ctx.tenantId(),
                        providerId,
                        "PRACTISING_CERTIFICATE",
                        "ACTIVE");
        if (cert.isEmpty()) {
            return false;
        }
        ProviderCertificateEntity certificate = cert.get();
        if (certificate.getExpiryDate() == null) {
            return true; // No expiry date means permanent
        }
        return !certificate.isExpired(); // Check if not expired
    }

    /**
     * Get valid practising certificate for provider.
     */
    @Transactional(readOnly = true)
    public Optional<ProviderCertificateEntity> getPractisingCertificate(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        return certificateRepository
                .findByTenantIdAndProviderIdAndCertificateTypeAndStatus(
                        ctx.tenantId(),
                        providerId,
                        "PRACTISING_CERTIFICATE",
                        "ACTIVE");
    }

    /**
     * Create a certificate (from controller).
     */
    @Transactional
    public ProviderCertificateEntity createCertificate(
            Long providerId,
            String certificateType,
            String certificateNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating certificate: providerId={}, type={}", providerId, certificateType);

        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());

        ProviderCertificateEntity certificate = new ProviderCertificateEntity();
        certificate.setProvider(provider);
        certificate.setTenantId(ctx.tenantId());
        certificate.setCertificateType(certificateType);
        certificate.setCertificateNumber(certificateNumber);
        certificate.setIssueDate(issueDate);
        certificate.setExpiryDate(expiryDate);
        certificate.setIssuedUnderAuthority(issuingAuthority);
        certificate.setStatus("PENDING");
        certificate.setNotes(notes);
        certificate.setVersion(1);
        certificate.setCreatedBy(ctx.actorId());
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate created: id={}", certificate.getId());
        return certificate;
    }

    /**
     * Issue a pending certificate.
     */
    @Transactional
    public ProviderCertificateEntity issueCertificate(
            Long certificateId,
            String certificateNumber,
            LocalDate issueDate,
            LocalDate expiryDate) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing certificate: id={}", certificateId);

        ProviderCertificateEntity certificate = requireCertificate(certificateId, ctx.tenantId());

        if (!"PENDING".equals(certificate.getStatus())) {
            throw new IllegalStateException("Can only issue pending certificates");
        }

        certificate.setCertificateNumber(certificateNumber);
        certificate.setIssueDate(issueDate != null ? issueDate : LocalDate.now());
        certificate.setExpiryDate(expiryDate);
        certificate.setStatus("ACTIVE");
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate issued: id={}", certificateId);
        return certificate;
    }

    /**
     * Renew a certificate.
     */
    @Transactional
    public ProviderCertificateEntity renewCertificate(
            Long certificateId,
            LocalDate newExpiryDate,
            String renewalReference,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Renewing certificate: id={}, newExpiryDate={}", certificateId, newExpiryDate);

        ProviderCertificateEntity certificate = requireCertificate(certificateId, ctx.tenantId());

        certificate.setExpiryDate(newExpiryDate);
        certificate.setStatus("ACTIVE");
        certificate.setRenewalReference(renewalReference);
        certificate.setNotes(notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate renewed: id={}", certificateId);
        publishEligibilityChanged(certificate, "ACTIVE", "Certificate renewed");
        return certificate;
    }

    /**
     * Suspend a certificate.
     */
    @Transactional
    public ProviderCertificateEntity suspendCertificate(
            Long certificateId,
            LocalDate suspensionDate,
            String reason,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Suspending certificate: id={}", certificateId);

        ProviderCertificateEntity certificate = requireCertificate(certificateId, ctx.tenantId());

        certificate.setStatus("SUSPENDED");
        certificate.setSuspensionDate(suspensionDate);
        certificate.setNotes(reason + " | " + notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate suspended: id={}", certificateId);
        publishEligibilityChanged(certificate, "SUSPENDED", reason);
        return certificate;
    }

    /**
     * Reinstate a suspended certificate.
     */
    @Transactional
    public ProviderCertificateEntity reinstateCertificate(
            Long certificateId,
            LocalDate reinstatementDate,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Reinstating certificate: id={}", certificateId);

        ProviderCertificateEntity certificate = requireCertificate(certificateId, ctx.tenantId());

        certificate.setStatus("ACTIVE");
        certificate.setNotes(certificate.getNotes() + " | Reinstated: " + notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate reinstated: id={}", certificateId);
        return certificate;
    }

    /**
     * Revoke a certificate.
     */
    @Transactional
    public ProviderCertificateEntity revokeCertificate(
            Long certificateId,
            LocalDate revocationDate,
            String reason,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking certificate: id={}", certificateId);

        ProviderCertificateEntity certificate = requireCertificate(certificateId, ctx.tenantId());

        certificate.setStatus("REVOKED");
        certificate.setRevocationDate(revocationDate != null ? revocationDate : LocalDate.now());
        certificate.setNotes("Revoked: " + reason + " | " + notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate revoked: id={}", certificateId);
        return certificate;
    }

    /**
     * Get valid certificates for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getValidCertificates(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return certificateRepository.findByTenantIdAndProviderIdAndStatus(ctx.tenantId(), providerId, "ACTIVE");
    }

    /**
     * Get current (most recent valid) certificate for a provider.
     */
    @Transactional(readOnly = true)
    public ProviderCertificateEntity getCurrentCertificate(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        List<ProviderCertificateEntity> active = certificateRepository
                .findByTenantIdAndProviderIdAndStatusOrderByExpiryDateDescIssueDateDescIdDesc(
                        ctx.tenantId(),
                        providerId,
                        "ACTIVE");
        return active.isEmpty() ? null : active.get(0);
    }

    /**
     * Get certificate by number.
     */
    @Transactional(readOnly = true)
    public ProviderCertificateEntity getCertificateByNumber(String certificateNumber) {
        TrustContext ctx = TrustContextHolder.require();
        return certificateRepository.findByCertificateNumberAndTenantId(certificateNumber, ctx.tenantId()).orElse(null);
    }

    /**
     * Get expired certificates.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiredCertificates() {
        TrustContext ctx = TrustContextHolder.require();
        return certificateRepository.findByTenantIdAndStatusAndExpiryDateBefore(ctx.tenantId(), "ACTIVE", LocalDate.now());
    }

    /**
     * Get expiring certificates within days.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiringCertificates(int daysAhead) {
        TrustContext ctx = TrustContextHolder.require();
        LocalDate targetDate = LocalDate.now().plusDays(daysAhead);
        return certificateRepository.findByTenantIdAndExpiryDateBefore(ctx.tenantId(), targetDate);
    }

    private ProviderEntity requireProvider(Long providerId, UUID tenantId) {
        return providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
    }

    private ProviderCertificateEntity requireCertificate(Long certificateId, UUID tenantId) {
        return certificateRepository.findByIdAndTenantId(certificateId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static final ObjectMapper ELIGIBILITY_JSON = new ObjectMapper();

    /**
     * Credential-change signal for downstream facility-regulatory consumers
     * (TUSO flags active PIC assignments REVIEW_REQUIRED — never auto-erases).
     */
    private void publishEligibilityChanged(ProviderCertificateEntity certificate,
                                           String newStatus, String reason) {
        ProviderEntity provider = certificate.getProvider();
        Long providerId = provider != null ? provider.getId() : null;
        String providerPublicId = provider != null ? provider.getProviderPublicId() : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerId", providerId);
        payload.put("providerPublicId", providerPublicId);
        payload.put("impiloHealthId", provider != null && provider.getImpiloHealthId() != null
                ? provider.getImpiloHealthId().toString() : null);
        payload.put("changeAxis", "CERTIFICATE");
        payload.put("newStatus", newStatus);
        payload.put("reason", reason);
        payload.put("occurredAt", OffsetDateTime.now().toString());

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CREDENTIAL");
        event.setAggregateId(providerPublicId != null ? providerPublicId : String.valueOf(providerId));
        event.setEventType("varapi.provider.eligibility.changed");
        try {
            event.setPayload(ELIGIBILITY_JSON.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Eligibility-changed payload serialization failed", e);
        }
        TrustContext ctx = TrustContextHolder.get();
        event.setTenantId(ctx != null && ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        event.setPodId("national-spine");
        event.setIdempotencyKey("varapi:eligchange:" + providerId + ":" + java.util.UUID.randomUUID());
        outboxRepository.save(event);
    }
}
