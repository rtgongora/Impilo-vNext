package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MultiSiteGroupMemberRepository extends JpaRepository<MultiSiteGroupMemberEntity, UUID> {

    List<MultiSiteGroupMemberEntity> findByTenantIdAndGroupIdAndStatus(UUID tenantId, UUID groupId, String status);

    List<MultiSiteGroupMemberEntity> findByTenantIdAndMemberTargetTypeAndMemberTargetIdAndStatus(
            UUID tenantId, String memberTargetType, String memberTargetId, String status);
}
