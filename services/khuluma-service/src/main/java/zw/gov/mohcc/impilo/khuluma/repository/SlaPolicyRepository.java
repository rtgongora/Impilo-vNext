package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.SlaPolicyEntity;

import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicyEntity, UUID> {

    Optional<SlaPolicyEntity> findByTenantIdAndPriorityAndActiveTrue(UUID tenantId, String priority);
}
