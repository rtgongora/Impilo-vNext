package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.RankingSnapshotEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshotEntity, String> {

    Optional<RankingSnapshotEntity> findFirstByRequestIdAndOfferId(String requestId, String offerId);

    List<RankingSnapshotEntity> findByRequestId(String requestId);
}
