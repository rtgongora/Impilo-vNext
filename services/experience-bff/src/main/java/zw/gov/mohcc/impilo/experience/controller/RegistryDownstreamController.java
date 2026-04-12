package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.RegistryDownstreamFacadeClient;

/**
 * Legacy path for shell reachability checks; delegates to {@link RegistryDownstreamFacadeClient}.
 * Prefer {@link RegistryShellController} for new callers.
 */
@RestController
@RequestMapping("/internal/v1/registry-downstream")
public class RegistryDownstreamController {

    private final RegistryDownstreamFacadeClient registryDownstreamFacadeClient;

    public RegistryDownstreamController(RegistryDownstreamFacadeClient registryDownstreamFacadeClient) {
        this.registryDownstreamFacadeClient = registryDownstreamFacadeClient;
    }

    @GetMapping("/{mavenModule}/health")
    public ResponseEntity<String> downstreamHealth(@PathVariable("mavenModule") String mavenModule) {
        return registryDownstreamFacadeClient.probeHealth(mavenModule);
    }
}
