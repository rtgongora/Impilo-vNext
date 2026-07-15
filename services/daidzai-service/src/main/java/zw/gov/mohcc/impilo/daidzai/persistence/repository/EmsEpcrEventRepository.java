package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmsEpcrEventRepository extends JpaRepository<EmsEpcrEventEntity, UUID> {
    List<EmsEpcrEventEntity> findByEpcrIdOrderByRecordedAtAsc(UUID epcrId);
    List<EmsEpcrEventEntity> findByMissionIdOrderByRecordedAtAsc(UUID missionId);
}
