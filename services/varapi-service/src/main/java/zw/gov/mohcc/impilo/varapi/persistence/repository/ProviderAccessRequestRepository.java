package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderAccessRequestRepository
        extends JpaRepository<ProviderAccessRequestEntity, Long> {

    List<ProviderAccessRequestEntity> findByTenantIdAndApplicantHealthIdOrderByCreatedAtDescIdDesc(
            UUID tenantId, UUID applicantHealthId);

    Optional<ProviderAccessRequestEntity> findByTenantIdAndPublicId(UUID tenantId, String publicId);

    boolean existsByPublicId(String publicId);
}
