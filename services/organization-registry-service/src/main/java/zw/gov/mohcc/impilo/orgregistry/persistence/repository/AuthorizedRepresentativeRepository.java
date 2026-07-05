package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizedRepresentativeRepository extends JpaRepository<AuthorizedRepresentativeEntity, UUID> {

    List<AuthorizedRepresentativeEntity> findByOrganizationId(UUID organizationId);

    Optional<AuthorizedRepresentativeEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    boolean existsByOrganizationIdAndVerificationStatus(UUID organizationId, VerificationStatus verificationStatus);
}
