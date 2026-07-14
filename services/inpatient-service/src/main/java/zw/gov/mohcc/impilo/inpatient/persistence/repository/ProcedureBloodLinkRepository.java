package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureBloodLinkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureBloodLinkRepository extends JpaRepository<ProcedureBloodLinkEntity, UUID> {
    List<ProcedureBloodLinkEntity> findByEpisodeIdOrderByCreatedAtAsc(UUID episodeId);
    Optional<ProcedureBloodLinkEntity> findByTenantIdAndEpisodeIdAndKindAndSeq(UUID tenantId, UUID episodeId, String kind, int seq);
    Optional<ProcedureBloodLinkEntity> findFirstByEpisodeIdOrderByCreatedAtDesc(UUID episodeId);
    List<ProcedureBloodLinkEntity> findByTenantIdAndStatusNotIn(UUID tenantId, List<String> statuses);
    List<ProcedureBloodLinkEntity> findByStatusNotIn(List<String> statuses);
}
