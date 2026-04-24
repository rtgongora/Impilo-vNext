package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderComplianceActionEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderComplianceActionRepository extends JpaRepository<ProviderComplianceActionEntity, Long> {

    List<ProviderComplianceActionEntity> findByProviderId(Long providerId);

    Optional<ProviderComplianceActionEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<ProviderComplianceActionEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<ProviderComplianceActionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderComplianceActionEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderComplianceActionEntity> findByTenantIdAndProviderIdAndStatus(UUID tenantId, Long providerId, String status);

    List<ProviderComplianceActionEntity> findByTenantIdAndActionType(UUID tenantId, String actionType);

    List<ProviderComplianceActionEntity> findByStatusAndDueDateBefore(String status, LocalDate dueDate);

    List<ProviderComplianceActionEntity> findByTenantIdAndStatusAndDueDateBefore(UUID tenantId, String status, LocalDate dueDate);

    List<ProviderComplianceActionEntity> findByProviderIdAndStatusAndDueDateBefore(Long providerId, String status, LocalDate dueDate);

    List<ProviderComplianceActionEntity> findByTenantIdAndProviderIdAndStatusAndDueDateBefore(
            UUID tenantId,
            Long providerId,
            String status,
            LocalDate dueDate);
}
