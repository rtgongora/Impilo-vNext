package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CarePlanEntity;

import java.util.List;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlanEntity, UUID> {

    List<CarePlanEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
