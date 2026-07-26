package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamVisitEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImamVisitRepository extends JpaRepository<ImamVisitEntity, UUID> {

    List<ImamVisitEntity> findByTenantIdAndImamEpisodeIdOrderByVisitNumberAsc(UUID tenantId, UUID imamEpisodeId);
}
