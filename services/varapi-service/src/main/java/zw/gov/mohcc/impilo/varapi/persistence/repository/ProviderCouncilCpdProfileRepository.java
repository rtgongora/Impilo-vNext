package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilCpdProfileEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilCpdProfileRepository extends JpaRepository<ProviderCouncilCpdProfileEntity, Long> {

    List<ProviderCouncilCpdProfileEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);
}
