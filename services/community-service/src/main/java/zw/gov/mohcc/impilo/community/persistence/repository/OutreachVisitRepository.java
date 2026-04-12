package zw.gov.mohcc.impilo.community.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.persistence.entity.OutreachVisitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachVisitRepository extends JpaRepository<OutreachVisitEntity, Long> {

    Optional<OutreachVisitEntity> findByVisitId(UUID visitId);

    List<OutreachVisitEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<OutreachVisitEntity> findByTenantIdAndUnitIdOrderByCreatedAtDesc(UUID tenantId, UUID unitId);
}
