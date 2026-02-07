package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityIdentifierRepository extends JpaRepository<FacilityIdentifierEntity, Long> {

    List<FacilityIdentifierEntity> findByFacilityId(Long facilityId);

    Optional<FacilityIdentifierEntity> findBySystemAndValue(String system, String value);

    List<FacilityIdentifierEntity> findByFacilityIdAndActiveTrue(Long facilityId);
}
