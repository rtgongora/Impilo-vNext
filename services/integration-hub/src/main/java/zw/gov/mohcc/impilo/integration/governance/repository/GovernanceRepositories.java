package zw.gov.mohcc.impilo.integration.governance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import zw.gov.mohcc.impilo.integration.governance.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repositories for the integration governance plane.
 * Bundled in a single file because each repository surface is small and
 * sharing the file makes future schema discovery easier.
 */
public final class GovernanceRepositories {

    private GovernanceRepositories() {
    }

    public interface ExternalApplicationRepository
            extends JpaRepository<ExternalApplicationEntity, String> {
        List<ExternalApplicationEntity> findByTenantIdOrderByRegisteredAtDesc(String tenantId);
        Optional<ExternalApplicationEntity> findByTenantIdAndAppCode(String tenantId, String appCode);
        List<ExternalApplicationEntity> findByStatus(String status);
        List<ExternalApplicationEntity> findByIntegrationCategory(String integrationCategory);
    }

    public interface IntegrationContractRepository
            extends JpaRepository<IntegrationContractEntity, String> {
        List<IntegrationContractEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
        List<IntegrationContractEntity> findByExternalAppIdOrderByCreatedAtDesc(String externalAppId);
        Optional<IntegrationContractEntity> findByExternalAppIdAndContractCodeAndVersion(
                String externalAppId, String contractCode, String version);
    }

    public interface ServiceToServiceContractRepository
            extends JpaRepository<ServiceToServiceContractEntity, String> {
        List<ServiceToServiceContractEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
        List<ServiceToServiceContractEntity> findByCallerServiceIdOrderByCreatedAtDesc(String callerServiceId);
        List<ServiceToServiceContractEntity> findByCalleeServiceIdOrderByCreatedAtDesc(String calleeServiceId);
        Optional<ServiceToServiceContractEntity> findByCallerServiceIdAndCalleeServiceIdAndContractVersion(
                String callerServiceId, String calleeServiceId, String contractVersion);
    }

    public interface WebhookSubscriptionRepository
            extends JpaRepository<WebhookSubscriptionEntity, String> {
        List<WebhookSubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
        List<WebhookSubscriptionEntity> findByExternalAppIdOrderByCreatedAtDesc(String externalAppId);
        List<WebhookSubscriptionEntity> findByActiveTrueOrderByCreatedAtDesc();
    }

    public interface ExternalEventSubscriptionRepository
            extends JpaRepository<ExternalEventSubscriptionEntity, String> {
        List<ExternalEventSubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
        List<ExternalEventSubscriptionEntity> findByExternalAppId(String externalAppId);
        List<ExternalEventSubscriptionEntity> findByEventTopicAndStatus(String eventTopic, String status);
    }

    public interface EventCatalogueRepository
            extends JpaRepository<EventCatalogueEntryEntity, String> {
        Optional<EventCatalogueEntryEntity> findByTopic(String topic);
        List<EventCatalogueEntryEntity> findByClassificationOrderByTopicAsc(String classification);
        List<EventCatalogueEntryEntity> findByOwnerServiceIdOrderByTopicAsc(String ownerServiceId);
    }

    @NoRepositoryBean
    public interface MarkerOnly {}
}
