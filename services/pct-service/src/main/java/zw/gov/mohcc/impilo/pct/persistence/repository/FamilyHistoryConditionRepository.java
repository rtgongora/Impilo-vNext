package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FamilyHistoryConditionEntity;

import java.util.List;
import java.util.UUID;

public interface FamilyHistoryConditionRepository extends JpaRepository<FamilyHistoryConditionEntity, UUID> {

    List<FamilyHistoryConditionEntity> findByMemberId(UUID memberId);
}
