package zw.gov.mohcc.impilo.tshepo.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ConsentProperties.class)
public class TshepoConsentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TshepoConsentApplication.class, args);
    }
}
