package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.ConversationLinkEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationLinkRepository extends JpaRepository<ConversationLinkEntity, UUID> {
    List<ConversationLinkEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
