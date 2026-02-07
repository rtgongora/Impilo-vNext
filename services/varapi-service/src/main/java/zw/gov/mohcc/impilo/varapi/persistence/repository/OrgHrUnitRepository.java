package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.OrgHrUnitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgHrUnitRepository extends JpaRepository<OrgHrUnitEntity, Long> {

    List<OrgHrUnitEntity> findByTenantId(UUID tenantId);

    Optional<OrgHrUnitEntity> findByOrgCode(String orgCode);
}
