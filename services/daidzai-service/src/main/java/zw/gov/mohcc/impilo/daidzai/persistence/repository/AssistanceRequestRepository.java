package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** VITO identity repoint (see DaidzaiIntakeRepointHook). Idempotent: a replay matches nothing. */
    @Modifying
    @Query("update AssistanceRequestEntity a set a.subjectCpid = :confirmed "
            + "where a.tenantId = :tenantId and a.subjectCpid = :provisional")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                           @Param("provisional") String provisionalCpid,
                           @Param("confirmed") String confirmedCpid);
}
