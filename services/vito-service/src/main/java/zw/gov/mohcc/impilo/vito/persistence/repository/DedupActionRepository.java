package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupActionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DedupActionRepository extends JpaRepository<DedupActionEntity, Long> {

    List<DedupActionEntity> findByCaseIdOrderByCreatedAtAsc(Long caseId);

    Page<DedupActionEntity> findByTenantIdAndActorId(UUID tenantId, String actorId, Pageable pageable);
}
