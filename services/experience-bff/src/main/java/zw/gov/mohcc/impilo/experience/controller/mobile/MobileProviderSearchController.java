package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.*;

/**
 * Mobile provider search endpoints.
 * POST /internal/v1/mobile/provider/search/qr - decode QR payload and resolve patient.
 *
 * <p>STRANGLER: migrated from JdbcTemplate to VitoServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/search")
public class MobileProviderSearchController {

    private static final Logger log = LoggerFactory.getLogger(MobileProviderSearchController.class);

    private final VitoServiceClient vitoClient;

    public MobileProviderSearchController(VitoServiceClient vitoClient) {
        this.vitoClient = vitoClient;
    }

    public record QrSearchRequest(
            @NotBlank String qr_data
    ) {}

    @PostMapping("/qr")
    public ResponseEntity<Map<String, Object>> searchByQr(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody QrSearchRequest request) {

        // Previously: jdbcTemplate.queryForList("SELECT ... FROM patients p WHERE p.tenant_id = ? AND (p.cpid = ? OR p.national_id = ? OR p.id::text = ?)")
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            JsonNode result = vitoClient.searchClients(request.qr_data());
            if (result != null && result.isArray()) {
                for (JsonNode node : result) {
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    if (node.has("cpid")) attributes.put("cpid", node.get("cpid").asText());
                    if (node.has("given_name")) attributes.put("given_name", node.get("given_name").asText());
                    if (node.has("family_name")) attributes.put("family_name", node.get("family_name").asText());
                    if (node.has("date_of_birth")) attributes.put("date_of_birth", node.get("date_of_birth").asText());
                    if (node.has("sex")) attributes.put("sex", node.get("sex").asText());
                    if (node.has("national_id")) attributes.put("national_id", node.get("national_id").asText());
                    if (node.has("phone")) attributes.put("phone", node.get("phone").asText());
                    if (node.has("status")) attributes.put("status", node.get("status").asText());
                    if (node.has("facility_id")) attributes.put("facility_id", node.get("facility_id").asText());

                    Map<String, Object> resource = new LinkedHashMap<>();
                    resource.put("id", node.has("id") ? node.get("id").asText() : "");
                    resource.put("type", "Patient");
                    resource.put("attributes", attributes);
                    data.add(resource);
                }
            }
        } catch (Exception e) {
            log.warn("VITO search from mobile QR failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "result_count", data.size()
        ));

        return ResponseEntity.ok(response);
    }
}
