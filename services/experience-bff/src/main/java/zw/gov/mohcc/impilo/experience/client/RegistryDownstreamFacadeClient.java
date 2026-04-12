package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.RegistryDownstreamProperties;

import java.util.Map;

/**
 * Shared reachability helper for registry services listed in {@code registry-downstream-services.yml}.
 * Used by {@link zw.gov.mohcc.impilo.experience.controller.RegistryDownstreamController}.
 */
@Component
public class RegistryDownstreamFacadeClient {

    private static final Logger log = LoggerFactory.getLogger(RegistryDownstreamFacadeClient.class);

    private final RestTemplate restTemplate;
    private final RegistryDownstreamProperties properties;

    public RegistryDownstreamFacadeClient(
            RestTemplate serviceRestTemplate,
            RegistryDownstreamProperties properties) {
        this.restTemplate = serviceRestTemplate;
        this.properties = properties;
    }

    public ResponseEntity<String> probeHealth(String mavenModule) {
        if ("experience-bff".equals(mavenModule)) {
            return ResponseEntity.badRequest().body(
                    "{\"error\":\"experience-bff is the shell; use actuator on this process\"}");
        }
        Map<String, String> map = properties.getServices();
        String base = map.get(mavenModule);
        if (base == null || base.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        try {
            return restTemplate.getForEntity(normalized + "/actuator/health", String.class);
        } catch (RestClientException first) {
            log.debug("actuator/health failed for {}: {}", mavenModule, first.getMessage());
        }
        try {
            return restTemplate.getForEntity(normalized + "/internal/v1/health", String.class);
        } catch (RestClientException second) {
            log.warn("Health probe failed for {}: {}", mavenModule, second.getMessage());
            return ResponseEntity.status(502).body("{\"reachable\":false,\"mavenModule\":\"" + mavenModule + "\"}");
        }
    }
}
