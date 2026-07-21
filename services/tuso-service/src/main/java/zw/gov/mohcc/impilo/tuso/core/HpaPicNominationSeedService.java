package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.integration.VarapiClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PicNominationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Seed PIC nominations in the HPA-2017 state machine from the materialised HPA
 * practitioner registry.
 *
 * <p>Pulls the (facility, provider) pairs VARAPI produced from the HPA
 * practitioner-in-charge import, resolves each HPA institution to its live TUSO
 * facility (deterministic {@code HPA-<id>} code), and drives each through
 * {@link PicNominationService#nominate} — so every relationship becomes a real
 * nomination in the authoritative machine, attributed to the HPA import actor,
 * with the VARAPI eligibility assessment snapshotted verbatim.</p>
 *
 * <p>Because the providers are PRELOADED/unclaimed, the eligibility check returns
 * not-yet-eligible and the nomination parks at {@code ELIGIBILITY_CHECKED} — an
 * honest state that advances (practitioner acceptance → regulator approval →
 * activation) only as the real person claims their identity. No access is
 * conferred at any point. Idempotent: a facility/provider pair that already has a
 * nomination is skipped, so a re-run seeds nothing new.</p>
 */
@Service
public class HpaPicNominationSeedService {

    private static final Logger log = LoggerFactory.getLogger(HpaPicNominationSeedService.class);

    private static final String SYS_HPA_INSTITUTION_ID = "HPA_INSTITUTION_ID";

    private final VarapiClient varapiClient;
    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final PicNominationService picNominationService;
    private final PicNominationRepository nominationRepository;

    public HpaPicNominationSeedService(VarapiClient varapiClient,
                                       FacilityRepository facilityRepository,
                                       FacilityIdentifierRepository identifierRepository,
                                       PicNominationService picNominationService,
                                       PicNominationRepository nominationRepository) {
        this.varapiClient = varapiClient;
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.picNominationService = picNominationService;
        this.nominationRepository = nominationRepository;
    }

    public record SeedSummary(int pairs, int seeded, int skippedExisting,
                              int skippedNoFacility, int failed) {}

    public SeedSummary seedFromHpa(UUID requestedTenant) {
        UUID tenantId = resolveTenant(requestedTenant);
        List<Map<String, Object>> pairs = varapiClient.fetchHpaNominationPairs();
        int seeded = 0, skippedExisting = 0, skippedNoFacility = 0, failed = 0;

        for (Map<String, Object> pair : pairs) {
            Object instObj = pair.get("hpa_institution_id");
            Object providerObj = pair.get("provider_public_id");
            if (instObj == null || providerObj == null) {
                failed++;
                continue;
            }
            long institutionId = ((Number) instObj).longValue();
            String providerPublicId = String.valueOf(providerObj).trim();
            String hpaId = "HPA-" + institutionId;

            // Resolve via the HPA_INSTITUTION_ID identifier — present on BOTH created
            // (facility_code = HPA-<id>) and enriched (identifier only) facilities.
            // Fall back to facility_code for created facilities.
            Long facilityId = identifierRepository.findBySystemAndValue(SYS_HPA_INSTITUTION_ID, hpaId)
                    .map(idn -> idn.getFacility() != null ? idn.getFacility().getId() : null)
                    .orElseGet(() -> facilityRepository.findByTenantIdAndFacilityCode(tenantId, hpaId)
                            .map(FacilityEntity::getId).orElse(null));
            if (facilityId == null) {
                skippedNoFacility++;
                continue;
            }

            if (nominationRepository.existsByFacilityIdAndProviderPublicId(facilityId, providerPublicId)) {
                skippedExisting++;
                continue;
            }

            try {
                // Its own @Transactional per nomination (includes the VARAPI eligibility call).
                picNominationService.nominate(new PicNominationService.NominateRequest(
                        facilityId, null, providerPublicId, null,
                        "HPA-2017 regulatory practitioner-in-charge import"));
                seeded++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("PIC nomination seed failed facility={} provider={}: {}",
                        facilityId, providerPublicId, e.getMessage());
            }
        }

        log.info("HPA PIC nomination seed: pairs={} seeded={} skippedExisting={} skippedNoFacility={} failed={}",
                pairs.size(), seeded, skippedExisting, skippedNoFacility, failed);
        return new SeedSummary(pairs.size(), seeded, skippedExisting, skippedNoFacility, failed);
    }

    private UUID resolveTenant(UUID requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return TrustContextHolder.require().tenantId();
        } catch (Exception e) {
            throw new IllegalStateException("No tenant context for HPA PIC nomination seed");
        }
    }
}
