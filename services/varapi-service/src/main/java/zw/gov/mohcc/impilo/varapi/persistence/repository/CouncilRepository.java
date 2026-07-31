package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouncilRepository extends JpaRepository<CouncilEntity, Long> {

    Optional<CouncilEntity> findByCouncilCode(String councilCode);

    List<CouncilEntity> findByTenantId(UUID tenantId);

    List<CouncilEntity> findByTenantIdAndCouncilType(UUID tenantId, String councilType);

    Optional<CouncilEntity> findByIdAndTenantId(Long id, UUID tenantId);

    /**
     * The council regulated by an org-registry organisation (ROM R1). Used to turn a configuration
     * event, which names an organisation, into the council whose registers it governs.
     */
    Optional<CouncilEntity> findByTenantIdAndOrgRegistryOrgId(UUID tenantId, UUID orgRegistryOrgId);
}
