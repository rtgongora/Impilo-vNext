package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReproductiveIntentionRepository
        extends JpaRepository<ReproductiveIntentionEntity, UUID> {

    @Query("SELECT i FROM ReproductiveIntentionEntity i "
            + "WHERE i.tenantId = :tenantId AND i.subjectCpid = :subjectCpid "
            + "AND i.status = 'CURRENT'")
    Optional<ReproductiveIntentionEntity> findCurrent(@Param("tenantId") UUID tenantId,
                                                      @Param("subjectCpid") String subjectCpid);

    @Query("SELECT i FROM ReproductiveIntentionEntity i "
            + "WHERE i.tenantId = :tenantId AND i.subjectCpid = :subjectCpid "
            + "ORDER BY i.recordedAt DESC")
    List<ReproductiveIntentionEntity> history(@Param("tenantId") UUID tenantId,
                                              @Param("subjectCpid") String subjectCpid);

    Optional<ReproductiveIntentionEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String offlineId);
}
