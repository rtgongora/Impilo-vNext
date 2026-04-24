package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MultiSiteGroupRepository extends JpaRepository<MultiSiteGroupEntity, UUID> {

    Optional<MultiSiteGroupEntity> findByTenantIdAndCode(UUID tenantId, String code);
}
