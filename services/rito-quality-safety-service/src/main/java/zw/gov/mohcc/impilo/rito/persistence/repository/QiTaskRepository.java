package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiTaskEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface QiTaskRepository extends JpaRepository<QiTaskEntity, UUID> {

    List<QiTaskEntity> findByQiPlanIdOrderByOrdinalAsc(UUID qiPlanId);
}
