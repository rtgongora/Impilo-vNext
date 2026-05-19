package zw.gov.mohcc.impilo.msikaapps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.msikaapps.domain.*;

import java.util.List;
import java.util.Optional;

public final class MsikaAppsRepositories {
    private MsikaAppsRepositories() {}

    public interface PublisherRepository extends JpaRepository<PublisherEntity, String> {
        List<PublisherEntity> findAllByOrderByNameAsc();
    }

    public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItemEntity, String> {
        Optional<MarketplaceItemEntity> findByItemCode(String itemCode);
        List<MarketplaceItemEntity> findByVisibilityInAndApprovalStatusOrderByNameAsc(List<String> visibilities, String approvalStatus);

        @Query("SELECT m FROM MarketplaceItemEntity m " +
               "WHERE (:type IS NULL OR m.type = :type) " +
               "  AND (:category IS NULL OR m.category = :category) " +
               "  AND (:publisherId IS NULL OR m.publisherId = :publisherId) " +
               "ORDER BY m.name ASC")
        List<MarketplaceItemEntity> search(@Param("type") String type,
                                           @Param("category") String category,
                                           @Param("publisherId") String publisherId);
    }

    public interface ActivationRequestRepository extends JpaRepository<ActivationRequestEntity, String> {
        List<ActivationRequestEntity> findByRequesterTenantIdOrderByCreatedAtDesc(String tenantId);
        List<ActivationRequestEntity> findByStatusOrderByCreatedAtAsc(String status);
        List<ActivationRequestEntity> findByItemIdOrderByCreatedAtDesc(String itemId);
    }

    public interface InstallationRepository extends JpaRepository<InstallationEntity, String> {
        List<InstallationEntity> findByTenantIdOrderByInstalledAtDesc(String tenantId);
        List<InstallationEntity> findByTenantIdAndLifecycleOrderByInstalledAtDesc(String tenantId, String lifecycle);
        List<InstallationEntity> findByItemIdAndTenantIdOrderByInstalledAtDesc(String itemId, String tenantId);
    }

    public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
        List<AuditEventEntity> findByTenantIdOrderByOccurredAtDesc(String tenantId);
        List<AuditEventEntity> findByItemIdOrderByOccurredAtDesc(String itemId);
    }
}
