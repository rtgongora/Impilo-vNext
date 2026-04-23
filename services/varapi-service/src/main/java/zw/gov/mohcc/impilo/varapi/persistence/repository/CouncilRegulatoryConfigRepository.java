package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilRegulatoryConfigEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouncilRegulatoryConfigRepository extends JpaRepository<CouncilRegulatoryConfigEntity, Long> {

    Optional<CouncilRegulatoryConfigEntity> findByTenantIdAndCouncil_Id(UUID tenantId, Long councilId);
}
