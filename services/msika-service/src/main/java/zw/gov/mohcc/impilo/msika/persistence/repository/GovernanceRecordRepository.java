package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.GovernanceRecordEntity;

import java.util.List;

@Repository
public interface GovernanceRecordRepository extends JpaRepository<GovernanceRecordEntity, String> {
    List<GovernanceRecordEntity> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);
    List<GovernanceRecordEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);
}

