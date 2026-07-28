package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Withdraws platform operation when the facility a verdict was issued about materially changes
 * (FCV-W1).
 *
 * <p>A Ministry verdict is a statement about a particular facility: at that address, under that
 * ownership, at that level. Change those and the verdict no longer describes what is in front of
 * you — but the projected allow would happily stand, so a facility could be verified at one address
 * and then quietly moved, keeping its operational status. That is the same class of hole as an
 * expiry nothing reads.</p>
 *
 * <p><b>This is a demotion, not a punishment.</b> It returns the facility to "awaiting
 * verification", which is where an unverified facility already sits — no verdict is reversed and no
 * finding of illegitimacy is made. A verifier looks again and issues a fresh decision. The Ministry's
 * recognition is untouched: the facility is still the same recognised facility, it is the
 * <em>operating</em> permission that can no longer be taken on trust.</p>
 *
 * <p>Which fields count is deliberately narrow — the ones a verifier physically checks. Renaming a
 * facility or correcting a phone number does not invalidate an inspection.</p>
 */
@Service
public class FacilityLegitimacyReverificationService {

    private static final Logger log = LoggerFactory.getLogger(FacilityLegitimacyReverificationService.class);

    /** Fields a Ministry verdict rests on. Changing one means the verdict must be re-earned. */
    public static final Set<String> MATERIAL_FIELDS = Set.of(
            "province", "district", "address_line1", "latitude", "longitude",
            "ownership", "level", "facility_type");

    private final FacilitySourceLegitimacyRepository legitimacyRepository;

    public FacilityLegitimacyReverificationService(FacilitySourceLegitimacyRepository legitimacyRepository) {
        this.legitimacyRepository = legitimacyRepository;
    }

    public static boolean isMaterial(String field) {
        return field != null && MATERIAL_FIELDS.contains(field.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Demote the facility to awaiting-verification if any changed field is material and a
     * decision-backed allow is standing.
     *
     * @return true when the allow was withdrawn
     */
    @Transactional
    public boolean onFacilityChanged(UUID facilityUuid, Collection<String> changedFields, String actorId) {
        if (facilityUuid == null || changedFields == null || changedFields.isEmpty()) {
            return false;
        }
        Set<String> material = changedFields.stream()
                .filter(FacilityLegitimacyReverificationService::isMaterial)
                .map(f -> f.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (material.isEmpty()) {
            return false;
        }

        return legitimacyRepository
                .findByFacilityIdAndSource(facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL)
                .filter(row -> row.isAllowedOnPlatform() && row.getDecisionId() != null)
                .map(row -> {
                    row.setAllowedOnPlatform(false);
                    row.setStatus(FacilityLegitimacyStatus.PENDING_VERIFICATION);
                    row.setAsOf(Instant.now());
                    row.setReason("Awaiting re-verification: " + String.join(", ", material)
                            + " changed after Ministry decision " + row.getDecisionId()
                            + ". The verdict was issued about the facility as it was; platform "
                            + "operation is withdrawn until a verifier confirms it again.");
                    row.setRecordedBy(actorId);
                    legitimacyRepository.save(row);
                    log.info("Facility {} demoted to awaiting re-verification — material change: {}",
                            facilityUuid, material);
                    return true;
                })
                .orElse(false);
    }
}
