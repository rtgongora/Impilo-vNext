package zw.gov.mohcc.impilo.integration.governance.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.integration.governance.api.dto.GovernanceDtos.*;
import zw.gov.mohcc.impilo.integration.governance.service.GovernanceService;
import zw.gov.mohcc.impilo.integration.governance.service.WebhookSigner;

import java.util.List;
import java.util.Map;

/**
 * REST controllers for the integration governance plane. Grouped into one
 * file because the governance surface is intentionally a thin CRUD plane;
 * complex orchestration lives in {@link GovernanceService}.
 */
public final class GovernanceControllers {

    private GovernanceControllers() {}

    // ── External Applications ────────────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/external-apps")
    public static class ExternalAppsController {
        private final GovernanceService service;
        public ExternalAppsController(GovernanceService service) { this.service = service; }

        @PostMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ResponseEntity<ExternalApplicationResponse> register(
                @Valid @RequestBody ExternalApplicationRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            ExternalApplicationResponse resp = service.registerExternalApplication(req, ctx.tenantId(), ctx.podId());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public List<ExternalApplicationResponse> list() {
            return service.listExternalApplications(RequestContextHolder.require().tenantId());
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public ExternalApplicationResponse get(@PathVariable String id) {
            return service.getExternalApplication(id);
        }

        @PostMapping("/{id}/suspend")
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ExternalApplicationResponse suspend(@PathVariable String id, @Valid @RequestBody SuspendRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.suspendExternalApplication(id, req.reason(), actorId);
        }
    }

    // ── Integration Contracts ────────────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/integration-contracts")
    public static class IntegrationContractsController {
        private final GovernanceService service;
        public IntegrationContractsController(GovernanceService service) { this.service = service; }

        @PostMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ResponseEntity<IntegrationContractResponse> create(
                @Valid @RequestBody IntegrationContractRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.createIntegrationContract(req, ctx.tenantId(), ctx.podId()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public List<IntegrationContractResponse> list(
                @RequestParam(value = "externalAppId", required = false) String externalAppId) {
            return service.listIntegrationContracts(RequestContextHolder.require().tenantId(), externalAppId);
        }
    }

    // ── Service-to-Service Contracts ─────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/s2s-contracts")
    public static class ServiceToServiceContractsController {
        private final GovernanceService service;
        public ServiceToServiceContractsController(GovernanceService service) { this.service = service; }

        @PostMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','SYSTEM_ADMIN')")
        public ResponseEntity<ServiceToServiceContractResponse> create(
                @Valid @RequestBody ServiceToServiceContractRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.createS2SContract(req, ctx.tenantId(), ctx.podId()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public List<ServiceToServiceContractResponse> list() {
            return service.listS2SContracts(RequestContextHolder.require().tenantId());
        }

        @GetMapping("/check")
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER','SERVICE')")
        public Map<String, Object> check(
                @RequestParam String caller,
                @RequestParam String callee,
                @RequestParam(value = "requestSource", defaultValue = "SYSTEM") String requestSource) {
            boolean authorised = service.isS2SCallAuthorised(caller, callee, requestSource);
            return Map.of("authorised", authorised, "caller", caller, "callee", callee, "requestSource", requestSource);
        }
    }

    // ── Webhook Subscriptions ────────────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/webhook-subscriptions")
    public static class WebhookSubscriptionsController {
        private final GovernanceService service;
        private final WebhookSigner signer;
        public WebhookSubscriptionsController(GovernanceService service, WebhookSigner signer) {
            this.service = service;
            this.signer = signer;
        }

        @PostMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ResponseEntity<WebhookSubscriptionResponse> create(
                @Valid @RequestBody WebhookSubscriptionRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.createWebhookSubscription(req, ctx.tenantId(), ctx.podId()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public List<WebhookSubscriptionResponse> list(
                @RequestParam(value = "externalAppId", required = false) String externalAppId) {
            return service.listWebhookSubscriptions(RequestContextHolder.require().tenantId(), externalAppId);
        }

        @PostMapping("/{id}/test")
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
        public Map<String, Object> test(@PathVariable String id) {
            String samplePayload = "{\"sample\":true,\"webhookSubscriptionId\":\"" + id + "\"}";
            // In a real test delivery we would resolve the per-subscription secret
            // from vault-kms and POST to the configured URL. This endpoint returns
            // the sample signed envelope so administrators can verify integration
            // wiring before exposing a real subscription. Never expose production
            // secrets here.
            String signature = signer.signNow("dev-sandbox-secret", samplePayload);
            return Map.of(
                    "subscriptionId", id,
                    "samplePayload", samplePayload,
                    "signatureHeader", signature,
                    "dispatched", false,
                    "note", "Sandbox test signature shown; production secret resolved from vault-kms."
            );
        }
    }

    // ── Event Catalogue ──────────────────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/event-catalogue")
    public static class EventCatalogueController {
        private final GovernanceService service;
        public EventCatalogueController(GovernanceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("isAuthenticated()")
        public List<EventCatalogueEntryResponse> list(
                @RequestParam(value = "classification", required = false) String classification,
                @RequestParam(value = "ownerServiceId", required = false) String ownerServiceId) {
            return service.listEventCatalogue(classification, ownerServiceId);
        }
    }

    // ── External Event Subscriptions ─────────────────────────────────────────
    @RestController
    @RequestMapping("/internal/v1/external-event-subscriptions")
    public static class ExternalEventSubscriptionsController {
        private final GovernanceService service;
        public ExternalEventSubscriptionsController(GovernanceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','MARKETPLACE_ADMIN','OPERATIONS','AUDITOR','SYSTEM_ADMIN','DEVELOPER')")
        public List<Map<String, Object>> list(
                @RequestParam(value = "externalAppId", required = false) String externalAppId) {
            String tenantId = RequestContextHolder.require().tenantId();
            return service.listExternalEventSubscriptions(tenantId).stream()
                    .filter(sub -> externalAppId == null || externalAppId.isBlank()
                            || externalAppId.equals(sub.getExternalAppId()))
                    .map(sub -> Map.<String, Object>of(
                            "id", sub.getId(),
                            "externalAppId", sub.getExternalAppId(),
                            "integrationContractId", sub.getIntegrationContractId(),
                            "webhookSubscriptionId", sub.getWebhookSubscriptionId(),
                            "eventTopic", sub.getEventTopic(),
                            "status", sub.getStatus(),
                            "approvedBy", sub.getApprovedBy() != null ? sub.getApprovedBy() : "",
                            "approvedAt", sub.getApprovedAt(),
                            "createdAt", sub.getCreatedAt()))
                    .toList();
        }
    }
}
