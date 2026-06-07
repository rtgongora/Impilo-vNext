package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.HaemovigilanceCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HaemovigilanceCaseRepository extends JpaRepository<HaemovigilanceCaseEntity, Long> {
    Optional<HaemovigilanceCaseEntity> findByCaseIdAndTenantId(UUID caseId, UUID tenantId);
    List<HaemovigilanceCaseEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
