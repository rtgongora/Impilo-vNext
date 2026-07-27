package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ObservationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads are always tenant-scoped: an observation is only ever addressable inside the tenant that
 * recorded it, so every finder leads with {@code tenantId}.
 */
@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, UUID> {

    List<ObservationEntity> findByTenantIdAndSubjectCpidOrderByEffectiveAtDesc(
            UUID tenantId, String subjectCpid);

    List<ObservationEntity> findByTenantIdAndSubjectCpidAndCodeOrderByEffectiveAtDesc(
            UUID tenantId, String subjectCpid, String code);

    List<ObservationEntity> findByTenantIdAndEncounterIdOrderByEffectiveAtDesc(
            UUID tenantId, String encounterId);

    /**
     * The provenance lookup behind the {@code uq_pct_observations_derived} unique index: a
     * re-submitted form must correct the row it already produced rather than accumulate a
     * duplicate that later disagrees with it.
     */
    Optional<ObservationEntity> findByTenantIdAndDerivedFromTypeAndDerivedFromIdAndCode(
            UUID tenantId, String derivedFromType, String derivedFromId, String code);

    Optional<ObservationEntity> findByTenantIdAndObservationId(UUID tenantId, UUID observationId);
}
