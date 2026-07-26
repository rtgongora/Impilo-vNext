package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.util.*;

/**
 * Administrative ward provisioning. Delegates to inpatient-service, the SoR for hospital wards.
 *
 * <p>POST /internal/v1/admin/wards — create a ward and materialise its bed rows.</p>
 *
 * <p><b>Repointed from tuso (route-contract completion).</b> This controller used to POST to
 * {@code tuso /v1/wards} — a path no service in the estate serves, so ward creation had never
 * worked. Ward <em>reads</em> already resolved to inpatient-service
 * ({@code BedController} → {@code GET /internal/v1/beds/wards} over {@code inpatient.ward}), so
 * reads and writes now agree. Tuso's only ward table is {@code zw_admin_ward} — electoral wards
 * behind the geography API — and building hospital wards there would have split ward truth across
 * two services that use the same word for different things.</p>
 *
 * <p>Authority: the shared {@code serviceRestTemplate} forwards the caller's bearer token, so this
 * runs as the signed-in administrator. No client-credentials token is minted here — doing so on a
 * user-initiated action would substitute service authority for the user's.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/wards")
public class AdminWardController {

    private final InpatientServiceClient inpatientClient;

    public AdminWardController(InpatientServiceClient inpatientClient) {
        this.inpatientClient = inpatientClient;
    }

    /**
     * Capability flags are {@link Boolean} rather than {@code boolean} so "not stated" stays
     * distinguishable from "stated false" all the way to the SoR, which treats an undeclared
     * capability as absent. They gate patient placement through the bed-safety evaluator.
     */
    public record CreateWardRequest(
            @NotBlank String name,
            @NotBlank String wardType,
            @NotNull @Min(1) @Max(200) Integer totalBeds,
            @NotNull UUID facilityId,
            String floorLabel,
            String genderDesignation,
            String ageGroup,
            Boolean isolationCapable,
            Boolean oxygenAvailable,
            Boolean monitoringCapable,
            Boolean icuCapable
    ) {}

    @PostMapping
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
        putIfPresent(wardData, "floorLabel", body.floorLabel());
        putIfPresent(wardData, "genderDesignation", body.genderDesignation());
        putIfPresent(wardData, "ageGroup", body.ageGroup());
        putIfPresent(wardData, "isolationCapable", body.isolationCapable());
        putIfPresent(wardData, "oxygenAvailable", body.oxygenAvailable());
        putIfPresent(wardData, "monitoringCapable", body.monitoringCapable());
        putIfPresent(wardData, "icuCapable", body.icuCapable());

        JsonNode result = inpatientClient.createWard(wardData);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Omit rather than forward nulls, so the SoR can tell "not stated" from "stated". */
    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            target.put(key, value);
        }
    }
}
