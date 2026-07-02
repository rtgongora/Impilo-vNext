package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ConfigEvent;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ConfigurationSummary;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.DownstreamReadiness;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.QueueDefinitionView;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.SetupChecklist;
import zw.gov.mohcc.impilo.tuso.core.FacilityConfigurationService;

import java.util.List;

/**
 * Facility Configuration Console read-models + configuration-publish (TUSO SoR). Composes existing
 * service-point / workspace / unit / readiness / contact entities into the console's overview, setup
 * checklist, derived queue definitions and downstream readiness. TUSO owns facility configuration; PCT
 * only materialises the derived queue definitions.
 *
 * <p>Authz via TSHEPO ext_authz; the mutating publish route is additionally guarded downstream (the BFF
 * admin namespace restricts it to admin roles). Reads are tenant-scoped by {@code ctx.tenantId()}.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityId}/configuration")
public class FacilityConfigurationController {

    private static final Logger log = LoggerFactory.getLogger(FacilityConfigurationController.class);

    private final FacilityConfigurationService configurationService;

    public FacilityConfigurationController(FacilityConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ConfigurationSummary>> summary(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Facility configuration summary [facilityId={}] correlationId={}", facilityId, ctx.correlationId());
        ConfigurationSummary out = call(() -> configurationService.getConfigurationSummary(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/setup-checklist")
    public ResponseEntity<ApiResponse<SetupChecklist>> setupChecklist(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        SetupChecklist out = call(() -> configurationService.getSetupChecklist(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/queue-definitions")
    public ResponseEntity<ApiResponse<List<QueueDefinitionView>>> queueDefinitions(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<QueueDefinitionView> out = call(() -> configurationService.getQueueDefinitions(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/downstream-readiness")
    public ResponseEntity<ApiResponse<DownstreamReadiness>> downstreamReadiness(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        DownstreamReadiness out = call(() -> configurationService.getDownstreamReadiness(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ConfigEvent>>> history(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<ConfigEvent> out = call(() -> configurationService.getConfigurationHistory(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<ConfigurationSummary>> publish(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Publishing facility configuration update [facilityId={}] correlationId={}",
                facilityId, ctx.correlationId());
        ConfigurationSummary out = call(() -> configurationService.publishConfigurationUpdate(ctx, facilityId));
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    /** Translate the service's IllegalArgument/Security exceptions into HTTP 404/403. */
    private static <T> T call(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }
}
