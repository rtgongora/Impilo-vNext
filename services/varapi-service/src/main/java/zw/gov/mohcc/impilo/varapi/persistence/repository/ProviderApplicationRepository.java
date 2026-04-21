package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderApplicationRepository extends JpaRepository<ProviderApplicationEntity, Long> {

    List<ProviderApplicationEntity> findByProviderId(Long providerId);

    List<ProviderApplicationEntity> findByTenantIdAndWorkflowState(UUID tenantId, String workflowState);

    List<ProviderApplicationEntity> findByTenantIdAndApplicationType(UUID tenantId, String applicationType);

    int countByProviderIdAndWorkflowState(Long providerId, String workflowState);
}