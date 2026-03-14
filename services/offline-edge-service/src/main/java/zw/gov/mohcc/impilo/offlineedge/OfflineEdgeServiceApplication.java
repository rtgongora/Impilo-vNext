package zw.gov.mohcc.impilo.offlineedge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OfflineEdgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OfflineEdgeServiceApplication.class, args);
    }
}
