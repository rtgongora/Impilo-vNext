package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;

import java.util.List;
import java.util.UUID;

public interface OrgClaimSubmissionRepository extends JpaRepository<OrgClaimSubmissionEntity, UUID> {

    List<OrgClaimSubmissionEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
