package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinitionEntity, UUID> {

    Optional<RoleDefinitionEntity> findByTenantIdAndRoleCode(UUID tenantId, String roleCode);
}
