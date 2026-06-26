package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.FollowUpRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowUpRequestRepository extends JpaRepository<FollowUpRequestEntity, UUID> {
    List<FollowUpRequestEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    Optional<FollowUpRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
