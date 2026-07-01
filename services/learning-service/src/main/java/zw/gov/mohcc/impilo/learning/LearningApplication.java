package zw.gov.mohcc.impilo.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.learning.config.LearningDatabaseBootstrap;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;

@SpringBootApplication
@EnableConfigurationProperties(LearningProperties.class)
@EnableScheduling
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LearningApplication.class);
        application.addInitializers(context -> LearningDatabaseBootstrap.createDatabaseIfMissing(context.getEnvironment()));
        application.run(args);
    }
}
