package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.MsikaAppsClient;
import zw.gov.mohcc.impilo.experience.client.IntegrationRegistryClient;

/**
 * BFF surface for the Health OS Launcher, Capability Marketplace,
 * Activation Workflow, Installations, and Integration Operations
 * (external app health, contracts, webhooks, event catalogue).
 *
 * <p>The web shell and admin dashboards call into these endpoints. All
 * requests are proxied through the trust-header-forwarding RestTemplate so
 * tenant/pod/actor/purpose context is preserved end-to-end.</p>
 */
@RestController
@RequestMapping("/internal/v1/marketplace")
public class HealthOsMarketplaceController {

    private final MsikaAppsClient msikaApps;
    private final IntegrationRegistryClient integration;

    public HealthOsMarketplaceController(MsikaAppsClient msikaApps, IntegrationRegistryClient integration) {
        this.msikaApps = msikaApps;
        this.integration = integration;
    }

    // ── Launcher ─────────────────────────────────────────────────────────────
    @GetMapping("/launcher")
    public ResponseEntity<String> launcher(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String roles) {
        return msikaApps.launcher(facilityId, roles);
    }

    // ── Catalogue ────────────────────────────────────────────────────────────
    @GetMapping("/items")
    public ResponseEntity<String> items(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String publisherId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (type != null)        params.add("type", type);
        if (category != null)    params.add("category", category);
        if (publisherId != null) params.add("publisherId", publisherId);
        return msikaApps.catalogue(params);
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<String> item(@PathVariable String id) { return msikaApps.item(id); }

    @GetMapping("/publishers")
    public ResponseEntity<String> publishers() { return msikaApps.publishers(); }

    // ── Activation workflow ──────────────────────────────────────────────────
    @PostMapping("/activation-requests")
    public ResponseEntity<String> requestActivation(@RequestBody String body) {
        return msikaApps.requestActivation(body);
    }

    @GetMapping("/activation-requests")
    public ResponseEntity<String> listActivationRequests(
            @RequestParam(required = false) String status) {
        return msikaApps.listActivationRequests(status);
    }

    @PostMapping("/activation-requests/{id}/decision")
    public ResponseEntity<String> decideActivation(@PathVariable String id, @RequestBody String body) {
        return msikaApps.decideActivation(id, body);
    }

    // ── Installations ────────────────────────────────────────────────────────
    @GetMapping("/installations")
    public ResponseEntity<String> installations(@RequestParam(required = false) String lifecycle) {
        return msikaApps.listInstallations(lifecycle);
    }

    @GetMapping("/installations/{id}")
    public ResponseEntity<String> installation(@PathVariable String id) {
        return msikaApps.getInstallation(id);
    }

    @PostMapping("/installations/{id}/configure")
    public ResponseEntity<String> configureInstallation(@PathVariable String id, @RequestBody String body) {
        return msikaApps.configureInstallation(id, body);
    }

    @PostMapping("/installations/{id}/activate")
    public ResponseEntity<String> activateInstallation(@PathVariable String id) {
        return msikaApps.activateInstallation(id);
    }

    @PostMapping("/installations/{id}/suspend")
    public ResponseEntity<String> suspendInstallation(@PathVariable String id, @RequestBody String body) {
        return msikaApps.suspendInstallation(id, body);
    }

    @GetMapping("/audit")
    public ResponseEntity<String> audit(@RequestParam(required = false) String itemId) {
        return msikaApps.auditEvents(itemId);
    }

    // ── Integration operations (from integration-hub) ────────────────────────

    @GetMapping("/integration/external-apps")
    public ResponseEntity<String> externalApps() { return integration.externalApps(); }

    @GetMapping("/integration/external-apps/{id}")
    public ResponseEntity<String> externalApp(@PathVariable String id) { return integration.externalApp(id); }

    @PostMapping("/integration/external-apps")
    public ResponseEntity<String> registerExternalApp(@RequestBody String body) {
        return integration.registerExternalApp(body);
    }

    @PostMapping("/integration/external-apps/{id}/suspend")
    public ResponseEntity<String> suspendExternalApp(@PathVariable String id, @RequestBody String body) {
        return integration.suspendExternalApp(id, body);
    }

    @GetMapping("/integration/contracts")
    public ResponseEntity<String> integrationContracts(@RequestParam(required = false) String externalAppId) {
        return integration.integrationContracts(externalAppId);
    }

    @PostMapping("/integration/contracts")
    public ResponseEntity<String> createIntegrationContract(@RequestBody String body) {
        return integration.createIntegrationContract(body);
    }

    @GetMapping("/integration/s2s-contracts")
    public ResponseEntity<String> s2sContracts() { return integration.s2sContracts(); }

    @PostMapping("/integration/s2s-contracts")
    public ResponseEntity<String> createS2SContract(@RequestBody String body) {
        return integration.createS2SContract(body);
    }

    @GetMapping("/integration/webhooks")
    public ResponseEntity<String> webhookSubscriptions(@RequestParam(required = false) String externalAppId) {
        return integration.webhookSubscriptions(externalAppId);
    }

    @PostMapping("/integration/webhooks")
    public ResponseEntity<String> createWebhookSubscription(@RequestBody String body) {
        return integration.createWebhookSubscription(body);
    }

    @GetMapping("/integration/event-catalogue")
    public ResponseEntity<String> eventCatalogue(
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String ownerServiceId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (classification != null)  params.add("classification", classification);
        if (ownerServiceId != null)  params.add("ownerServiceId", ownerServiceId);
        return integration.eventCatalogue(params);
    }
}
