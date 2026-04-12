package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional base URLs for registry services (generated from services-registry.yaml).
 * Used by {@link zw.gov.mohcc.impilo.experience.controller.RegistryDownstreamController}
 * for shell-level health reachability checks.
 */
@ConfigurationProperties(prefix = "impilo.registry-downstream")
public class RegistryDownstreamProperties {

    private Map<String, String> services = new HashMap<>();

    public Map<String, String> getServices() {
        return services;
    }

    public void setServices(Map<String, String> services) {
        this.services = services != null ? services : new HashMap<>();
    }
}
