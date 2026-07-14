package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureCountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureCountRepository extends JpaRepository<ProcedureCountEntity, UUID> {
    List<ProcedureCountEntity> findByEpisodeIdOrderByKindAscSeqAsc(UUID episodeId);
    Optional<ProcedureCountEntity> findByTenantIdAndEpisodeIdAndKindAndSeq(UUID tenantId, UUID episodeId, String kind, int seq);
    int countByEpisodeIdAndKind(UUID episodeId, String kind);
    List<ProcedureCountEntity> findByEpisodeIdAndReconciledFalseAndOverrideFalse(UUID episodeId);
}
