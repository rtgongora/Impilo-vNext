package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AffiliationRepository extends JpaRepository<AffiliationEntity, UUID> {

    List<AffiliationEntity> findByOrganizationId(UUID organizationId);

    Optional<AffiliationEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
