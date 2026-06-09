package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.CarePlanInterventionEntity;

import java.util.List;
import java.util.UUID;

public interface CarePlanInterventionRepository extends JpaRepository<CarePlanInterventionEntity, UUID> {
    List<CarePlanInterventionEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);
}
