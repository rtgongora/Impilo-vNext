package zw.gov.mohcc.impilo.tshepo.offline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OfflineProperties.class)
public class TshepoOfflineApplication {
    public static void main(String[] args) {
        SpringApplication.run(TshepoOfflineApplication.class, args);
    }
}
