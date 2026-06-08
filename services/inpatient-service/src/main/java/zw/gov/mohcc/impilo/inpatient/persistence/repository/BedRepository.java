package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BedRepository extends JpaRepository<BedEntity, UUID> {

    List<BedEntity> findByFacilityId(UUID facilityId);

    List<BedEntity> findByFacilityIdAndWardId(UUID facilityId, UUID wardId);

    List<BedEntity> findByFacilityIdAndStatus(UUID facilityId, String status);

    List<BedEntity> findByFacilityIdAndWardIdAndStatus(UUID facilityId, UUID wardId, String status);

    Optional<BedEntity> findByIdAndFacilityId(UUID id, UUID facilityId);
}
