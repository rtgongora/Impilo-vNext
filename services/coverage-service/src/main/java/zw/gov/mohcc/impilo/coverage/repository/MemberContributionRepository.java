package zw.gov.mohcc.impilo.coverage.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.MemberContributionEntity;

@Repository
public interface MemberContributionRepository extends JpaRepository<MemberContributionEntity, UUID> {

    List<MemberContributionEntity> findByTenantIdAndMemberIdIn(UUID tenantId, Collection<UUID> memberIds);
}
