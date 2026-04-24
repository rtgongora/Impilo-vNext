package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilDocumentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilDocumentRepository extends JpaRepository<ProviderCouncilDocumentEntity, Long> {

    List<ProviderCouncilDocumentEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);
}
