package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.MergeHistoryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MergeHistoryRepository extends JpaRepository<MergeHistoryEntity, Long> {

    List<MergeHistoryEntity> findByTenantIdAndSurvivorCrid(UUID tenantId, UUID survivorCrid);

    Optional<MergeHistoryEntity> findByTenantIdAndMergedCrid(UUID tenantId, UUID mergedCrid);

    Page<MergeHistoryEntity> findByTenantIdAndReversibleTrueAndReversedAtIsNull(UUID tenantId, Pageable pageable);
}
