package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.DeliveryMissionEntity;

import java.util.List;

@Repository
public interface DeliveryMissionRepository extends JpaRepository<DeliveryMissionEntity, String> {
    List<DeliveryMissionEntity> findByLegIdOrderByCreatedAtDesc(String legId);
}

