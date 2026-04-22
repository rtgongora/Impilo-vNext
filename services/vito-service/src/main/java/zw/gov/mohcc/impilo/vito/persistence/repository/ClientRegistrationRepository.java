package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.ClientRegistrationWorkflowState;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientRegistrationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientRegistrationRepository extends JpaRepository<ClientRegistrationEntity, UUID> {
    List<ClientRegistrationEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientRegistrationEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
    Page<ClientRegistrationEntity> findByTenantIdAndWorkflowState(UUID tenantId,
                                                                  ClientRegistrationWorkflowState workflowState,
                                                                  Pageable pageable);
}
