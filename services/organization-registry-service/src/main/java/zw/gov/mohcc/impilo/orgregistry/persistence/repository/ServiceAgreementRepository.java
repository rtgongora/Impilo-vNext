package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceAgreementEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAgreementRepository extends JpaRepository<ServiceAgreementEntity, UUID> {

    List<ServiceAgreementEntity> findByOrganisationIdAndStatus(UUID organisationId, String status);

    Optional<ServiceAgreementEntity> findFirstByTrustDomainIdAndOrganisationIdAndStatus(
            UUID trustDomainId, UUID organisationId, String status);

    Page<ServiceAgreementEntity> findByTrustDomainId(UUID trustDomainId, Pageable pageable);
}
