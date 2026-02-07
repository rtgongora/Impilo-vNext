package zw.gov.mohcc.impilo.zibo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;

/**
 * Entry point for the ZIBO (National Terminology, Definitions &amp; Classification) service.
 *
 * <p>Manages FHIR terminology artifacts (CodeSystem, ValueSet, ConceptMap,
 * NamingSystem, StructureDefinition, ImplementationGuide), terminology packs,
 * scope-based assignments, concept mapping lookups, and terminology validation
 * for the Impilo platform.</p>
 *
 * <p>Publishes domain events via the transactional outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ZiboProperties.class)
public class ZiboApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiboApplication.class, args);
    }
}
