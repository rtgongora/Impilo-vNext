package zw.gov.mohcc.impilo.varapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(VarapiProperties.class)
public class VarapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VarapiApplication.class, args);
    }
}
