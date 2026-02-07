package zw.gov.mohcc.impilo.landela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.landela.config.LandelaProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LandelaProperties.class)
public class LandelaAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(LandelaAdapterApplication.class, args);
    }
}
