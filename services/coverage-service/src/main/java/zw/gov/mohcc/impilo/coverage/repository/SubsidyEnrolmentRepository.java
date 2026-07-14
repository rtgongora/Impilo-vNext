package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubsidyEnrolmentRepository extends JpaRepository<SubsidyEnrolmentEntity, UUID> {

    List<SubsidyEnrolmentEntity> findByTenantIdAndMemberCpidAndStatus(UUID tenantId, String memberCpid, String status);

    List<SubsidyEnrolmentEntity> findByTenantIdAndMemberCpid(UUID tenantId, String memberCpid);

    Optional<SubsidyEnrolmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
            UUID tenantId, UUID subsidyProgramId, String memberCpid, String status);

    /** Exemption-carrying enrolments for billing-category resolution (newest first). */
    List<SubsidyEnrolmentEntity> findByTenantIdAndMemberCpidAndStatusAndExemptionCategoryIsNotNullOrderByCreatedAtDesc(
            UUID tenantId, String memberCpid, String status);
}
