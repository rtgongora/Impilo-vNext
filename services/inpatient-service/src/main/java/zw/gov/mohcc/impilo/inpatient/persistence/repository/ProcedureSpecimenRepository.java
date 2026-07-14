package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureSpecimenRepository extends JpaRepository<ProcedureSpecimenEntity, UUID> {
    List<ProcedureSpecimenEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);
    Optional<ProcedureSpecimenEntity> findByTenantIdAndEpisodeIdAndKindAndSeq(UUID tenantId, UUID episodeId, String kind, int seq);
    Optional<ProcedureSpecimenEntity> findByTenantIdAndOrosSpecimenId(UUID tenantId, String orosSpecimenId);
    List<ProcedureSpecimenEntity> findByTenantIdAndStatusNotIn(UUID tenantId, List<String> statuses);
    int countByEpisodeId(UUID episodeId);
    List<ProcedureSpecimenEntity> findByStatusNotIn(List<String> statuses);
}
