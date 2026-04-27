package zw.gov.mohcc.impilo.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Only enable the Kafka {@link org.springframework.kafka.annotation.KafkaListener} infrastructure when
 * the Mvumo preference consumer is turned on; keeps tests and local runs without a broker unblocked.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "impilo.kafka.mvumo.enabled", havingValue = "true")
public class MvumoKafkaBootstrapConfig {}
