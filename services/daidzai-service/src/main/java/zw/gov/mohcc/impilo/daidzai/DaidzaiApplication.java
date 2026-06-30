package zw.gov.mohcc.impilo.daidzai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Daidzai — Emergency, Disaster &amp; Public-Health Response Command.
 *
 * <p>Owns the {@code emergency_request} / {@code emergency_incident} / {@code resource_request}
 * aggregates and the mission/dispatch status timeline, and orchestrates the sovereign
 * systems-of-record (Nhume=dispatch, Ndila=maps, PCT=clinical encounter/handoff, Tuso/Indawo=
 * facility, Vito=identity incl. temporary/unknown, Khuluma=comms, Rito=after-action) WITHOUT
 * duplicating any of them. Doctrine: no payment ever blocks an emergency.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class DaidzaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaidzaiApplication.class, args);
    }
}
