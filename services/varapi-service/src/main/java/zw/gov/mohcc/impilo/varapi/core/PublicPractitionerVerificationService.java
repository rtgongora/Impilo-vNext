package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerProjectionSupport.LicenceWindow;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Public practitioner register verification (gateway W2, pillar 4 "Find or
 * verify a service" — docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Resolves a council registration number to public register facts only.
 * Lookup is EXACT-match (case-insensitive) by registration number — no fuzzy
 * or name search, by design (enumeration resistance). A miss returns the
 * uniform {@code NOT_FOUND} shape without leaking why.</p>
 *
 * <p>Data reality: register truth lives on
 * {@link ProviderCouncilRegistrationRecordEntity} (number, status, dates,
 * council); the practising-certificate window and display/authority derivation
 * are delegated to {@link PublicPractitionerProjectionSupport} so this lane and
 * the facility-scoped listing lane never diverge.</p>
 */
@Service
public class PublicPractitionerVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PublicPractitionerVerificationService.class);

    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final PublicPractitionerProjectionSupport projection;
    private final zw.gov.mohcc.impilo.varapi.integration.RitoClient ritoClient;

    public PublicPractitionerVerificationService(
            ProviderCouncilRegistrationRecordRepository registrationRepository,
            PublicPractitionerProjectionSupport projection,
            zw.gov.mohcc.impilo.varapi.integration.RitoClient ritoClient) {
        this.registrationRepository = registrationRepository;
        this.projection = projection;
        this.ritoClient = ritoClient;
    }

    @Transactional(readOnly = true)
    public PublicPractitionerVerificationResponse verify(UUID tenantId, String registrationNumber) {
        if (tenantId == null || registrationNumber == null || registrationNumber.isBlank()) {
            return PublicPractitionerVerificationResponse.notFound();
        }
        Optional<ProviderCouncilRegistrationRecordEntity> record = registrationRepository
                .findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, registrationNumber.trim());
        log.debug("public practitioner verification requested (result present={})", record.isPresent());
        return record.map(r -> toPublicDisclosure(tenantId, r))
                .orElseGet(PublicPractitionerVerificationResponse::notFound);
    }

    // ---- Mapper: allow-listed public register facts only ----

    private PublicPractitionerVerificationResponse toPublicDisclosure(
            UUID tenantId, ProviderCouncilRegistrationRecordEntity record) {
        ProviderEntity provider = record.getProvider();

        String registerStatus = PublicPractitionerProjectionSupport.normalizeStatus(record.getStatus());
        String displayName = PublicPractitionerProjectionSupport.displayName(provider);
        String profession = provider != null ? provider.getProfession() : null;
        String cadre = provider != null ? provider.getCadre() : null;
        String authority = PublicPractitionerProjectionSupport.resolveAuthority(record);

        LicenceWindow licence = provider != null
                ? projection.resolveLicenceWindow(tenantId, provider.getId())
                : LicenceWindow.EMPTY;

        // Read-only, Rito-sourced patient-experience summary — composed, never owned.
        // Gated + fail-open: null when Rito is disabled/unavailable.
        PublicPractitionerVerificationResponse.ExperienceSummary experience = provider != null
                ? ritoClient.getExperienceSummary(tenantId, provider.getProviderPublicId())
                : null;

        return new PublicPractitionerVerificationResponse(
                registerStatus,
                displayName,
                profession,
                cadre,
                record.getRegistrationNumber(),
                record.getRegistrationDate(),
                record.getExpiryDate(),
                licence.status(),
                licence.validFrom(),
                licence.validTo(),
                authority,
                experience);
    }
}
