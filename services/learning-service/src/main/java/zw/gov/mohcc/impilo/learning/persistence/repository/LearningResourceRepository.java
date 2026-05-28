package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningResourceEntity;

public interface LearningResourceRepository extends JpaRepository<LearningResourceEntity, UUID> {

    Optional<LearningResourceEntity> findByTenantIdAndResourceCode(UUID tenantId, String resourceCode);

    Optional<LearningResourceEntity> findByTenantIdAndSourceTypeAndExternalRefAndActiveTrue(
            UUID tenantId, String sourceType, String externalRef);

    Optional<LearningResourceEntity> findByTenantIdAndMoodleCmIdAndActiveTrue(UUID tenantId, Long moodleCmId);

    @Query(
            """
            select r from LearningResourceEntity r
            where r.tenantId = :tenantId and r.active = true
            and (
              lower(r.title) like lower(concat('%', :q, '%'))
              or lower(r.resourceCode) like lower(concat('%', :q, '%'))
              or lower(coalesce(r.description, '')) like lower(concat('%', :q, '%'))
              or lower(coalesce(r.workflowTags, '')) like lower(concat('%', :q, '%'))
            )
            """)
    List<LearningResourceEntity> searchActive(
            @Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

    List<LearningResourceEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    List<LearningResourceEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
