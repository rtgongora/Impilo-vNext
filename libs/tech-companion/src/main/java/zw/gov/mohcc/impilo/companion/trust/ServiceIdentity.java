package zw.gov.mohcc.impilo.companion.trust;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the calling service's identity for outbound S2S calls.
 * Populated from properties:
 *   - {@code impilo.service.id}        e.g. "msika-apps-service"
 *   - {@code impilo.service.name}      human-readable
 *   - {@code impilo.service.version}   semver
 *
 * <p>Defaults fall back to {@code spring.application.name} where possible.
 */
@Component
public class ServiceIdentity {

    private final String id;
    private final String name;
    private final String version;

    public ServiceIdentity(
            @Value("${impilo.service.id:${spring.application.name:unknown-service}}") String id,
            @Value("${impilo.service.name:${spring.application.name:unknown-service}}") String name,
            @Value("${impilo.service.version:0.1.0-SNAPSHOT}") String version) {
        this.id = id;
        this.name = name;
        this.version = version;
    }

    public String id()      { return id; }
    public String name()    { return name; }
    public String version() { return version; }
}
