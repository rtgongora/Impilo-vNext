package zw.gov.mohcc.impilo.oros.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.AdminConfigService;
import zw.gov.mohcc.impilo.oros.core.RoutingDirectoryService;
import zw.gov.mohcc.impilo.oros.api.dto.DestinationDto;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clinician-facing read access to the Service Catalogue &amp; Fulfilment Directory (spec §9):
 * the orderable test/procedure/service catalogue, the fulfilment-destination directory, and
 * specimen configuration the order forms pick from. Curated by admins via {@code /v1/admin/*};
 * read here for ordering. Authorization is enforced at the Envoy/TSHEPO edge.
 */
@RestController
@RequestMapping("/v1/catalogue")
public class ServiceCatalogueController {

    private final AdminConfigService adminConfigService;
    private final RoutingDirectoryService routingDirectoryService;
    private final ObjectMapper objectMapper;

    public ServiceCatalogueController(AdminConfigService adminConfigService,
                                      RoutingDirectoryService routingDirectoryService,
                                      ObjectMapper objectMapper) {
        this.adminConfigService = adminConfigService;
        this.routingDirectoryService = routingDirectoryService;
        this.objectMapper = objectMapper;
    }

    /** The curated service catalogue & fulfilment directory (capabilities, hours, return method, …). */
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<Object>> services(
            @RequestParam(name = "facility", required = false) UUID facility) {
        return read(AdminConfigService.SERVICE_CATALOGUE, facility);
    }

    /** Orderable tests/procedures/services (codes the order forms pick from). */
    @GetMapping("/orderables")
    public ResponseEntity<ApiResponse<Object>> orderables(
            @RequestParam(name = "facility", required = false) UUID facility) {
        return read(AdminConfigService.ORDERABLE_CATALOGUE, facility);
    }

    /** Specimen-type configuration (accepted types/containers/handling). */
    @GetMapping("/specimen-config")
    public ResponseEntity<ApiResponse<Object>> specimenConfig(
            @RequestParam(name = "facility", required = false) UUID facility) {
        return read(AdminConfigService.SPECIMEN_CONFIG, facility);
    }

    /** Live fulfilment destinations (internal TUSO depts/service-points + external VARAPI providers). */
    @GetMapping("/destinations")
    public ResponseEntity<ApiResponse<List<DestinationDto>>> destinations() {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(routingDirectoryService.destinations(), cid));
    }

    private ResponseEntity<ApiResponse<Object>> read(String type, UUID facility) {
        String cid = TrustContextHolder.require().correlationId().toString();
        String json = adminConfigService.get(type, facility);
        Object parsed;
        try {
            parsed = objectMapper.readValue(json != null ? json : "{}", Object.class);
        } catch (Exception e) {
            parsed = Map.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(parsed, cid));
    }
}
