package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface WardRepository extends JpaRepository<WardEntity, UUID> {

    List<WardEntity> findByFacilityIdOrderByNameAsc(UUID facilityId);
}
