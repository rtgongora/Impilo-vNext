package zw.gov.mohcc.impilo.ndr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.ndr.config.NdrProperties;

/**
 * Entry point for the NDR (National Data Repository) service.
 *
 * <p>Analytics-ready data store managing national datasets, versioned table
 * definitions, and materialized view queries. Not a shared health record —
 * stores aggregate and de-identified data for reporting and analysis.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NdrProperties.class)
public class NdrApplication {

    public static void main(String[] args) {
        SpringApplication.run(NdrApplication.class, args);
    }
}
