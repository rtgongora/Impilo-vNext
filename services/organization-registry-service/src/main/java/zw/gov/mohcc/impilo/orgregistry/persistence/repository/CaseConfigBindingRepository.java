package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.CaseConfigBindingEntity;

import java.util.Optional;
import java.util.UUID;

public interface CaseConfigBindingRepository extends JpaRepository<CaseConfigBindingEntity, UUID> {

    Optional<CaseConfigBindingEntity> findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
            UUID tenantId, String caseSystem, String caseType, String caseRef);

    Optional<CaseConfigBindingEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
