package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facility list/detail integration for {@code /internal/v1/facilities}.
 *
 * <p>{@code live} — use TUSO only and fail closed when unavailable.<br>
 * {@code stub} — skip TUSO entirely; use seeded facilities only (deterministic test harness / Maestro).</p>
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
        /** TUSO search/get only; no synthetic fallback in production mode. */
        live,
        /** Never call TUSO; always serve seeded registry responses. */
        stub
    }
}
