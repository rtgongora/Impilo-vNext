package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to
 * CommunityServiceClient + VitoServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileHouseholdController {

    // STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to CommunityServiceClient + VitoServiceClient
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final CommunityServiceClient communityClient;
    private final VitoServiceClient vitoClient;

    public MobileHouseholdController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                     CommunityServiceClient communityClient, VitoServiceClient vitoClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, head_of_household, address, gps_latitude, gps_longitude,
                   member_count, ward, village, status, created_at, updated_at
            FROM households
            WHERE tenant_id = ? AND facility_id = ?::uuid
            ORDER BY head_of_household ASC
            LIMIT ? OFFSET ?
            """, tenantId, facilityId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM households WHERE tenant_id = ? AND facility_id = ?::uuid
            """, Long.class, tenantId, facilityId);

        List<Map<String, Object>> data = rows.stream().map(row -> toHouseholdResource(row)).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/households/{id}")
    public ResponseEntity<Map<String, Object>> getHousehold(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, head_of_household, address, gps_latitude, gps_longitude,
                   member_count, ward, village, status, created_at, updated_at
            FROM households
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Household not found: " + id);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toHouseholdResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/households")
    @Transactional
    public ResponseEntity<Map<String, Object>> registerHousehold(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RegisterHouseholdRequest request) {

        UUID householdId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO households
                (id, tenant_id, facility_id, head_of_household, address, gps_latitude, gps_longitude,
                 member_count, ward, village, status, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
                householdId, tenantId, request.facility_id(), request.head_of_household(),
                request.address(), request.gps_latitude(), request.gps_longitude(),
                request.member_count(), request.ward(), request.village(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.household.registered.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Household",
                householdId.toString(),
                Map.of(
                        "household_id", householdId.toString(),
                        "facility_id", request.facility_id(),
                        "head_of_household", request.head_of_household(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

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
    @Transactional
    public ResponseEntity<Map<String, Object>> recordVisit(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordVisitRequest request) {

        UUID visitId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO household_visits
                (id, tenant_id, household_id, visited_by, visit_type, findings,
                 actions_taken, follow_up_required, follow_up_date, visited_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?::date, ?, ?, ?)
            """,
                visitId, tenantId, request.household_id(), request.visited_by(),
                request.visit_type(), request.findings(), request.actions_taken(),
                request.follow_up_required(), request.follow_up_date(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.household_visit.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "HouseholdVisit",
                visitId.toString(),
                Map.of(
                        "visit_id", visitId.toString(),
                        "household_id", request.household_id(),
                        "visit_type", request.visit_type()
                ),
                Map.of()
        );

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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, household_id, visited_by, visit_type, findings, actions_taken,
                   follow_up_required, follow_up_date, visited_at, created_at, updated_at
            FROM household_visits
            WHERE tenant_id = ? AND household_id = ?
            ORDER BY visited_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, id, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM household_visits WHERE tenant_id = ? AND household_id = ?
            """, Long.class, tenantId, id);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("household_id", row.get("household_id"));
            attributes.put("visited_by", row.get("visited_by"));
            attributes.put("visit_type", row.get("visit_type"));
            attributes.put("findings", row.get("findings"));
            attributes.put("actions_taken", row.get("actions_taken"));
            attributes.put("follow_up_required", row.get("follow_up_required"));
            attributes.put("follow_up_date", row.get("follow_up_date"));
            attributes.put("visited_at", row.get("visited_at"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "HouseholdVisit");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/screenings")
    @Transactional
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

        jdbcTemplate.update("""
            INSERT INTO screenings
                (id, tenant_id, patient_id, encounter_id, screening_type, result,
                 notes, risk_level, screened_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            """,
                screeningId, tenantId, request.patient_id(), request.encounter_id(),
                request.screening_type(), request.result(), request.notes(),
                riskLevel, now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.screening.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Screening",
                screeningId.toString(),
                Map.of(
                        "screening_id", screeningId.toString(),
                        "patient_id", request.patient_id(),
                        "screening_type", request.screening_type(),
                        "result", request.result(),
                        "risk_level", riskLevel
                ),
                Map.of()
        );

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
    @Transactional
    public ResponseEntity<Map<String, Object>> recordImmunization(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordImmunizationRequest request) {

        UUID immunizationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO immunizations
                (id, tenant_id, patient_id, encounter_id, vaccine_code, vaccine_name,
                 dose_number, lot_number, site, route, administered_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                immunizationId, tenantId, request.patient_id(), request.encounter_id(),
                request.vaccine_code(), request.vaccine_name(), request.dose_number(),
                request.lot_number(), request.site(), request.route(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.immunization.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Immunization",
                immunizationId.toString(),
                Map.of(
                        "immunization_id", immunizationId.toString(),
                        "patient_id", request.patient_id(),
                        "vaccine_code", request.vaccine_code(),
                        "vaccine_name", request.vaccine_name(),
                        "dose_number", request.dose_number()
                ),
                Map.of()
        );

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
