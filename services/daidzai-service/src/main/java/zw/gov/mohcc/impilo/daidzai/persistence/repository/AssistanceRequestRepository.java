package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistanceRequestRepository extends JpaRepository<AssistanceRequestEntity, UUID> {

    Optional<AssistanceRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AssistanceRequestEntity> findByTenantIdAndVerificationStatusOrderByCreatedAtDesc(
            UUID tenantId, String verificationStatus);

    List<AssistanceRequestEntity> findByTenantIdAndRequesterActorIdOrderByCreatedAtDesc(
            UUID tenantId, String requesterActorId);

    Optional<AssistanceRequestEntity> findByContributionRequestId(UUID contributionRequestId);

    boolean existsByTenantIdAndAssistanceReference(UUID tenantId, String assistanceReference);
}
