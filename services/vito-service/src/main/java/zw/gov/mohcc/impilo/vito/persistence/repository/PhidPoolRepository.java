package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhidPoolRepository extends JpaRepository<PhidPoolEntity, Long> {

    List<PhidPoolEntity> findByFacilityIdAndStatus(String facilityId, String status, Pageable pageable);

    Optional<PhidPoolEntity> findByHealthId(UUID healthId);

    Optional<PhidPoolEntity> findByPhid(String phid);

    long countByFacilityIdAndStatus(String facilityId, String status);
}
