package zw.gov.mohcc.impilo.tshepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import zw.gov.mohcc.impilo.tshepo.config.TshepoLearningRelayProperties;

@SpringBootApplication
@EnableConfigurationProperties(TshepoLearningRelayProperties.class)
public class TshepoApplication {
    public static void main(String[] args) {
        SpringApplication.run(TshepoApplication.class, args);
    }
}
