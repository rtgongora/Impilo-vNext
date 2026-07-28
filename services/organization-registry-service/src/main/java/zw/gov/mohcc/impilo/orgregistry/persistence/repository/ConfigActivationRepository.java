package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigActivationEntity;

import java.util.List;
import java.util.UUID;

public interface ConfigActivationRepository extends JpaRepository<ConfigActivationEntity, UUID> {

    List<ConfigActivationEntity> findByTenantIdAndPackIdOrderByActivatedAtDesc(UUID tenantId, UUID packId);
}
