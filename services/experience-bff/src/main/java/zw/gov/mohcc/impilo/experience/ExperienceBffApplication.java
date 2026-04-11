package zw.gov.mohcc.impilo.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(ClinicalPlatformProperties.class)
public class ExperienceBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExperienceBffApplication.class, args);
    }
}
