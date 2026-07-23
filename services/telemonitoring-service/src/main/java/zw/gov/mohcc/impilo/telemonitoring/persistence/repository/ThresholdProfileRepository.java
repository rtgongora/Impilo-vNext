package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThresholdProfileRepository extends JpaRepository<ThresholdProfileEntity, UUID> {

    List<ThresholdProfileEntity> findByPlanIdOrderByVersionDesc(UUID planId);

    Optional<ThresholdProfileEntity> findFirstByPlanIdOrderByVersionDesc(UUID planId);
}
