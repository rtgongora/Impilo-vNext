package zw.gov.mohcc.impilo.butano.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Core HAPI FHIR JPA Server configuration for BUTANO.
 *
 * <p>Imports {@link JpaR4Config} which brings in all the R4 DAO/provider beans
 * from HAPI FHIR JPA. This class then customizes the storage settings to match
 * the national SHR requirements:</p>
 *
 * <ul>
 *   <li>Referential integrity enforcement on writes</li>
 *   <li>Page size limits to protect against resource exhaustion</li>
 *   <li>Disabled features that are inappropriate for a national SHR (expunge, cascading deletes)</li>
 *   <li>Contains-search enabled for flexible CPID lookups</li>
 * </ul>
 *
 * <p>The HAPI FHIR JPA system uses Hibernate for persistence; table creation
 * and indexing are handled by Hibernate DDL-auto ({@code update} mode in dev).
 * Custom BUTANO tables (tenant registry, reconciliation, outbox) are managed
 * by Flyway migrations.</p>
 */
@Configuration
@Import(JpaR4Config.class)
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

    /**
     * Provides the FHIR R4 context as the primary FhirContext bean.
     *
     * <p>HAPI FHIR JPA internally creates a FhirContext; we mark ours as
     * {@code @Primary} to ensure all components use the R4 context consistently.</p>
     */
    @Bean
    @Primary
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /**
     * Configures HAPI FHIR JPA storage settings.
     *
     * <p>In HAPI FHIR 7.4.0, {@link JpaStorageSettings} replaces the deprecated
     * {@code DaoConfig}. This bean controls all storage-level behaviour including
     * search parameters, referential integrity, and resource limits.</p>
     *
     * <p>National SHR hardening:</p>
     * <ul>
     *   <li>Expunge disabled — clinical data must never be physically deleted</li>
     *   <li>Multiple-delete disabled — prevents accidental mass deletion</li>
     *   <li>Cascading deletes disabled — referential integrity preserved</li>
     *   <li>External references disabled — all references must be local to BUTANO</li>
     *   <li>Contains searches enabled — supports partial CPID lookups</li>
     *   <li>Referential integrity on write — ensures references resolve</li>
     * </ul>
     */
    @Bean
    public JpaStorageSettings jpaStorageSettings() {
        JpaStorageSettings settings = new JpaStorageSettings();

        // Page sizes
        settings.setDefaultSearchParamsCanBeOverridden(true);
        settings.setFetchSizeDefaultMaximum(maxPageSize);

        // Referential integrity
        settings.setEnforceReferentialIntegrityOnWrite(enforceReferentialIntegrityOnWrite);
        settings.setEnforceReferentialIntegrityOnDelete(enforceReferentialIntegrityOnDelete);

        // Feature toggles
        settings.setAllowMultipleDelete(allowMultipleDelete);
        settings.setExpungeEnabled(expungeEnabled);
        settings.setAllowContainsSearches(allowContainsSearches);
        settings.setAllowExternalReferences(allowExternalReferences);
        settings.setDeleteEnabled(true);

        // Search tuning
        settings.setSearchPreFetchThresholds(java.util.List.of(20, 50, 200));
        settings.setMaximumSearchResultCountInTransaction(maxPageSize);

        // Indexing — enable inline resource storage for performance
        settings.setStoreResourceInHSearchIndex(false);

        // Auto-versioning of references (for provenance tracking)
        settings.setAutoVersionReferenceAtPaths();

        return settings;
    }

    /**
     * Provides the DaoRegistry for programmatic access to FHIR DAOs.
     *
     * <p>The DaoRegistry is imported from JpaR4Config but we expose it
     * explicitly so custom controllers and reconciliation services can
     * look up DAOs by resource type.</p>
     */
    @Bean
    @Primary
    public DaoRegistry daoRegistry() {
        return new DaoRegistry();
    }
}
