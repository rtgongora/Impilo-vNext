package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Administrative ward provisioning. Delegates to TusoServiceClient.
 *
 * <p>POST /internal/v1/admin/wards — create a ward and materialize bed rows for capacity setup.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/wards")
public class AdminWardController {

    private final TusoServiceClient tusoClient;

        this.tusoClient = tusoClient;
    }

    public record CreateWardRequest(
            @NotBlank String name,
            @NotBlank String wardType,
            @NotNull @Min(1) @Max(200) Integer totalBeds,
            @NotNull UUID facilityId
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createWard(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateWardRequest body) {

        Map<String, Object> wardData = new LinkedHashMap<>();
        wardData.put("name", body.name());
        wardData.put("wardType", body.wardType());
        wardData.put("totalBeds", body.totalBeds());
        wardData.put("facilityId", body.facilityId().toString());
        wardData.put("tenantId", tenantId);

        JsonNode result = tusoClient.createWard(wardData);

        // jdbcTemplate.update("""
        //     INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, ...) VALUES (...)
        //     """, ...);
        // for (int i = 1; i <= body.totalBeds(); i++) {
        //     jdbcTemplate.update("INSERT INTO beds (...) VALUES (...)", ...);
        // }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
