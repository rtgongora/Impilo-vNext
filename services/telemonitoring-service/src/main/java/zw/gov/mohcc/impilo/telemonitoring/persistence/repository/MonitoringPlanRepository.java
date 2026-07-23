package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoringPlanRepository extends JpaRepository<MonitoringPlanEntity, UUID> {

    Optional<MonitoringPlanEntity> findByOrosOrderId(String orosOrderId);

    List<MonitoringPlanEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
}
