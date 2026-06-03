package zw.gov.mohcc.impilo.butano.config;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.config.HapiJpaConfig;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Core HAPI FHIR JPA Server configuration for BUTANO.
 *
 * <p>Imports {@link JpaR4Config} and {@link HapiJpaConfig} so R4 DAO/providers and
 * {@code IResourceSupportedSvc} / {@link ca.uhn.fhir.rest.server.provider.ResourceProviderFactory}
 * are registered. Storage settings are customized for national SHR requirements.</p>
 */
@Configuration
@Import({JpaR4Config.class, HapiJpaConfig.class})
public class FhirServerConfig {

    @Value("${hapi.fhir.default-page-size:20}")
    private int defaultPageSize;

    @Value("${hapi.fhir.max-page-size:200}")
    private int maxPageSize;

    @Value("${hapi.fhir.allow-multiple-delete:false}")
    private boolean allowMultipleDelete;

    @Value("${hapi.fhir.allow-cascading-deletes:false}")
    private boolean allowCascadingDeletes;

    @Value("${hapi.fhir.allow-contains-searches:true}")
    private boolean allowContainsSearches;

    @Value("${hapi.fhir.allow-external-references:false}")
    private boolean allowExternalReferences;

    @Value("${hapi.fhir.expunge-enabled:false}")
    private boolean expungeEnabled;

    @Value("${hapi.fhir.enforce-referential-integrity-on-write:true}")
    private boolean enforceReferentialIntegrityOnWrite;

    @Value("${hapi.fhir.enforce-referential-integrity-on-delete:false}")
    private boolean enforceReferentialIntegrityOnDelete;

    @Bean
    public JpaStorageSettings jpaStorageSettings() {
        JpaStorageSettings settings = new JpaStorageSettings();

        settings.setDefaultSearchParamsCanBeOverridden(true);
        settings.setFetchSizeDefaultMaximum(maxPageSize);

        settings.setEnforceReferentialIntegrityOnWrite(enforceReferentialIntegrityOnWrite);
        settings.setEnforceReferentialIntegrityOnDelete(enforceReferentialIntegrityOnDelete);

        settings.setAllowMultipleDelete(allowMultipleDelete);
        settings.setExpungeEnabled(expungeEnabled);
        settings.setAllowContainsSearches(allowContainsSearches);
        settings.setAllowExternalReferences(allowExternalReferences);
        settings.setDeleteEnabled(true);

        settings.setSearchPreFetchThresholds(java.util.List.of(20, 50, 200));
        settings.setMaximumSearchResultCountInTransaction(maxPageSize);

        settings.setStoreResourceInHSearchIndex(false);
        settings.setAutoVersionReferenceAtPaths();

        return settings;
    }
}
