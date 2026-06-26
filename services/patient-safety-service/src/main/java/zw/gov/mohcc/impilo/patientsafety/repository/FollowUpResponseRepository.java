package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.FollowUpResponseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowUpResponseRepository extends JpaRepository<FollowUpResponseEntity, UUID> {
    List<FollowUpResponseEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    List<FollowUpResponseEntity> findByFollowUpRequestIdOrderByCreatedAtAsc(UUID followUpRequestId);
}
