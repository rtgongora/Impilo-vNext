package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgClaimSubmissionRepository extends JpaRepository<OrgClaimSubmissionEntity, UUID> {

    List<OrgClaimSubmissionEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /**
     * Resolves a claim from the workflow-service instance id carried on the
     * adjudication decision event — the authoritative claim ↔ decision join.
     */
    Optional<OrgClaimSubmissionEntity> findByWorkflowInstanceId(UUID workflowInstanceId);
}
