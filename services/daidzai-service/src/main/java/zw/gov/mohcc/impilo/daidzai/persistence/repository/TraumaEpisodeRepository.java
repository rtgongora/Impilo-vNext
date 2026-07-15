package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TraumaEpisodeRepository extends JpaRepository<TraumaEpisodeEntity, UUID> {
    Optional<TraumaEpisodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    /** Idempotency anchor for the dual-entry mint: one episode per (tenant, origin_key). */
    Optional<TraumaEpisodeEntity> findByTenantIdAndOriginKey(UUID tenantId, String originKey);
    Optional<TraumaEpisodeEntity> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);

    /** VITO merge repoint: move the subject anchor from the tombstoned to the surviving Health ID. */
    @Modifying
    @Query("update TraumaEpisodeEntity e set e.subjectHealthId = :survivor "
            + "where e.tenantId = :tenantId and e.subjectHealthId = :merged")
    int repointSubjectHealthId(@Param("tenantId") UUID tenantId,
                               @Param("merged") String mergedHealthId,
                               @Param("survivor") String survivorHealthId);
}
