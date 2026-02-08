package zw.gov.mohcc.impilo.msikaflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.msikaflow.config.MsikaFlowProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MsikaFlowProperties.class)
public class MsikaFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsikaFlowApplication.class, args);
    }
}
