package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationContributorInviteEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationContributorInviteRepository
        extends JpaRepository<ApplicationContributorInviteEntity, Long> {

    List<ApplicationContributorInviteEntity> findByTenantIdAndApplicationId(
            UUID tenantId, Long applicationId);

    Optional<ApplicationContributorInviteEntity> findByTenantIdAndId(UUID tenantId, Long id);

    Optional<ApplicationContributorInviteEntity> findByTokenHash(String tokenHash);
}
