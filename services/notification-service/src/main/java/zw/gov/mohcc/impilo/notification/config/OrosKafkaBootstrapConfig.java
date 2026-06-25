package zw.gov.mohcc.impilo.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Enables the Kafka listener infrastructure for the OROS critical-result consumer when
 * {@code impilo.kafka.oros.enabled=true}. Kept independent of the Mvumo toggle so either consumer
 * can run alone; Spring de-duplicates the {@link EnableKafka} bootstrap beans if both are active.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "impilo.kafka.oros.enabled", havingValue = "true")
public class OrosKafkaBootstrapConfig {}
