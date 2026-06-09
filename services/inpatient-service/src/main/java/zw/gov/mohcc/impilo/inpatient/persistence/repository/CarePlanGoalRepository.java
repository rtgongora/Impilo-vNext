package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.CarePlanGoalEntity;

import java.util.List;
import java.util.UUID;

public interface CarePlanGoalRepository extends JpaRepository<CarePlanGoalEntity, UUID> {
    List<CarePlanGoalEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);
}
