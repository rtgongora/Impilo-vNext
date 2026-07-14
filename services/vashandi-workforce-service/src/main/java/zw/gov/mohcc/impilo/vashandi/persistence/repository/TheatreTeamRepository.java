package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TheatreTeamRepository extends JpaRepository<TheatreTeamEntity, UUID> {

    Optional<TheatreTeamEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TheatreTeamEntity> findByTenantIdAndSessionRef(UUID tenantId, UUID sessionRef);
}
