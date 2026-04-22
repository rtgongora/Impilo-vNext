package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.indawo.domain.InspectionChecklistTemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionChecklistTemplateRepository extends JpaRepository<InspectionChecklistTemplateEntity, UUID> {

    UUID SYSTEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Query("""
            SELECT t
            FROM InspectionChecklistTemplateEntity t
            WHERE (t.tenantId = :tenantId OR t.tenantId = :systemTenant)
              AND t.activeFlag = true
              AND t.applicableInspectionType = :inspectionType
              AND (:siteCategory IS NULL OR t.applicableSiteCategory IS NULL OR t.applicableSiteCategory = :siteCategory)
            ORDER BY t.code, t.version DESC
            """)
    List<InspectionChecklistTemplateEntity> listApplicable(
            @Param("tenantId") UUID tenantId,
            @Param("systemTenant") UUID systemTenant,
            @Param("inspectionType") String inspectionType,
            @Param("siteCategory") String siteCategory);

    Optional<InspectionChecklistTemplateEntity> findFirstByTenantIdAndCodeAndActiveFlagIsTrueOrderByVersionDesc(UUID tenantId, String code);
}

