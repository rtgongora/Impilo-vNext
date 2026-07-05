package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilitySourceLegitimacyRepository
        extends JpaRepository<FacilitySourceLegitimacyEntity, Long> {

    /** {@code facilityId} is the canonical facility UUID ({@code tuso.facility.facility_uuid}). */
    Optional<FacilitySourceLegitimacyEntity> findByFacilityIdAndSource(
            UUID facilityId, FacilityLegitimacySource source);

    List<FacilitySourceLegitimacyEntity> findByFacilityIdOrderBySourceAsc(UUID facilityId);
}
