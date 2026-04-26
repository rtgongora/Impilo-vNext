package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facility list/detail integration for {@code /internal/v1/facilities}.
 *
 * <p>{@code live} — try TUSO first, then seeded facilities (existing behaviour).<br>
 * {@code stub} — skip TUSO entirely; use seeded facilities only (deterministic CI / Maestro).</p>
 *
 * <p>Environment: {@code IMPILO_BFF_FACILITIES_MODE}</p>
 */
@ConfigurationProperties(prefix = "impilo.bff.facilities")
public class BffFacilitiesProperties {

    private Mode mode = Mode.live;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.live;
    }

    public enum Mode {
        /** TUSO search/get when reachable, else seed data. */
        live,
        /** Never call TUSO; always serve seeded registry responses. */
        stub
    }
}
