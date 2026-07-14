package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureTransportEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureTransportRepository extends JpaRepository<ProcedureTransportEntity, UUID> {
    List<ProcedureTransportEntity> findByEpisodeIdOrderByCreatedAtAsc(UUID episodeId);
    Optional<ProcedureTransportEntity> findByTenantIdAndEpisodeIdAndLegAndSeq(UUID tenantId, UUID episodeId, String leg, int seq);
    List<ProcedureTransportEntity> findByTenantIdAndStatusNotIn(UUID tenantId, List<String> statuses);
    int countByEpisodeIdAndLeg(UUID episodeId, String leg);
    List<ProcedureTransportEntity> findByStatusNotIn(List<String> statuses);
}
