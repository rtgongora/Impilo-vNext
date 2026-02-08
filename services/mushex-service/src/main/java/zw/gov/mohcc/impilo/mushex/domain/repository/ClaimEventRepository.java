package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;

import java.util.List;

@Repository
public interface ClaimEventRepository extends JpaRepository<ClaimEventEntity, String> {

    List<ClaimEventEntity> findByClaimIdOrderByCreatedAtAsc(String claimId);
}
