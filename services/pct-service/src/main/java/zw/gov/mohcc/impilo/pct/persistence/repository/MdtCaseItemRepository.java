package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtCaseItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MdtCaseItemRepository extends JpaRepository<MdtCaseItemEntity, UUID> {
    List<MdtCaseItemEntity> findByMdtSessionIdOrderByOrdinalAsc(UUID mdtSessionId);
    Optional<MdtCaseItemEntity> findByTenantIdAndCaseItemId(UUID tenantId, UUID caseItemId);
    int countByMdtSessionId(UUID mdtSessionId);
}
