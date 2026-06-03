package zw.gov.mohcc.impilo.butano;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

/**
 * BUTANO — National Shared Health Record Service.
 *
 * <p>HAPI FHIR R4 JPA Server providing a PII-free shared health record.
 * All patient data is referenced by CPID (Clinical Person Identifier) only;
 * names, telecom, addresses, and other personally-identifiable information
 * are stored exclusively in VITO (the identity service).</p>
 *
 * <p>Multi-tenant enforcement ensures strict data isolation per tenant,
 * with all requests validated through TSHEPO trust headers.</p>
 *
 * <p>Port: 8090 (local dev)</p>
 */
@SpringBootApplication(exclude = {
        ElasticsearchRestClientAutoConfiguration.class
})
@EntityScan(basePackages = {
        "zw.gov.mohcc.impilo.butano.persistence.entity",
        "ca.uhn.fhir.jpa.model.entity",
        "ca.uhn.fhir.jpa.entity"
})
@EnableJpaRepositories(basePackages = "zw.gov.mohcc.impilo.butano.persistence.repository")
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties(ButanoProperties.class)
public class ButanoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ButanoApplication.class, args);
    }
}
