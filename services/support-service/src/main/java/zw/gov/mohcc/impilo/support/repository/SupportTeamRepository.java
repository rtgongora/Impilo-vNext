package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.SupportTeamEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTeamRepository extends JpaRepository<SupportTeamEntity, UUID> {
    Optional<SupportTeamEntity> findByTenantIdAndTeamCode(UUID tenantId, String teamCode);
    List<SupportTeamEntity> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
}
