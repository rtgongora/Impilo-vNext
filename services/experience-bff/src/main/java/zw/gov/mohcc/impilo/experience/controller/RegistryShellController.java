package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.RegistryDownstreamFacadeClient;

/**
 * Thin shell routes that delegate to {@link RegistryDownstreamFacadeClient} so the Experience BFF
 * has an explicit typed integration surface for every registry downstream in config.
 */
@RestController
@RequestMapping("/internal/v1/registry-shell")
public class RegistryShellController {

    private final RegistryDownstreamFacadeClient registryDownstreamFacadeClient;

    public RegistryShellController(RegistryDownstreamFacadeClient registryDownstreamFacadeClient) {
        this.registryDownstreamFacadeClient = registryDownstreamFacadeClient;
    }

    @GetMapping("/services/{mavenModule}/health")
    public ResponseEntity<String> serviceHealth(@PathVariable("mavenModule") String mavenModule) {
        return registryDownstreamFacadeClient.probeHealth(mavenModule);
    }
}
