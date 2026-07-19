package zw.gov.mohcc.impilo.abis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ABIS — Automated Biometric Identification System boundary (Identity Contract §11, D6).
 *
 * <p>Owns encrypted biometric template custody, 1:1 verification and restricted 1:N
 * identification. Fails closed until a vendor matching engine is registered.
 * Deploy queues for the next authorized full-boot (new service).</p>
 */
@SpringBootApplication
@EnableScheduling
public class AbisApplication {
    public static void main(String[] args) {
        SpringApplication.run(AbisApplication.class, args);
    }
}
