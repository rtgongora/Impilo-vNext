package zw.gov.mohcc.impilo.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clinical Knowledge and Guidance Platform — national EDLIZ-aligned capability.
 *
 * <p>Single deployable with modular packages (source, model, rules, assistant,
 * prescribing, pathway, nudge, audit) ready to extract into separate services.</p>
 */
@SpringBootApplication
@EnableScheduling
public class ClinicalKnowledgePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicalKnowledgePlatformApplication.class, args);
    }
}
