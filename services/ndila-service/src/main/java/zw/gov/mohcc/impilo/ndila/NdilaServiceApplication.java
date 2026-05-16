package zw.gov.mohcc.impilo.ndila;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ndila — Geospatial Intelligence, Routing, Location and Spatial Orchestration
 * Service for Impilo vNext.
 *
 * <p>Ndila is the spatial intelligence layer of Impilo vNext: it turns
 * locations, routes, boundaries, catchments, movements and risks into
 * actionable health system intelligence.
 *
 * <p>Ndila helps Impilo understand where things are, what they are close to,
 * where they are moving, what area they belong to, what route should be taken,
 * what risks are nearby, what services are available nearby, what populations
 * are affected, what operational area a user is responsible for, what
 * facility / provider / courier / inspector / response team is closest, and
 * what delivery, inspection, outreach, surveillance, referral, dispatch or
 * emergency action is needed next.
 *
 * <p>Ndila is a cross-cutting platform capability for the full Impilo
 * ecosystem (Tuso, Indawo, Nhume/Dispatch, Public Health Operations,
 * Data/Intelligence, Comms Hub, Telehealth, Marketplace, MusheX, Varapi,
 * Vito, Fundo, Nompilo, the One UI Experience Layer, the Provider Mobile
 * App and the Citizen Mobile App).
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NdilaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NdilaServiceApplication.class, args);
    }
}
