package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.IssueLicenseRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * License lifecycle management service.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final LicenseRepository licenseRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public LicenseService(LicenseRepository licenseRepository,
                          ProviderRepository providerRepository,
                          EventOutboxRepository outboxRepository) {
        this.licenseRepository = licenseRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public LicenseEntity issueLicense(String providerPublicId, IssueLicenseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing license: providerPublicId={}, licenseType={}, actor={}",
                providerPublicId, request.licenseType(), ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        LicenseEntity license = new LicenseEntity();
        license.setProvider(provider);
        license.setTenantId(ctx.tenantId());
        license.setLicenseType(request.licenseType());
        license.setLicenseNumber(request.licenseNumber());
        license.setStatus("ACTIVE");
        license.setValidFrom(request.validFrom());
        license.setValidTo(request.validTo());
        license.setConditions(request.conditions());
        license.setIssuedBy(ctx.actorId());
        license.setIssuedAt(Instant.now());
        license.setVersion(1);

        if (request.councilId() != null) {
            CouncilEntity councilRef = new CouncilEntity();
            councilRef.setId(request.councilId());
            license.setCouncil(councilRef);
        }

        license = licenseRepository.save(license);
        log.info("License issued: licenseId={}, providerPublicId={}", license.getId(), providerPublicId);

        publishEvent("LICENSE", providerPublicId, "varapi.license.status.changed",
                String.format("{\"licenseId\":%d,\"providerPublicId\":\"%s\",\"status\":\"ACTIVE\"}",
                        license.getId(), providerPublicId));

        return license;
    }

    @Transactional
    public LicenseEntity renewLicense(String providerPublicId, Long licenseId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Renewing license: providerPublicId={}, licenseId={}, actor={}",
                providerPublicId, licenseId, ctx.actorId());

        LicenseEntity license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("License not found: " + licenseId));

        if (license.isRevoked()) {
            throw new IllegalStateException("Cannot renew a REVOKED license: " + licenseId);
        }

        String previousStatus = license.getStatus();
        LocalDate baseDate = license.getValidTo();
        if (baseDate == null || baseDate.isBefore(LocalDate.now())) {
            baseDate = LocalDate.now();
        }
        license.setValidTo(baseDate.plusYears(1));
        license.setStatus("ACTIVE");
        license.setVersion(license.getVersion() + 1);
        license = licenseRepository.save(license);

        log.info("License renewed: licenseId={}, {} -> ACTIVE", licenseId, previousStatus);

        publishEvent("LICENSE", providerPublicId, "varapi.license.status.changed",
                String.format("{\"licenseId\":%d,\"status\":\"ACTIVE\"}", licenseId));

        return license;
    }

    @Transactional
    public LicenseEntity suspendLicense(String providerPublicId, Long licenseId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Suspending license: providerPublicId={}, licenseId={}, reason={}, actor={}",
                providerPublicId, licenseId, reason, ctx.actorId());

        LicenseEntity license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("License not found: " + licenseId));

        if (license.isRevoked()) {
            throw new IllegalStateException("Cannot suspend a REVOKED license: " + licenseId);
        }

        license.setStatus("SUSPENDED");
        license.setSuspendedAt(Instant.now());
        license.setSuspensionReason(reason);
        license.setVersion(license.getVersion() + 1);
        license = licenseRepository.save(license);

        log.info("License suspended: licenseId={}", licenseId);

        publishEvent("LICENSE", providerPublicId, "varapi.license.status.changed",
                String.format("{\"licenseId\":%d,\"status\":\"SUSPENDED\"}", licenseId));

        return license;
    }

    @Transactional
    public LicenseEntity revokeLicense(String providerPublicId, Long licenseId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking license: providerPublicId={}, licenseId={}, reason={}, actor={}",
                providerPublicId, licenseId, reason, ctx.actorId());

        LicenseEntity license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("License not found: " + licenseId));

        license.setStatus("REVOKED");
        license.setRevokedAt(Instant.now());
        license.setRevocationReason(reason);
        license.setVersion(license.getVersion() + 1);
        license = licenseRepository.save(license);

        log.info("License revoked: licenseId={}", licenseId);

        publishEvent("LICENSE", providerPublicId, "varapi.license.status.changed",
                String.format("{\"licenseId\":%d,\"status\":\"REVOKED\"}", licenseId));

        return license;
    }

    @Transactional(readOnly = true)
    public List<LicenseEntity> getLicenseHistory(String providerPublicId) {
        TrustContextHolder.require();
        log.debug("Fetching license history: providerPublicId={}", providerPublicId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        return licenseRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId());
    }

    @Transactional(readOnly = true)
    public byte[] downloadCertificate(String providerPublicId, Long licenseId) {
        TrustContextHolder.require();
        log.debug("Downloading certificate: providerPublicId={}, licenseId={}", providerPublicId, licenseId);

        LicenseEntity license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("License not found: " + licenseId));

        if (!license.getProvider().getProviderPublicId().equals(providerPublicId)) {
            throw new IllegalArgumentException("License does not belong to provider: " + providerPublicId);
        }

        throw new UnsupportedOperationException(
                "Certificate PDF generation requires integration with reporting-service. " +
                "Please implement PDF generation using a template engine with providerId=" +
                license.getProvider().getId() + ", licenseType=" + license.getLicenseType() +
                ", validFrom=" + license.getValidFrom() + ", validTo=" + license.getValidTo());
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
