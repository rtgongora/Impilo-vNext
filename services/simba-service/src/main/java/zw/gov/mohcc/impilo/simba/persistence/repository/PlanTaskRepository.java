package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanTaskEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanTaskRepository extends JpaRepository<PlanTaskEntity, Long> {

    List<PlanTaskEntity> findByPlanIdOrderByStageAscSeqAsc(UUID planId);
}
