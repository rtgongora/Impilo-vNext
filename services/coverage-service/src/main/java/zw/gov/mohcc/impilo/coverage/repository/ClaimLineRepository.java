package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.ClaimLineEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimLineRepository extends JpaRepository<ClaimLineEntity, UUID> {
    List<ClaimLineEntity> findByClaimIdOrderByLineNumber(UUID claimId);
}
