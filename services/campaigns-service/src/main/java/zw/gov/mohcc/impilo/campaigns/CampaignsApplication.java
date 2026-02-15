package zw.gov.mohcc.impilo.campaigns;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampaignsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignsApplication.class, args);
    }
}
