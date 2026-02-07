package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;

import java.util.List;

@Repository
public interface FacilityContactRepository extends JpaRepository<FacilityContactEntity, Long> {

    List<FacilityContactEntity> findByFacilityId(Long facilityId);

    List<FacilityContactEntity> findByFacilityIdAndContactType(Long facilityId, String contactType);
}
