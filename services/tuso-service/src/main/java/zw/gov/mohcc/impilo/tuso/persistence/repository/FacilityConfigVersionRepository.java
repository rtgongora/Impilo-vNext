package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityConfigVersionEntity;

import java.util.Optional;

@Repository
public interface FacilityConfigVersionRepository extends JpaRepository<FacilityConfigVersionEntity, Long> {

    @Query("SELECT fcv FROM FacilityConfigVersionEntity fcv " +
           "WHERE fcv.facility.id = :facilityId AND fcv.active = true " +
           "ORDER BY fcv.version DESC LIMIT 1")
    Optional<FacilityConfigVersionEntity> findLatestActive(@Param("facilityId") Long facilityId);

    Page<FacilityConfigVersionEntity> findByFacility_IdOrderByVersionDesc(Long facilityId, Pageable pageable);
}
