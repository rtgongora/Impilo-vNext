package zw.gov.mohcc.impilo.butano.config;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.config.Batch2SupportConfig;
import ca.uhn.fhir.jpa.config.HapiJpaConfig;
import ca.uhn.fhir.jpa.config.JpaConfig;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil;
import ca.uhn.fhir.jpa.dao.ThreadPoolFactory;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * Core HAPI FHIR JPA Server configuration for BUTANO.
 *
 * <p>Imports {@link JpaR4Config} and {@link HapiJpaConfig} so R4 DAO/providers and
 * {@code IResourceSupportedSvc} / {@link ca.uhn.fhir.rest.server.provider.ResourceProviderFactory}
 * are registered. Storage settings are customized for national SHR requirements.</p>
 */
@Configuration
@Import({JpaConfig.class, JpaR4Config.class, HapiJpaConfig.class,
        JpaBatch2Config.class, Batch2SupportConfig.class, Batch2JobsConfig.class,
        SubscriptionChannelConfig.class})
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
    public PartitionSettings partitionSettings() {
        return new PartitionSettings();
    }

    @Bean
    public ThreadPoolFactory threadPoolFactory() {
        return new ThreadPoolFactory();
    }

    /**
     * HAPI-managed JPA {@link jakarta.persistence.EntityManagerFactory}.
     *
     * <p>HAPI FHIR registers custom Hibernate service contributors (for example
     * {@code ISequenceValueMassager}) and id generators that the standard Spring Boot
     * auto-configured factory does not provide. The factory must therefore be built
     * through {@link HapiEntityManagerFactoryUtil}. It scans both the HAPI entity
     * packages and BUTANO's own entities so the single persistence unit serves the
     * FHIR DAOs and the service's domain repositories.</p>
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            ConfigurableListableBeanFactory beanFactory,
            FhirContext fhirContext,
            DataSource dataSource,
            JpaStorageSettings storageSettings) {
        LocalContainerEntityManagerFactoryBean emf =
                HapiEntityManagerFactoryUtil.newEntityManagerFactory(beanFactory, fhirContext, storageSettings);
        emf.setPersistenceUnitName("HAPI_PU");
        emf.setDataSource(dataSource);
        emf.setPackagesToScan(
                "ca.uhn.fhir.jpa.model.entity",
                "ca.uhn.fhir.jpa.entity",
                "zw.gov.mohcc.impilo.butano.persistence.entity");
        emf.setJpaProperties(hibernateProperties());
        return emf;
    }

    private Properties hibernateProperties() {
        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // HAPI owns the HFJ_* schema; let Hibernate create/extend it while Flyway
        // continues to manage BUTANO's own tables.
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.format_sql", "false");
        props.put("hibernate.jdbc.batch_size", "20");
        props.put("hibernate.order_inserts", "true");
        props.put("hibernate.order_updates", "true");
        props.put("hibernate.search.enabled", "false");
        return props;
    }

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
