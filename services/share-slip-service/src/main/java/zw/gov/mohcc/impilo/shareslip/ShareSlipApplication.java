package zw.gov.mohcc.impilo.shareslip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.shareslip.config.ShareSlipProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ShareSlipProperties.class)
public class ShareSlipApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShareSlipApplication.class, args);
    }
}
