package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.ComplaintStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.EnvironmentalComplaintEntity;

@Repository
public interface EnvironmentalComplaintRepository extends JpaRepository<EnvironmentalComplaintEntity, Long> {

    List<EnvironmentalComplaintEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatusNot(UUID tenantId, ComplaintStatus status);
}
