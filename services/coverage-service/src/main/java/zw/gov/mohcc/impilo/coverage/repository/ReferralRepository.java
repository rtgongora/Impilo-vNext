package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.ReferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
    List<ReferralEntity> findByTenantIdAndMemberCpid(UUID tenantId, String memberCpid);
    Optional<ReferralEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
