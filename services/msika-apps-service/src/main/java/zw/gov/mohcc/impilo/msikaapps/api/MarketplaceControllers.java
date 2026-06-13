package zw.gov.mohcc.impilo.msikaapps.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.msikaapps.api.dto.MsikaAppsDtos.*;
import zw.gov.mohcc.impilo.msikaapps.service.MarketplaceService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controllers for the Msika Apps capability marketplace. Endpoints are
 * intentionally consumed by both the experience-bff (for users) and admin
 * dashboards. All endpoints enforce tenant isolation via the trust context.
 */
public final class MarketplaceControllers {
    private MarketplaceControllers() {}

    @RestController
    @RequestMapping("/internal/v1/marketplace/items")
    public static class CatalogueController {
        private final MarketplaceService service;
        public CatalogueController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('CITIZEN','PROVIDER','FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER')")
        public List<MarketplaceItemResponse> search(
                @RequestParam(required = false) String type,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) String publisherId) {
            return service.searchCatalogue(type, category, publisherId);
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('CITIZEN','PROVIDER','FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER')")
        public MarketplaceItemResponse get(@PathVariable String id) {
            return service.getItem(id);
        }
    }

    @RestController
    @RequestMapping("/internal/v1/marketplace/publishers")
    public static class PublisherController {
        private final MarketplaceService service;
        public PublisherController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER')")
        public List<PublisherResponse> list() { return service.listPublishers(); }
    }

    @RestController
    @RequestMapping("/internal/v1/marketplace/activation-requests")
    public static class ActivationRequestsController {
        private final MarketplaceService service;
        public ActivationRequestsController(MarketplaceService service) { this.service = service; }

        @PostMapping
        @PreAuthorize("hasAnyRole('CITIZEN','PROVIDER','FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public ResponseEntity<ActivationRequestResponse> create(
                @Valid @RequestBody ActivationRequestRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "anonymous";
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.createActivationRequest(req, actorId, ctx.tenantId(), ctx.podId(), ctx.correlationId()));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public List<ActivationRequestResponse> list(
                @RequestParam(required = false) String status) {
            return service.listActivationRequests(RequestContextHolder.require().tenantId(), status);
        }

