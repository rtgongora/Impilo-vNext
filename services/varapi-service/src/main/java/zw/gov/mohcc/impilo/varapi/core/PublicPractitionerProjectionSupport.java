package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilLicenceRecordRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Shared derivation for the public practitioner projection (gateway public lane —
 * docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Single home for the allow-listed register-fact derivation reused by both the
 * registration-number verification lane
 * ({@link PublicPractitionerVerificationService}) and the facility-scoped listing
 * lane ({@link PublicFacilityPractitionerService}), so the two surfaces can never
 * diverge on how a display name, licence window, register status or registering
 * authority is derived. Every helper emits ONLY public register facts — never
 * contact details, national identifiers, date of birth or internal identifiers.</p>
 */
@Component
public class PublicPractitionerProjectionSupport {

    private final ProviderCouncilLicenceRecordRepository councilLicenceRepository;
    private final LicenseRepository licenseRepository;

    public PublicPractitionerProjectionSupport(
            ProviderCouncilLicenceRecordRepository councilLicenceRepository,
            LicenseRepository licenseRepository) {
        this.councilLicenceRepository = councilLicenceRepository;
        this.licenseRepository = licenseRepository;
    }

    /**
     * Current practising-certificate window: newest council licence record by
     * issue date; fallback to the newest legacy licence row when none exist.
     */
    public LicenceWindow resolveLicenceWindow(UUID tenantId, Long providerId) {
        if (tenantId == null || providerId == null) {
            return LicenceWindow.EMPTY;
        }
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

    /** Upper-cased, trimmed status; null for blank/absent. */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    /** Public register display name: title + given + family, never contacts. */
    public static String displayName(ProviderEntity provider) {
        if (provider == null) {
            return null;
        }
        return Stream.of(provider.getTitle(), provider.getGivenName(), provider.getFamilyName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
    }

    /** Registering authority label: council name, falling back to the issued-under authority. */
    public static String resolveAuthority(ProviderCouncilRegistrationRecordEntity record) {
        if (record == null) {
            return null;
        }
        if (record.getCouncil() != null && record.getCouncil().getName() != null) {
            return record.getCouncil().getName();
        }
        return record.getIssuedUnderAuthority();
    }

    /** Practising-certificate validity window (allow-listed public facts only). */
    public record LicenceWindow(String status, LocalDate validFrom, LocalDate validTo) {
        public static final LicenceWindow EMPTY = new LicenceWindow(null, null, null);
    }
}
