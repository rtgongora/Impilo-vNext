package zw.gov.mohcc.impilo.community.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.persistence.entity.CommunityUnitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityUnitRepository extends JpaRepository<CommunityUnitEntity, Long> {

    Optional<CommunityUnitEntity> findByUnitId(UUID unitId);

    List<CommunityUnitEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
