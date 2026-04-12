package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentPlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlanEntity, Long> {
    List<PaymentPlanEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    Optional<PaymentPlanEntity> findByTenantIdAndPlanId(UUID tenantId, UUID planId);
}
