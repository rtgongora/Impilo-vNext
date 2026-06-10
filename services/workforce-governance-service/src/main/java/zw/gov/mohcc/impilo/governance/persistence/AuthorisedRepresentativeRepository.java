package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthorisedRepresentativeRepository extends JpaRepository<AuthorisedRepresentativeEntity, UUID> {
    List<AuthorisedRepresentativeEntity> findByTenantIdAndOrganisationIdOrderByUpdatedAtDesc(UUID tenantId, UUID organisationId);
}