        @PostMapping("/{id}/decision")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ActivationRequestResponse decide(
                @PathVariable String id,
                @Valid @RequestBody ActivationDecisionRequest decision) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.decideActivationRequest(id, decision, actorId, ctx.tenantId());
        }
    }

    @RestController
    @RequestMapping("/internal/v1/marketplace/installations")
    public static class InstallationsController {
        private final MarketplaceService service;
        public InstallationsController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public List<InstallationResponse> list(
                @RequestParam(required = false) String lifecycle) {
            return service.listInstallations(RequestContextHolder.require().tenantId(), lifecycle);
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public InstallationResponse get(@PathVariable String id) {
            return service.getInstallation(id, RequestContextHolder.require().tenantId());
        }

        @PostMapping("/{id}/configure")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','SYSTEM_ADMIN')")
        public InstallationResponse configure(
                @PathVariable String id,
                @Valid @RequestBody InstallationConfigureRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.configureInstallation(id, req, actorId, ctx.tenantId());
        }

        @PostMapping("/{id}/activate")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public InstallationResponse activate(@PathVariable String id) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.activateInstallation(id, actorId, ctx.tenantId());
        }

        @PostMapping("/{id}/suspend")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public InstallationResponse suspend(
                @PathVariable String id,
                @Valid @RequestBody InstallationSuspendRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.suspendInstallation(id, req, actorId, ctx.tenantId());
        }

        @PostMapping("/{id}/reinstate")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public InstallationResponse reinstate(@PathVariable String id) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.activateInstallation(id, actorId, ctx.tenantId());
        }
    }

    /** Contract path-param aliases (`installationId`, `requestId`, `itemId`). */
    @RestController
    @RequestMapping("/internal/v1/marketplace")
    public static class ContractPathAliasController {
        private final MarketplaceService service;
        public ContractPathAliasController(MarketplaceService service) { this.service = service; }

        @GetMapping("/installations/{installationId}")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public InstallationResponse getInstallation(@PathVariable("installationId") String installationId) {
            return service.getInstallation(installationId, RequestContextHolder.require().tenantId());
        }

        @PostMapping("/installations/{installationId}/configure")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','SYSTEM_ADMIN')")
        public InstallationResponse configureInstallation(
                @PathVariable("installationId") String installationId,
                @Valid @RequestBody InstallationConfigureRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.configureInstallation(installationId, req, actorId, ctx.tenantId());
        }

        @PostMapping("/installations/{installationId}/suspend")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN')")
        public InstallationResponse suspendInstallation(
                @PathVariable("installationId") String installationId,
                @Valid @RequestBody InstallationSuspendRequest req) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.suspendInstallation(installationId, req, actorId, ctx.tenantId());
        }

        @PostMapping("/installations/{installationId}/reinstate")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public InstallationResponse reinstateInstallation(@PathVariable("installationId") String installationId) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            return service.activateInstallation(installationId, actorId, ctx.tenantId());
        }
    }

    /** Contract-aligned aliases for `/apps` paths (canonical handlers remain on `/items`). */
    @RestController
    @RequestMapping("/internal/v1/marketplace/apps")
    public static class AppsAliasController {
        private final MarketplaceService service;
        public AppsAliasController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('CITIZEN','PROVIDER','FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER')")
        public List<MarketplaceItemResponse> search(
                @RequestParam(required = false) String type,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) String publisherId) {
            return service.searchCatalogue(type, category, publisherId);
        }

        @GetMapping("/{itemId}")
        @PreAuthorize("hasAnyRole('CITIZEN','PROVIDER','FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','DEVELOPER')")
        public MarketplaceItemResponse get(@PathVariable("itemId") String itemId) {
            return service.getItem(itemId);
        }
    }

    /** Contract-aligned approve/reject aliases for activation decisions. */
    @RestController
    @RequestMapping("/internal/v1/marketplace/activation-requests")
    public static class ActivationDecisionAliasController {
        private final MarketplaceService service;
        public ActivationDecisionAliasController(MarketplaceService service) { this.service = service; }

        @PostMapping("/{requestId}/approve")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ActivationRequestResponse approve(
                @PathVariable("requestId") String requestId,
                @RequestBody(required = false) Map<String, String> body) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            String reason = body != null ? body.getOrDefault("reason", "approved") : "approved";
            return service.decideActivationRequest(
                    requestId, new ActivationDecisionRequest("APPROVED", reason), actorId, ctx.tenantId());
        }

        @PostMapping("/{requestId}/reject")
        @PreAuthorize("hasAnyRole('FACILITY_ADMIN','DISTRICT_ADMIN','PROVINCIAL_ADMIN','NATIONAL_ADMIN','MARKETPLACE_ADMIN','SYSTEM_ADMIN')")
        public ActivationRequestResponse reject(
                @PathVariable("requestId") String requestId,
                @RequestBody(required = false) Map<String, String> body) {
            RequestContext ctx = RequestContextHolder.require();
            String actorId = ctx.principal() != null ? ctx.principal().getName() : "system";
            String reason = body != null ? body.getOrDefault("reason", "rejected") : "rejected";
            return service.decideActivationRequest(
                    requestId, new ActivationDecisionRequest("REJECTED", reason), actorId, ctx.tenantId());
        }
    }

    /** Contract-aligned audit path alias (`/audit-events`). */
    @RestController
    @RequestMapping("/internal/v1/marketplace/audit-events")
    public static class AuditEventsAliasController {
        private final MarketplaceService service;
        public AuditEventsAliasController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','AUDITOR')")
        public List<AuditEventResponse> list(@RequestParam(required = false) String itemId) {
            return service.listAuditEvents(RequestContextHolder.require().tenantId(), itemId);
        }
    }

    @RestController
    @RequestMapping("/internal/v1/marketplace/launcher")
    public static class LauncherController {
        private final MarketplaceService service;
        public LauncherController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("isAuthenticated() or @msikaInternalTrustAuthorization.allowLauncher()")
        public List<LauncherAppResponse> launcher(
                @RequestParam(required = false) String facilityId,
                @RequestParam(required = false) String roles) {
            List<String> roleList = (roles == null || roles.isBlank())
                    ? List.of()
                    : Arrays.stream(roles.split(",")).map(String::trim).toList();
            return service.getLauncher(RequestContextHolder.require().tenantId(), facilityId, roleList);
        }
    }

    @RestController
    @RequestMapping("/internal/v1/marketplace/audit")
    public static class AuditController {
        private final MarketplaceService service;
        public AuditController(MarketplaceService service) { this.service = service; }

        @GetMapping
        @PreAuthorize("hasAnyRole('MARKETPLACE_ADMIN','INTEGRATION_ADMIN','OPERATIONS','SYSTEM_ADMIN','AUDITOR')")
        public List<AuditEventResponse> list(@RequestParam(required = false) String itemId) {
            return service.listAuditEvents(RequestContextHolder.require().tenantId(), itemId);
        }
    }
}
