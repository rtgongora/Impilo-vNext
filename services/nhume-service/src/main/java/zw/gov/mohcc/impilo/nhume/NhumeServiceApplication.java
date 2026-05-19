package zw.gov.mohcc.impilo.nhume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nhume — Dispatch, Delivery, Fleet Tracking & Last-Mile Logistics Service.
 *
 * <p>Nhume is the Impilo logistics nervous system: it moves health-related goods,
 * samples, medicines, devices, documents and supplies across the platform safely,
 * visibly, intelligently and accountably.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NhumeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NhumeServiceApplication.class, args);
    }
}
