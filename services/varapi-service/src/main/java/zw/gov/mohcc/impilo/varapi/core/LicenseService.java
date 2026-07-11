package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * License lifecycle management service.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final LicenseRepository licenseRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;
    private final LicenseCertificatePdfGenerator certificatePdfGenerator;

    public LicenseService(LicenseRepository licenseRepository,
                          ProviderRepository providerRepository,
                          EventOutboxRepository outboxRepository,
                          LicenseCertificatePdfGenerator certificatePdfGenerator) {
        this.licenseRepository = licenseRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
        this.certificatePdfGenerator = certificatePdfGenerator;
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
        publishEligibilityChanged(license, providerPublicId, "ACTIVE", "License renewed");

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
        publishEligibilityChanged(license, providerPublicId, "SUSPENDED", reason);

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
        publishEligibilityChanged(license, providerPublicId, "REVOKED", reason);

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

        return certificatePdfGenerator.generate(license);
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

    /**
     * Emit the credential-truth change signal consumed by PIC eligibility
     * subscribers (eligibility snapshots become stale on licence transitions).
     */
    private void publishEligibilityChanged(LicenseEntity license, String providerPublicId,
                                           String newStatus, String reason) {
        ProviderEntity provider = license.getProvider();
        Long providerId = provider != null ? provider.getId() : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerId", providerId);
        payload.put("providerPublicId", providerPublicId);
        payload.put("impiloHealthId", provider != null && provider.getImpiloHealthId() != null
                ? provider.getImpiloHealthId().toString() : null);
        payload.put("changeAxis", "LICENSE");
        payload.put("newStatus", newStatus);
        payload.put("reason", reason);
        payload.put("occurredAt", OffsetDateTime.now().toString());

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CREDENTIAL");
        event.setAggregateId(providerPublicId);
        event.setEventType("varapi.provider.eligibility.changed");
        event.setPayload(toJson(payload));
        TrustContext ctx = TrustContextHolder.get();
        event.setTenantId(ctx != null && ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        event.setPodId("national-spine");
        event.setIdempotencyKey("varapi:eligchange:" + providerId + ":" + UUID.randomUUID());
        outboxRepository.save(event);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Eligibility-changed payload serialization failed", e);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
}
