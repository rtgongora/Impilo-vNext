package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicFacilityPractitionerResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerProjectionSupport.LicenceWindow;
import zw.gov.mohcc.impilo.varapi.enums.PracticeContextType;
import zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderPracticeContextRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Facility-scoped public verified-practitioner listing (gateway public find-care
 * lane — docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Answers "who genuinely practises at this facility?" for the public find-care
 * journey. This is truth-sensitive: a practitioner is projected ONLY when ALL FOUR
 * axes are confirmed, and a provider failing ANY axis is silently dropped (never a
 * partial/pending row, no leak):</p>
 *
 * <ol>
 *   <li><b>Active, approved, in-window affiliation</b> — an ACTIVE
 *       {@link ProviderAffiliationEntity} with {@code approvalState == APPROVED}
 *       and effective window valid ({@code endDate == null || endDate >= today}).</li>
 *   <li><b>Operational + registry standing</b> — the linked provider is in an
 *       operational lifecycle
 *       ({@link ProviderLifecycleStatus#LICENCED_ACTIVE},
 *       {@link ProviderLifecycleStatus#LICENCE_DUE_FOR_RENEWAL},
 *       {@link ProviderLifecycleStatus#RENEWAL_IN_PROGRESS}) AND a verified registry
 *       status ({@link ProviderRegistryStatus#REGISTERED_ACTIVE} or
 *       {@link ProviderRegistryStatus#MATCHED_BOTH}).</li>
 *   <li><b>Platform trust</b> — {@code trustLevel >= }
 *       {@link ProviderTrustLevel#COUNCIL_VERIFIED} (ordinal-monotonic ladder).</li>
 *   <li><b>Active FACILITY practice context</b> — an ACTIVE
 *       {@link ProviderPracticeContextEntity} of type
 *       {@link PracticeContextType#FACILITY} linked to this facility for the provider.</li>
 * </ol>
 *
 * <p>An unknown/empty facility yields an EMPTY list (no existence oracle). Only the
 * allow-listed {@link PublicFacilityPractitionerResponse} fields are emitted; PII and
 * any availability/slot/schedule signal are never disclosed.</p>
 */
@Service
public class PublicFacilityPractitionerService {

    private static final Logger log = LoggerFactory.getLogger(PublicFacilityPractitionerService.class);

    /** Axis 2a — operational lifecycle values that admit public listing. */
    private static final Set<ProviderLifecycleStatus> OPERATIONAL_LIFECYCLE = EnumSet.of(
            ProviderLifecycleStatus.LICENCED_ACTIVE,
            ProviderLifecycleStatus.LICENCE_DUE_FOR_RENEWAL,
            ProviderLifecycleStatus.RENEWAL_IN_PROGRESS);

    /** Axis 2b — registry standing values that admit public listing. */
    private static final Set<ProviderRegistryStatus> VERIFIED_REGISTRY = EnumSet.of(
            ProviderRegistryStatus.REGISTERED_ACTIVE,
            ProviderRegistryStatus.MATCHED_BOTH);

    /** Axis 3 — minimum platform trust (ordinal-monotonic ladder). */
    private static final ProviderTrustLevel MIN_TRUST = ProviderTrustLevel.COUNCIL_VERIFIED;

    private static final String ACTIVE = "ACTIVE";
    private static final String APPROVED = "APPROVED";

    private final ProviderAffiliationRepository affiliationRepository;
    private final ProviderPracticeContextRepository practiceContextRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final PublicPractitionerProjectionSupport projection;

    public PublicFacilityPractitionerService(
            ProviderAffiliationRepository affiliationRepository,
            ProviderPracticeContextRepository practiceContextRepository,
            ProviderCouncilRegistrationRecordRepository registrationRepository,
            PublicPractitionerProjectionSupport projection) {
        this.affiliationRepository = affiliationRepository;
        this.practiceContextRepository = practiceContextRepository;
        this.registrationRepository = registrationRepository;
        this.projection = projection;
    }

    @Transactional(readOnly = true)
    public List<PublicFacilityPractitionerResponse> listVerifiedPractitioners(UUID tenantId, Long facilityId) {
        if (tenantId == null || facilityId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();

        // Axis 4 (bulk) — providers with an ACTIVE FACILITY practice context at this facility.
        Set<Long> facilityContextProviderIds = new HashSet<>();
        for (ProviderPracticeContextEntity ctx :
                practiceContextRepository.findByTenantIdAndLinkedFacilityIdAndStatus(tenantId, facilityId, ACTIVE)) {
            if (PracticeContextType.FACILITY.name().equalsIgnoreCase(ctx.getContextType())
                    && ctx.getProvider() != null && ctx.getProvider().getId() != null) {
                facilityContextProviderIds.add(ctx.getProvider().getId());
            }
        }

        // Axis 1 — ACTIVE affiliations at this facility. Dedupe by provider, first surviving row wins.
        Map<Long, PublicFacilityPractitionerResponse> byProvider = new LinkedHashMap<>();
        for (ProviderAffiliationEntity affiliation :
                affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, ACTIVE)) {

            if (!APPROVED.equalsIgnoreCase(affiliation.getApprovalState())) {
                continue; // affiliation not approved
            }
            if (affiliation.getEndDate() != null && affiliation.getEndDate().isBefore(today)) {
                continue; // affiliation effective window has closed
            }
            ProviderEntity provider = affiliation.getProvider();
            if (provider == null || provider.getId() == null) {
                continue;
            }
            Long providerId = provider.getId();
            if (byProvider.containsKey(providerId)) {
                continue; // already projected via an earlier surviving affiliation
            }
            // Axis 2 — operational lifecycle AND verified registry standing.
            if (!isOperational(provider.getLifecycleStatus()) || !isRegistryVerified(provider.getRegistryStatus())) {
                continue;
            }
            // Axis 3 — platform trust ladder at or above COUNCIL_VERIFIED.
            if (!hasSufficientTrust(provider.getTrustLevel())) {
                continue;
            }
            // Axis 4 — must also hold an ACTIVE FACILITY practice context here.
            if (!facilityContextProviderIds.contains(providerId)) {
                continue;
            }
            byProvider.put(providerId, project(tenantId, provider, affiliation));
        }

        List<PublicFacilityPractitionerResponse> result = List.copyOf(byProvider.values());
        log.debug("public facility practitioner listing (facility present={}, count={})",
                !result.isEmpty(), result.size());
        return result;
    }

    // ── Axis predicates ─────────────────────────────────────────────────────

    private static boolean isOperational(String lifecycleStatus) {
        ProviderLifecycleStatus ls = parse(ProviderLifecycleStatus.class, lifecycleStatus);
        return ls != null && OPERATIONAL_LIFECYCLE.contains(ls);
    }

    private static boolean isRegistryVerified(String registryStatus) {
        ProviderRegistryStatus rs = parse(ProviderRegistryStatus.class, registryStatus);
        return rs != null && VERIFIED_REGISTRY.contains(rs);
    }

    private static boolean hasSufficientTrust(String trustLevel) {
        ProviderTrustLevel tl = parse(ProviderTrustLevel.class, trustLevel);
        return tl != null && tl.ordinal() >= MIN_TRUST.ordinal();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null; // unknown/legacy string is treated as not-satisfying the axis
        }
    }

    // ── Projection (allow-listed public facts only) ─────────────────────────

    private PublicFacilityPractitionerResponse project(
            UUID tenantId, ProviderEntity provider, ProviderAffiliationEntity affiliation) {

        Optional<ProviderCouncilRegistrationRecordEntity> registration =
                registrationRepository.findByTenantIdAndProvider_Id(tenantId, provider.getId()).stream()
                        .max(Comparator.comparing(ProviderCouncilRegistrationRecordEntity::getRegistrationDate,
                                Comparator.nullsFirst(Comparator.naturalOrder())));

        String registerStatus = registration
                .map(r -> PublicPractitionerProjectionSupport.normalizeStatus(r.getStatus()))
                .orElse(null);
        String registrationNumber = registration
                .map(ProviderCouncilRegistrationRecordEntity::getRegistrationNumber)
                .orElse(null);
        LocalDate registeredSince = registration
                .map(ProviderCouncilRegistrationRecordEntity::getRegistrationDate)
                .orElse(null);
        LocalDate registrationExpiry = registration
                .map(ProviderCouncilRegistrationRecordEntity::getExpiryDate)
                .orElse(null);
        String authority = registration
                .map(PublicPractitionerProjectionSupport::resolveAuthority)
                .orElse(null);

        LicenceWindow licence = projection.resolveLicenceWindow(tenantId, provider.getId());

        return new PublicFacilityPractitionerResponse(
                PublicPractitionerProjectionSupport.displayName(provider),
                provider.getProfession(),
                provider.getCadre(),
                resolveRoleTitle(affiliation),
                registrationNumber,
                registerStatus,
                registeredSince,
                registrationExpiry,
                licence.status(),
                licence.validFrom(),
                licence.validTo(),
                authority);
    }

    /** Role title from the affiliation, falling back to a human label from the affiliation type. */
    private static String resolveRoleTitle(ProviderAffiliationEntity affiliation) {
        if (affiliation.getRoleTitle() != null && !affiliation.getRoleTitle().isBlank()) {
            return affiliation.getRoleTitle().trim();
        }
        return humaniseAffiliationType(affiliation.getAffiliationType());
    }

    /** "VISITING_CONSULTANT" -> "Visiting Consultant"; null/blank -> null. */
    private static String humaniseAffiliationType(String affiliationType) {
        if (affiliationType == null || affiliationType.isBlank()) {
            return null;
        }
        String[] parts = affiliationType.trim().toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? null : out.toString();
    }
}
