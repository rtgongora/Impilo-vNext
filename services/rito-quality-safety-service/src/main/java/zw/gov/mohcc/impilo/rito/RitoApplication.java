package zw.gov.mohcc.impilo.rito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Rito — Quality, Safety &amp; Client Voice service.
 *
 * Owns the general quality + client-voice + clinical-service-safety-incident + CAPA/QI
 * case truth and consumes/links sibling systems-of-record (see
 * {@code docs/design/rito/sor-boundary-rito-vs-patient-safety.md}). Operating cycle:
 * Listen → Classify → Triage → Assign → Investigate → Act → Verify → Close → Learn → Improve.
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class RitoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RitoApplication.class, args);
    }
}
