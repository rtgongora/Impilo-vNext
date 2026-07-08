package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgInvitationRepository extends JpaRepository<OrgInvitationEntity, UUID> {

    List<OrgInvitationEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<OrgInvitationEntity> findByTokenAndTenantId(String token, UUID tenantId);
}
