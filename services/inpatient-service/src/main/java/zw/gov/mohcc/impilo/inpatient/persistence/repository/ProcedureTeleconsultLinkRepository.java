package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureTeleconsultLinkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureTeleconsultLinkRepository extends JpaRepository<ProcedureTeleconsultLinkEntity, UUID> {

    List<ProcedureTeleconsultLinkEntity> findByEpisodeIdOrderByCreatedAtAsc(UUID episodeId);

    Optional<ProcedureTeleconsultLinkEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
