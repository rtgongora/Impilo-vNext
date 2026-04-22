package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JurisdictionLinkRepository extends JpaRepository<JurisdictionLinkEntity, UUID> {

    List<JurisdictionLinkEntity> findByTenantIdAndTargetTypeAndTargetIdAndStatus(
            UUID tenantId, String targetType, String targetId, String status);
}
