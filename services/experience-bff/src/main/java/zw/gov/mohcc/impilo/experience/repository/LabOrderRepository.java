package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.LabOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {

    List<LabOrder> findByEncounterId(UUID encounterId);

    Page<LabOrder> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);

    Page<LabOrder> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Optional<LabOrder> findByIdAndTenantId(UUID id, String tenantId);
}
