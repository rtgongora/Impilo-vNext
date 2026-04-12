package zw.gov.mohcc.impilo.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Community health service — outreach, CHW assignments, community units, field encounters.
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class CommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityApplication.class, args);
    }
}
