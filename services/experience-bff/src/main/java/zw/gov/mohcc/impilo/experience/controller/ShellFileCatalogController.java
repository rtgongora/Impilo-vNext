package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.shell.ShellFileCatalogService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shell-level file/document catalog (report runs, facility certificates, …).
 */
@RestController
@RequestMapping("/internal/v1/shell/file-catalog")
public class ShellFileCatalogController {

    private final ShellFileCatalogService catalogService;

    public ShellFileCatalogController(ShellFileCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            HttpServletRequest request) {
        Map<String, Object> data = catalogService.buildCatalog(request, facilityId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "tenant_id", tenantId));
        return ResponseEntity.ok(body);
    }
}
