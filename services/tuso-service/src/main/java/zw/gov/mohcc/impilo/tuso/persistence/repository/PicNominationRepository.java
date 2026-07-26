package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PicNominationRepository extends JpaRepository<PicNominationEntity, UUID> {
    List<PicNominationEntity> findByFacilityIdOrderByNominatedAtDesc(Long facilityId);
    List<PicNominationEntity> findByProviderPublicIdOrderByNominatedAtDesc(String providerPublicId);
    List<PicNominationEntity> findByStateOrderByNominatedAtDesc(String state);
    List<PicNominationEntity> findByProviderPublicIdAndStateIn(String providerPublicId, Collection<String> states);

    boolean existsByFacilityIdAndProviderPublicId(Long facilityId, String providerPublicId);

    /**
     * HAR W3 — the registry-wide nomination queue.
     *
     * <p>Only per-facility and per-practitioner lookups existed, so the 6,180 nominations the HPA
     * seed produced had no screen a registrar could open: you could see them one facility at a
     * time, across 5,509 facilities. Province lives on {@code tuso.facility}, so this is native —
     * {@link PicNominationEntity} holds {@code facilityId} as a plain column, not a relation.</p>
     */
    @Query(value = """
            SELECT n.* FROM tuso.pic_nomination n
              JOIN tuso.facility f ON f.id = n.facility_id
             WHERE n.tenant_id = :tenantId
               AND (:state IS NULL OR n.state = :state)
               AND (:province IS NULL OR lower(f.province) = lower(:province))
             ORDER BY n.nominated_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM tuso.pic_nomination n
              JOIN tuso.facility f ON f.id = n.facility_id
             WHERE n.tenant_id = :tenantId
               AND (:state IS NULL OR n.state = :state)
               AND (:province IS NULL OR lower(f.province) = lower(:province))
            """,
            nativeQuery = true)
    Page<PicNominationEntity> searchQueue(@Param("tenantId") UUID tenantId,
                                          @Param("state") String state,
                                          @Param("province") String province,
                                          Pageable pageable);
}
