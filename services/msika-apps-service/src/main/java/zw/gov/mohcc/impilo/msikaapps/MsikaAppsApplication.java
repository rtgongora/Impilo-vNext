package zw.gov.mohcc.impilo.msikaapps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Msika Apps — Capability Marketplace.
 *
 * Owns the governed catalogue of apps, plugins, extensions, connectors,
 * adapters, workflow packs, content packs, AI skills and device integrations
 * for the Impilo vNext Health OS. See doctrine.
 */
@SpringBootApplication
@EnableScheduling
public class MsikaAppsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsikaAppsApplication.class, args);
    }
}
