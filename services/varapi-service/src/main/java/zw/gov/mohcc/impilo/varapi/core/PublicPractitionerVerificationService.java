package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilLicenceRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
 * council); the practising-certificate window comes from the newest
 * {@link ProviderCouncilLicenceRecordEntity} for that provider+council,
 * falling back to the provider's newest {@link LicenseEntity} when the
 * council-record table has no row.</p>
 */
@Service
public class PublicPractitionerVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PublicPractitionerVerificationService.class);

    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final ProviderCouncilLicenceRecordRepository councilLicenceRepository;
    private final LicenseRepository licenseRepository;

    public PublicPractitionerVerificationService(
            ProviderCouncilRegistrationRecordRepository registrationRepository,
            ProviderCouncilLicenceRecordRepository councilLicenceRepository,
            LicenseRepository licenseRepository) {
        this.registrationRepository = registrationRepository;
        this.councilLicenceRepository = councilLicenceRepository;
        this.licenseRepository = licenseRepository;
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

        String registerStatus = normalizeStatus(record.getStatus());
        String displayName = provider != null ? displayName(provider) : null;
        String profession = provider != null ? provider.getProfession() : null;
        String cadre = provider != null ? provider.getCadre() : null;
        String authority = record.getCouncil() != null && record.getCouncil().getName() != null
                ? record.getCouncil().getName()
                : record.getIssuedUnderAuthority();

        LicenceWindow licence = provider != null
                ? resolveLicenceWindow(tenantId, provider.getId())
                : LicenceWindow.EMPTY;

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
                authority);
    }

    /**
     * Current practising-certificate window: newest council licence record by
     * issue date; fallback to the newest legacy licence row when none exist.
     */
    private LicenceWindow resolveLicenceWindow(UUID tenantId, Long providerId) {
        Optional<ProviderCouncilLicenceRecordEntity> councilLicence =
                councilLicenceRepository.findByTenantIdAndProvider_Id(tenantId, providerId).stream()
                        .max(Comparator.comparing(ProviderCouncilLicenceRecordEntity::getIssueDate,
                                Comparator.nullsFirst(Comparator.naturalOrder())));
        if (councilLicence.isPresent()) {
            ProviderCouncilLicenceRecordEntity l = councilLicence.get();
            return new LicenceWindow(normalizeStatus(l.getStatus()), l.getIssueDate(), l.getExpiryDate());
        }
        return licenseRepository.findByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .findFirst()
                .map(l -> new LicenceWindow(normalizeStatus(l.getStatus()), l.getValidFrom(), l.getValidTo()))
                .orElse(LicenceWindow.EMPTY);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    /** Public register display name: title + given + family, never contacts. */
    private static String displayName(ProviderEntity provider) {
        String joined = Stream.of(provider.getTitle(), provider.getGivenName(), provider.getFamilyName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
        return joined;
    }

    private record LicenceWindow(String status, LocalDate validFrom, LocalDate validTo) {
        static final LicenceWindow EMPTY = new LicenceWindow(null, null, null);
    }
}
