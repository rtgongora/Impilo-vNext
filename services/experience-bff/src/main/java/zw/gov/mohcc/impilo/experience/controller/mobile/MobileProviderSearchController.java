package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Mobile provider search endpoints.
 * POST /internal/v1/mobile/provider/search/qr - decode QR payload and resolve patient.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/search")
public class MobileProviderSearchController {

    private final JdbcTemplate jdbcTemplate;

    public MobileProviderSearchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT p.id, p.cpid, p.given_name, p.family_name, p.date_of_birth, p.sex,
                   p.national_id, p.phone, p.status, p.facility_id, p.created_at, p.updated_at
            FROM patients p
            WHERE p.tenant_id = ?
              AND (p.cpid = ? OR p.national_id = ? OR p.id::text = ?)
            LIMIT 10
            """, tenantId, request.qr_data(), request.qr_data(), request.qr_data());

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("cpid", row.get("cpid"));
            attributes.put("given_name", row.get("given_name"));
            attributes.put("family_name", row.get("family_name"));
            attributes.put("date_of_birth", row.get("date_of_birth"));
            attributes.put("sex", row.get("sex"));
            attributes.put("national_id", row.get("national_id"));
            attributes.put("phone", row.get("phone"));
            attributes.put("status", row.get("status"));
            attributes.put("facility_id", row.get("facility_id"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Patient");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

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
