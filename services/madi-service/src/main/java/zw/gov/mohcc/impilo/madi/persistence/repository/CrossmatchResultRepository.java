package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.CrossmatchResultEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrossmatchResultRepository extends JpaRepository<CrossmatchResultEntity, Long> {
    Optional<CrossmatchResultEntity> findByResultIdAndTenantId(UUID resultId, UUID tenantId);
    List<CrossmatchResultEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
