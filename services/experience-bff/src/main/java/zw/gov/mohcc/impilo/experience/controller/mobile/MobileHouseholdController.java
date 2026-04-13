package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile household and community health endpoints.
 * GET  /internal/v1/mobile/provider/households?facility_id=        - list households
 * GET  /internal/v1/mobile/provider/households/{id}                - get single
 * POST /internal/v1/mobile/provider/households                     - register new household
 * POST /internal/v1/mobile/provider/households/visits              - record community visit
 * GET  /internal/v1/mobile/provider/households/{id}/visits         - visit history
 * POST /internal/v1/mobile/provider/screenings                     - record screening
 * POST /internal/v1/mobile/provider/immunizations                  - record immunization
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileHouseholdController {

    private final CommunityServiceClient communityClient;
    private final VitoServiceClient vitoClient;

    public MobileHouseholdController(CommunityServiceClient communityClient, VitoServiceClient vitoClient) {
        this.communityClient = communityClient;
        this.vitoClient = vitoClient;
    }

    public record RegisterHouseholdRequest(
            @NotBlank String facility_id,
            @NotBlank String head_of_household,
            @NotBlank String address,
            String gps_latitude,
            String gps_longitude,
            Integer member_count,
            String ward,
            String village
    ) {}

    public record RecordVisitRequest(
            @NotBlank String household_id,
            @NotBlank String visited_by,
            @NotBlank String visit_type,
            String findings,
            String actions_taken,
            String follow_up_required,
            String follow_up_date
    ) {}

    public record RecordScreeningRequest(
            @NotBlank String patient_id,
            @NotBlank String encounter_id,
            @NotBlank String screening_type,
            @NotBlank String result,
            String notes,
            String risk_level
    ) {}

    public record RecordImmunizationRequest(
            @NotBlank String patient_id,
            @NotBlank String encounter_id,
            @NotBlank String vaccine_code,
            @NotBlank String vaccine_name,
            @NotNull Integer dose_number,
            String lot_number,
            String site,
            String route
    ) {}

    @GetMapping("/households")
    public ResponseEntity<Map<String, Object>> listHouseholds(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @GetMapping("/households/{id}")
    public ResponseEntity<Map<String, Object>> getHousehold(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/households")
    public ResponseEntity<Map<String, Object>> registerHousehold(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RegisterHouseholdRequest request) {

        UUID householdId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("head_of_household", request.head_of_household());
        attributes.put("address", request.address());
        attributes.put("gps_latitude", request.gps_latitude());
        attributes.put("gps_longitude", request.gps_longitude());
        attributes.put("member_count", request.member_count());
        attributes.put("ward", request.ward());
        attributes.put("village", request.village());
        attributes.put("status", "ACTIVE");
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", householdId.toString(),
                "type", "Household",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/households/visits")
    public ResponseEntity<Map<String, Object>> recordVisit(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordVisitRequest request) {

        UUID visitId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("household_id", request.household_id());
        attributes.put("visited_by", request.visited_by());
        attributes.put("visit_type", request.visit_type());
        attributes.put("findings", request.findings());
        attributes.put("actions_taken", request.actions_taken());
        attributes.put("follow_up_required", request.follow_up_required());
        attributes.put("follow_up_date", request.follow_up_date());
        attributes.put("visited_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", visitId.toString(),
                "type", "HouseholdVisit",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/households/{id}/visits")
    public ResponseEntity<Map<String, Object>> visitHistory(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/screenings")
    public ResponseEntity<Map<String, Object>> recordScreening(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordScreeningRequest request) {

        UUID screeningId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String riskLevel = request.risk_level() != null ? request.risk_level() : "NORMAL";

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("screening_type", request.screening_type());
        attributes.put("result", request.result());
        attributes.put("notes", request.notes());
        attributes.put("risk_level", riskLevel);
        attributes.put("screened_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", screeningId.toString(),
                "type", "Screening",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/immunizations")
    public ResponseEntity<Map<String, Object>> recordImmunization(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordImmunizationRequest request) {

        UUID immunizationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("vaccine_code", request.vaccine_code());
        attributes.put("vaccine_name", request.vaccine_name());
        attributes.put("dose_number", request.dose_number());
        attributes.put("lot_number", request.lot_number());
        attributes.put("site", request.site());
        attributes.put("route", request.route());
        attributes.put("administered_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", immunizationId.toString(),
                "type", "Immunization",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Map<String, Object> toHouseholdResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("head_of_household", row.get("head_of_household"));
        attributes.put("address", row.get("address"));
        attributes.put("gps_latitude", row.get("gps_latitude"));
        attributes.put("gps_longitude", row.get("gps_longitude"));
        attributes.put("member_count", row.get("member_count"));
        attributes.put("ward", row.get("ward"));
        attributes.put("village", row.get("village"));
        attributes.put("status", row.get("status"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Household");
        resource.put("attributes", attributes);
        return resource;
    }
}
