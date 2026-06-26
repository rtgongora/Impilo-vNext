package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseLinkEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseLinkRepository extends JpaRepository<CaseLinkEntity, UUID> {

    List<CaseLinkEntity> findByCaseId(UUID caseId);

    List<CaseLinkEntity> findByTenantIdAndLinkTypeAndTargetRef(UUID tenantId, String linkType, String targetRef);
}
