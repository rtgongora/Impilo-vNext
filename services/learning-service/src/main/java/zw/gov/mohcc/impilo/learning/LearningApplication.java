package zw.gov.mohcc.impilo.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;

@SpringBootApplication
@EnableConfigurationProperties(LearningProperties.class)
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
