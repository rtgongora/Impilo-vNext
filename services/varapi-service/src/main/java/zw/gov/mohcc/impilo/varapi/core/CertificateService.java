package zw.gov.mohcc.impilo.varapi.core;

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
import java.util.List;
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

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        // Mark previous active certificate of same type as superseded
        Optional<ProviderCertificateEntity> previousActive = certificateRepository
                .findByProviderIdAndCertificateTypeAndStatus(providerId, certificateType, "ACTIVE");
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
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));
    }

    /**
     * Get all certificates for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getCertificatesByProvider(Long providerId) {
        return certificateRepository.findByProviderId(providerId);
    }

    /**
     * Get active certificates for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getActiveCertificates(Long providerId) {
        return certificateRepository.findByProviderIdAndStatus(providerId, "ACTIVE");
    }

    /**
     * Get active certificate of a specific type.
     */
    @Transactional(readOnly = true)
    public Optional<ProviderCertificateEntity> getActiveCertificate(Long providerId, String certificateType) {
        return certificateRepository.findByProviderIdAndCertificateTypeAndStatus(providerId, certificateType, "ACTIVE");
    }

    /**
     * Get certificates expiring before a date.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiringCertificates(LocalDate beforeDate) {
        return certificateRepository.findByExpiryDateBefore(beforeDate);
    }

    /**
     * Check if provider has valid practising certificate.
     */
    @Transactional(readOnly = true)
    public boolean hasValidPractisingCertificate(Long providerId) {
        Optional<ProviderCertificateEntity> cert = certificateRepository
                .findByProviderIdAndCertificateTypeAndStatus(providerId, "PRACTISING_CERTIFICATE", "ACTIVE");
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
        return certificateRepository
                .findByProviderIdAndCertificateTypeAndStatus(providerId, "PRACTISING_CERTIFICATE", "ACTIVE");
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

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

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

        ProviderCertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));

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

        ProviderCertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));

        certificate.setExpiryDate(newExpiryDate);
        certificate.setStatus("ACTIVE");
        certificate.setRenewalReference(renewalReference);
        certificate.setNotes(notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate renewed: id={}", certificateId);
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

        ProviderCertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));

        certificate.setStatus("SUSPENDED");
        certificate.setSuspensionDate(suspensionDate);
        certificate.setNotes(reason + " | " + notes);
        certificate.setVersion(certificate.getVersion() + 1);
        certificate.setUpdatedBy(ctx.actorId());

        certificate = certificateRepository.save(certificate);
        log.info("Certificate suspended: id={}", certificateId);
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

        ProviderCertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));

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

        ProviderCertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certificateId));

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
        return certificateRepository.findByProviderIdAndStatus(providerId, "ACTIVE");
    }

    /**
     * Get current (most recent valid) certificate for a provider.
     */
    @Transactional(readOnly = true)
    public ProviderCertificateEntity getCurrentCertificate(Long providerId) {
        List<ProviderCertificateEntity> active = certificateRepository.findByProviderIdAndStatus(providerId, "ACTIVE");
        return active.isEmpty() ? null : active.get(0);
    }

    /**
     * Get certificate by number.
     */
    @Transactional(readOnly = true)
    public ProviderCertificateEntity getCertificateByNumber(String certificateNumber) {
        return certificateRepository.findByCertificateNumber(certificateNumber).orElse(null);
    }

    /**
     * Get expired certificates.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiredCertificates() {
        return certificateRepository.findByStatusAndExpiryDateBefore("ACTIVE", LocalDate.now());
    }

    /**
     * Get expiring certificates within days.
     */
    @Transactional(readOnly = true)
    public List<ProviderCertificateEntity> getExpiringCertificates(int daysAhead) {
        LocalDate targetDate = LocalDate.now().plusDays(daysAhead);
        return certificateRepository.findByExpiryDateBefore(targetDate);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}