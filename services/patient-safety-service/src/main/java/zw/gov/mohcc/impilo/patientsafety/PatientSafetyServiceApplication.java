package zw.gov.mohcc.impilo.patientsafety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Patient Safety &amp; Pharmacovigilance service.
 *
 * <p>System of record for ADR/AEFI safety reports, regulatory safety cases, seriousness and
 * outcomes, the MCAZ review workbench, manual VigiFlow submission tracking and E2B(R3)-aligned
 * export readiness. External submission to WHO-UMC / VigiFlow is NOT performed here — it is
 * delegated to integration-hub adapters which are disabled by default; until an adapter is
 * connected, cases surface as "manual entry / export pending", never "submitted".
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class PatientSafetyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientSafetyServiceApplication.class, args);
    }
}
