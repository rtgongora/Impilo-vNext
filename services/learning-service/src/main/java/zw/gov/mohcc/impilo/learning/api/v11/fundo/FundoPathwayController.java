package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoPathwayService;

/** Native Fundo v1.1 pathway surface (Phase 5B). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoPathwayController {

    private final FundoPathwayService pathwayService;

    public FundoPathwayController(FundoPathwayService pathwayService) {
        this.pathwayService = pathwayService;
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> listPathways(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "25") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", limit);
        if (tenantId == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", pathwayService.listPathways(tenantId, status, limit));
        return FundoV11Support.dataEnvelope(data);
    }

    @GetMapping("/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> getPathway(@PathVariable String pathwayId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID pid = FundoV11Support.tryParseUuid(pathwayId);
        if (tenantId == null || pid == null) {
            return FundoV11Support.notFound("PATHWAY_NOT_FOUND", "Pathway not found");
        }
        return pathwayService.getPathway(tenantId, pid)
                .map(p -> FundoV11Support.dataEnvelope("pathway", p))
                .orElseGet(() -> FundoV11Support.notFound("PATHWAY_NOT_FOUND", "Pathway not found"));
    }
}
