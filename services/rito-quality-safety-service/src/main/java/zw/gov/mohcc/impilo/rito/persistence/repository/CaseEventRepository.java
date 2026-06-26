package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseEventRepository extends JpaRepository<CaseEventEntity, UUID> {

    List<CaseEventEntity> findByCaseIdOrderByOccurredAtAsc(UUID caseId);
}
