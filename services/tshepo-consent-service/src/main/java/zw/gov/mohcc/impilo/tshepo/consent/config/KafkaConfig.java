package zw.gov.mohcc.impilo.tshepo.consent.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the trust channel.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, Object> trustChannelProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean(name = "trustChannelKafkaTemplate")
    public KafkaTemplate<String, Object> trustChannelKafkaTemplate(ProducerFactory<String, Object> trustChannelProducerFactory) {
        return new KafkaTemplate<>(trustChannelProducerFactory);
    }
}
