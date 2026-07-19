package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.CobDecisionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CobDecisionRepository extends JpaRepository<CobDecisionEntity, UUID> {
    List<CobDecisionEntity> findByTenantIdAndMemberCpidOrderByCreatedAtDesc(UUID tenantId, String memberCpid);
}
