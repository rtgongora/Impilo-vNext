package zw.gov.mohcc.impilo.mushex.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.service.rail.AdapterReadiness;
import zw.gov.mohcc.impilo.mushex.service.rail.AdapterReadinessService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Read-only operator endpoint that surfaces the readiness of every payment-rail
 * adapter. See {@link AdapterReadinessService} and Phase 2 of the MusheX/COSTA
 * release roadmap.
 *
 * <p>Never writes; never moves money; never reads credentials. Safe to expose
 * to finance-operator UIs through the BFF's
 * {@code FinanceMushexPlatformController}.
 */
@RestController
@RequestMapping("/mushex/v1/platform/adapter-readiness")
public class AdapterReadinessController {

    private final AdapterReadinessService adapterReadinessService;

    public AdapterReadinessController(AdapterReadinessService adapterReadinessService) {
        this.adapterReadinessService = adapterReadinessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdapterReadiness>>> list() {
        var ctx = TrustContextHolder.require();
        List<AdapterReadiness> rows = adapterReadinessService.describeAll();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }
}
