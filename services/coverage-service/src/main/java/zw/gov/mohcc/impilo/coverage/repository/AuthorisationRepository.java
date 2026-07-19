package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorisationRepository extends JpaRepository<AuthorisationEntity, UUID> {
    List<AuthorisationEntity> findByTenantIdAndMemberCpidOrderByCreatedAtDesc(UUID tenantId, String memberCpid);
    List<AuthorisationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    Optional<AuthorisationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
