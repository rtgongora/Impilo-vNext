package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseMessageEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseMessageRepository extends JpaRepository<CaseMessageEntity, UUID> {

    List<CaseMessageEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
