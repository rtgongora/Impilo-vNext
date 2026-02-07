package zw.gov.mohcc.impilo.tshepo.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
public class KafkaConfig {
    // Kafka consumer configuration is driven by application.yml.
    // Manual ack mode is configured in YAML (listener.ack-mode: manual)
    // to ensure audit events are only committed after successful chain append.
}
