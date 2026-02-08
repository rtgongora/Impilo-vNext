package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.AdjudicationEntity;

import java.util.Optional;

@Repository
public interface AdjudicationRepository extends JpaRepository<AdjudicationEntity, String> {

    Optional<AdjudicationEntity> findByClaimId(String claimId);
}
