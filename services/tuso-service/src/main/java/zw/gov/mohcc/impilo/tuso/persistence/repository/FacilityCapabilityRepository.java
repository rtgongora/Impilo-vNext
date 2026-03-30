package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;

import java.util.List;

@Repository
public interface FacilityCapabilityRepository extends JpaRepository<FacilityCapabilityEntity, Long> {

    List<FacilityCapabilityEntity> findByFacility_IdAndActiveTrue(Long facilityId);

    List<FacilityCapabilityEntity> findByFacility_IdAndCapabilityType(Long facilityId, String capabilityType);

    List<FacilityCapabilityEntity> findByCapabilityCodeAndCapabilityType(String capabilityCode, String capabilityType);
}
