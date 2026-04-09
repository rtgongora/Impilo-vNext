package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Administrative ward provisioning.
 *
 * <p>POST /internal/v1/admin/wards — create a ward and materialize bed rows for capacity setup.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/wards")
public class AdminWardController {

    private final JdbcTemplate jdbcTemplate;

    public AdminWardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        UUID wardId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
                INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, floor, total_beds, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?, 'ACTIVE', ?, ?)
                """,
                wardId, tenantId, body.facilityId(), body.name(), body.wardType(), body.totalBeds(), now, now);

        for (int i = 1; i <= body.totalBeds(); i++) {
            UUID bedId = UUID.randomUUID();
            String bedNumber = String.valueOf(i);
            jdbcTemplate.update("""
                    INSERT INTO beds (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'STANDARD', 'AVAILABLE', ?, ?)
                    """,
                    bedId, tenantId, body.facilityId(), wardId, bedNumber, now, now);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", body.name());
        attributes.put("wardType", body.wardType());
        attributes.put("totalBeds", body.totalBeds());
        attributes.put("facilityId", body.facilityId().toString());
        attributes.put("status", "ACTIVE");

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of(
                        "id", wardId.toString(),
                        "type", "ward",
                        "attributes", attributes),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
