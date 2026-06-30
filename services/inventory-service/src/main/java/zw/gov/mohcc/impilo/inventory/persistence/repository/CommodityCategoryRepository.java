package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Dura commodity categories.
 */
@Repository
public interface CommodityCategoryRepository extends JpaRepository<CommodityCategoryEntity, UUID> {

    Optional<CommodityCategoryEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<CommodityCategoryEntity> findByTenantIdOrderByName(UUID tenantId);

    List<CommodityCategoryEntity> findByTenantIdAndProgrammeAreaOrderByName(UUID tenantId, String programmeArea);

    List<CommodityCategoryEntity> findByTenantIdAndParentCategoryId(UUID tenantId, UUID parentCategoryId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
