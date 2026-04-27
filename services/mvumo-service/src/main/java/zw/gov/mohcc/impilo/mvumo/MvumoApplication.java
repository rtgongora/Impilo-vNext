package zw.gov.mohcc.impilo.mvumo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mvumo — national digital consent orchestration (adaptive assurance, templates, requests, remote sessions).
 * Deployed as a sovereign Ring-0 service (same boundary class as Tshepo, VITO, and other spine modules).
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "zw.gov.mohcc.impilo.mvumo.persistence")
@EnableJpaRepositories(basePackages = "zw.gov.mohcc.impilo.mvumo.persistence")
public class MvumoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MvumoApplication.class, args);
    }
}
