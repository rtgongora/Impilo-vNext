package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the TUSO (Facility Registry) sovereign service.
 *
 * <p>Provides access to facility resources, bookings (scheduling),
 * and shift management. TUSO manages the canonical booking lifecycle
 * with conflict detection and resource calendar management.</p>
 */
@Component
public class TusoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TusoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tusoBaseUrl();
    }

    /**
     * Create a booking for a facility resource.
     *
     * @param resourceId the TUSO resource UUID
     * @param subjectRef the patient/subject reference (e.g. CPID)
     * @param purpose    booking purpose (e.g. "OPD Consultation")
     * @param startTime  booking start
     * @param endTime    booking end
     * @param notes      optional notes
     * @return the booking response from TUSO
     */
    public JsonNode createBooking(UUID resourceId, String subjectRef, String purpose,
                                  OffsetDateTime startTime, OffsetDateTime endTime, String notes) {
        String url = baseUrl + "/v1/internal/resources/" + resourceId + "/bookings";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectRef", subjectRef);
        body.put("purpose", purpose);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());
        if (notes != null) body.put("notes", notes);

        log.info("TUSO: Creating booking for resource={}, subject={}, start={}",
                resourceId, subjectRef, startTime);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * List bookings for a resource in a time range.
     */
    public JsonNode listBookings(UUID resourceId, OffsetDateTime from, OffsetDateTime to) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/resources/" + resourceId + "/bookings")
                .queryParam("from", from)
                .queryParam("to", to)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel a booking.
     */
    public JsonNode cancelBooking(UUID bookingId, String reason) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/bookings/" + bookingId)
                .queryParam("reason", reason)
                .toUriString();
        log.info("TUSO: Cancelling booking={}, reason={}", bookingId, reason);
        restTemplate.delete(url);
        return null;
    }

    /**
     * List resources for a facility.
     */
    public JsonNode listFacilityResources(long facilityId, String resourceType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/facilities/" + facilityId + "/resources");
        if (resourceType != null) builder.queryParam("resourceType", resourceType);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Lightweight facility legitimacy summary for cross-service gating.
     */
    public JsonNode getFacilityStatusSummary(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/status-summary";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Composite per-source facility legitimacy (WS-E): regulatory status + certificates +
     * every per-source verdict + the derived platform-access verdict. {@code facilityUuid} is the
     * canonical facility UUID (never the numeric surrogate id / facility code).
     */
    public JsonNode getFacilityStatusComposite(String facilityUuid) {
        String url = baseUrl + "/v1/facilities/" + facilityUuid + "/status-composite";
        log.info("TUSO: Getting facility status-composite id={}", facilityUuid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Generic GET against a whitelisted tuso internal path (HPA regulatory ops surface). */
    public JsonNode regulatoryGet(String subPath) {
        String url = baseUrl + "/v1/internal/facility-registry" + subPath;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return extractData(response);
    }

    /** Generic POST against a whitelisted tuso internal path (HPA regulatory ops surface). */
    public JsonNode regulatoryPost(String subPath, Object body) {
        String url = baseUrl + "/v1/internal/facility-registry" + subPath;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(java.net.URI.create(url),
                body != null ? body : java.util.Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Generic GET against the tuso facility-classification reconciliation surface. */
    public JsonNode classificationGet(String subPath, String query) {
        String url = baseUrl + "/v1/internal/facilities/classification" + subPath + qs(query);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return extractData(response);
    }

    /** Generic POST against the tuso facility-classification reconciliation surface. */
    public JsonNode classificationPost(String subPath, Object body) {
        String url = baseUrl + "/v1/internal/facilities/classification" + subPath;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(java.net.URI.create(url),
                body != null ? body : java.util.Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Public certificate verification passthrough (no data-envelope unwrap — raw disclosure). */
    public JsonNode verifyCertificatePublic(String code) {
        String url = baseUrl + "/v1/public/facilities/certificates/verify/"
                + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return response.getBody();
    }

    /** Public facility-directory search (disclosure-limited summaries; anonymous-safe). */
    public JsonNode publicFacilitySearch(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/public/facilities/search");
        params.forEach(builder::queryParam);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                builder.build().toUri(), HttpMethod.GET, anonymousSafeEntity(), JsonNode.class);
        return extractData(response);
    }

    /** Public facility profile (disclosure-limited view; anonymous-safe). */
    public JsonNode publicFacilityProfile(long id) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                java.net.URI.create(baseUrl + "/v1/public/facilities/" + id + "/profile"),
                HttpMethod.GET, anonymousSafeEntity(), JsonNode.class);
        return extractData(response);
    }

    private static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    /**
     * Downstream registries reject requests missing the mandatory trust headers (400).
     * Anonymous public-lane requests arrive with actor/trust headers stripped at the
     * edge, so the BFF supplies service-originated defaults here; when the caller DID
     * send platform headers (the web client always does), the shared forwarding
     * interceptor overwrites these defaults with the inbound values.
     */
    private HttpEntity<Void> anonymousSafeEntity() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Tenant-ID", PUBLIC_DEFAULT_TENANT);
        headers.set("X-Actor-ID", "public-gateway");
        headers.set("X-Actor-Type", "SYSTEM");
        headers.set("X-Purpose-Of-Use", "PUBLIC_ACCESS");
        headers.set("X-Device-Fingerprint", "public-gateway");
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        return new HttpEntity<>(headers);
    }

    /** Resolve a facility by its canonical cross-service UUID (tuso facility_uuid). */
    public JsonNode getFacilityByUid(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/by-uid/" + facilityUuid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Facility registry search (search-before-create). */
    public JsonNode searchFacilities(Map<String, Object> requestBody) {
        String url = baseUrl + "/v1/internal/facilities/search";
        log.info("TUSO: Searching facilities");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestBody, JsonNode.class);
        return extractData(response);
    }

    public JsonNode referralPathway(String facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/referral-pathway";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Create facility shell / provisional record. */
    public JsonNode createFacility(Map<String, Object> requestBody) {
        String url = baseUrl + "/v1/internal/facilities";
        log.info("TUSO: Creating facility");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestBody, JsonNode.class);
        return extractData(response);
    }

    /** Governed master health facility pack import (dry-run or execute). */
    public JsonNode importFacilityMasterPack(Map<String, Object> requestBody) {
        String url = baseUrl + "/v1/internal/facilities/import/master-pack";
        log.info("TUSO: Importing facility master pack dryRun={}", requestBody.get("dryRun"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestBody, JsonNode.class);
        return extractData(response);
    }

    /** Read pack data quality report from generated artefacts. */
    public JsonNode getFacilityMasterQualityReport() {
        String url = baseUrl + "/v1/internal/facilities/import/master-pack/quality-report";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** List persisted facility import runs/batches (tenant-scoped downstream). */
    public JsonNode listFacilityImportRuns() {
        String url = baseUrl + "/v1/internal/facilities/import/runs";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Read a single facility import run/batch detail by id. */
    public JsonNode getFacilityImportRun(long runId) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Persisted row-level outcomes for an import run (filters/pagination via query string). */
    public JsonNode getFacilityImportRunRows(long runId, String query) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/rows" + qs(query);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Review buckets (counts + previews) built from persisted rows. */
    public JsonNode getFacilityImportRunReview(long runId) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/review";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Duplicate rows grouped for review. */
    public JsonNode getFacilityImportRunDuplicates(long runId, String type) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/duplicates"
                + qs(type != null ? "type=" + type : null);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Rows excluded for missing facility code. */
    public JsonNode getFacilityImportRunMissingCode(long runId, String query) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/missing-code" + qs(query);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Import-eligible rows carrying acceptable-missing fields. */
    public JsonNode getFacilityImportRunAcceptableMissing(long runId, String query) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/acceptable-missing" + qs(query);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private static String qs(String query) {
        return (query == null || query.isBlank()) ? "" : "?" + query;
    }

    /** POST a review-decision mutation for an import row and return the updated row/result. */
    public JsonNode postImportRowAction(long runId, long rowId, String action, Object body) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/rows/" + rowId + "/" + action;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** POST apply-approved for a run (POST used since the request factory lacks PATCH). */
    public JsonNode postImportRunAction(long runId, String action, Object body) {
        String url = baseUrl + "/v1/internal/facilities/import/runs/" + runId + "/" + action;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Facility import provenance + configuration-completeness checklist. */
    public JsonNode getFacilityImportProvenance(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/import-provenance";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Facility detail by numeric facility id. */
    public JsonNode getFacility(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId;
        log.info("TUSO: Getting facility id={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** C4 FacilityModeContext read-model produced by TUSO (single producer). */
    public JsonNode getFacilityModeContext(long facilityId) {
        String url = baseUrl + "/v1/internal/facility-mode/" + facilityId + "/context";
        log.info("TUSO: Getting facility-mode context id={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Facility-mode setup-wizard state. */
    public JsonNode getFacilitySetupState(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/setup";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Update a single facility-mode setup-wizard step. */
    public JsonNode updateFacilitySetupStep(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/setup/steps";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** List facility units (departments). */
    public JsonNode listFacilityUnits(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/units";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Create a facility unit (department). */
    public JsonNode createFacilityUnit(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/units";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Update a facility unit (department) — POST alias on tuso for partial update. */
    public JsonNode updateFacilityUnit(long facilityId, long unitId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/units/" + unitId;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Retire a facility unit (department). */
    public JsonNode retireFacilityUnit(long facilityId, long unitId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/units/" + unitId + "/retire";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Facility capabilities — curated "services offered" registry truth. */
    public JsonNode listFacilityCapabilities(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/capabilities";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createFacilityCapability(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/capabilities";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateFacilityCapability(long facilityId, long capabilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/capabilities/" + capabilityId;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode retireFacilityCapability(long facilityId, long capabilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/capabilities/" + capabilityId + "/retire";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Facility readiness (infrastructure assessment). */
    public JsonNode getFacilityReadiness(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/readiness";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode upsertFacilityReadiness(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/readiness";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Tenant-scoped control-tower aggregate for scoped dashboards. */
    public JsonNode getControlTowerAggregate() {
        String url = baseUrl + "/v1/internal/control-tower/aggregate";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Facility operational summary (occupancy, recent telemetry, alert/shift counts). */
    public JsonNode getControlTowerFacilitySummary(long facilityId) {
        String url = baseUrl + "/v1/internal/control-tower/facilities/" + facilityId + "/summary";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Control-tower alerts, optionally facility/status filtered. */
    public JsonNode listControlTowerAlerts(Long facilityId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/control-tower/alerts");
        if (facilityId != null) b.queryParam("facilityId", facilityId);
        if (status != null) b.queryParam("status", status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Acknowledge a control-tower alert. */
    public JsonNode acknowledgeAlert(String alertId) {
        String url = baseUrl + "/v1/internal/control-tower/alerts/" + alertId + "/acknowledge";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /** List facility service points. */
    public JsonNode listServicePoints(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/service-points";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Create a facility service point. */
    public JsonNode createServicePoint(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/service-points";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Shift Management ─────────────────────────────────────────────

    /** Get current active shift for a user. */
    public JsonNode getCurrentShift(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/shifts/current")
                .queryParam("userId", userId)
                .toUriString();
        log.info("TUSO: Getting current shift for user={}", userId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Start a new shift. */
    public JsonNode startShift(Map<String, Object> body) {
        String url = baseUrl + "/v1/shifts/start";
        log.info("TUSO: Starting shift for user={}", body.get("user_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** End a shift. */
    public JsonNode endShift(String shiftId, Map<String, Object> body) {
        String url = baseUrl + "/v1/shifts/" + shiftId + "/end";
        log.info("TUSO: Ending shift={}", shiftId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** List shifts for a facility (used by supervisor surfaces). */
    public JsonNode listFacilityShifts(String facilityId, String status, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/facilities/" + facilityId + "/shifts")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.toUriString();
        log.info("TUSO: Listing shifts facilityId={} status={}", facilityId, status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Staffing ──────────────────────────────────────────────────────

    /** Get roster for a week. */
    public JsonNode getRosterWeek(String facilityId, String weekStart, String workspaceId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/roster-week")
                .queryParam("facilityId", facilityId)
                .queryParam("weekStart", weekStart);
        if (workspaceId != null && !workspaceId.isBlank()) builder.queryParam("workspaceId", workspaceId);
        log.info("TUSO: Getting roster week facilityId={}, weekStart={}", facilityId, weekStart);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** List on-call assignments for a facility/week. */
    public JsonNode listOnCall(String facilityId, String weekStart) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/on-call")
                .queryParam("facilityId", facilityId)
                .queryParam("weekStart", weekStart)
                .toUriString();
        log.info("TUSO: Listing on-call facilityId={}, weekStart={}", facilityId, weekStart);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** List swap requests for a facility. */
    public JsonNode listSwapRequests(String facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/staffing/on-call/swaps")
                .queryParam("facilityId", facilityId)
                .toUriString();
        log.info("TUSO: Listing swap requests facilityId={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Create a swap request. */
    public JsonNode createSwapRequest(Map<String, Object> body) {
        String url = baseUrl + "/v1/staffing/on-call/swaps";
        log.info("TUSO: Creating swap request");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Update a swap request (approve/decline). */
    public JsonNode updateSwapRequest(String swapId, Map<String, Object> body) {
        String url = baseUrl + "/v1/staffing/on-call/swaps/" + swapId;
        log.info("TUSO: Updating swap request={}", swapId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Appointments/Scheduling ───────��───────────────────────────────

    /** List appointments with filters. */
    public JsonNode listAppointments(String patientId, String facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments")
                .queryParam("page", page)
                .queryParam("size", size);
        if (patientId != null) builder.queryParam("patientId", patientId);
        if (facilityId != null) builder.queryParam("facilityId", facilityId);
        if (status != null) builder.queryParam("status", status);
        log.info("TUSO: Listing appointments");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Create an appointment. */
    public JsonNode createAppointment(Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments";
        log.info("TUSO: Creating appointment");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Get a single appointment by ID. */
    public JsonNode getAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId;
        log.info("TUSO: Getting appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Confirm an appointment. */
    public JsonNode confirmAppointment(String appointmentId) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/confirm";
        log.info("TUSO: Confirming appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /** Cancel an appointment. */
    public JsonNode cancelAppointment(String appointmentId, String reason) {
        String url = baseUrl + "/v1/appointments/" + appointmentId + "/cancel";
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        log.info("TUSO: Cancelling appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Citizen Appointments ──────────────────────────────────────────

    /** List appointments for a citizen (patient CPID). */
    public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments/citizen/" + cpid)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        log.info("TUSO: Listing citizen appointments for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Create a citizen appointment request. */
    public JsonNode createCitizenAppointment(String cpid, Map<String, Object> body) {
        String url = baseUrl + "/v1/appointments/citizen/" + cpid;
        log.info("TUSO: Creating citizen appointment for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Ward Management ───────────────────────────────────────────────

    /** Create a ward (delegates to TUSO facility registry). */
    public JsonNode createWard(Map<String, Object> body) {
        String url = baseUrl + "/v1/wards";
        log.info("TUSO: Creating ward name={}", body.get("name"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // Regulatory facility lifecycle operations

    public JsonNode listFacilityRegistryFacilities(String search, String regulatoryStatus, String facilityType,
                                                   String province, String district, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/facility-registry/facilities")
                .queryParam("page", page)
                .queryParam("size", size);
        if (search != null && !search.isBlank()) builder.queryParam("search", search);
        if (regulatoryStatus != null && !regulatoryStatus.isBlank()) builder.queryParam("regulatoryStatus", regulatoryStatus);
        if (facilityType != null && !facilityType.isBlank()) builder.queryParam("facilityType", facilityType);
        if (province != null && !province.isBlank()) builder.queryParam("province", province);
        if (district != null && !district.isBlank()) builder.queryParam("district", district);
        // Pass a URI, not a String: getForEntity(String) re-encodes the already-encoded
        // string, so any search term with a space arrived double-encoded and matched nothing.
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFacilityRegulatoryProfile(String facilityId) {
        String url = baseUrl + "/v1/internal/facility-registry/facilities/" + facilityId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createFacilityApplication(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/applications", body);
    }

    public JsonNode submitFacilityApplication(String applicationId) {
        return postJson(baseUrl + "/v1/internal/facility-registry/applications/" + applicationId + "/submit", null);
    }

    public JsonNode markFacilityApplicationReadyForInspection(String applicationId) {
        return postJson(baseUrl + "/v1/internal/facility-registry/applications/" + applicationId + "/ready-for-inspection", null);
    }

    public JsonNode uploadFacilityDocument(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/documents", body);
    }

    public JsonNode listChecklistTemplates(String inspectionType, String facilityType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/facility-registry/checklist-templates")
                .queryParam("inspectionType", inspectionType);
        if (facilityType != null && !facilityType.isBlank()) builder.queryParam("facilityType", facilityType);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode scheduleFacilityInspection(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/inspections", body);
    }

    public JsonNode recordFacilityInspection(String inspectionId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/inspections/" + inspectionId + "/record", body);
    }

    public JsonNode updateComplianceAction(String actionId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/compliance-actions/" + actionId, body);
    }

    public JsonNode recordCommitteeDecision(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/committee-reviews", body);
    }

    public JsonNode openEnforcementCase(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/facility-registry/enforcement-cases", body);
    }

    public JsonNode getFacilityRegulatoryDashboard() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/internal/facility-registry/dashboard/summary", JsonNode.class);
        return extractData(response);
    }

    // ── Zimbabwe admin reference (COD-AB / ZIMSTAT import) ────────────

    public JsonNode listZwDistricts(String provinceCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/geo/zw/districts")
                .queryParam("provinceCode", provinceCode)
                .toUriString();
        log.info("TUSO: list ZW districts provinceCode={}", provinceCode);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listZwWards(String districtCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/geo/zw/wards")
                .queryParam("districtCode", districtCode)
                .toUriString();
        log.info("TUSO: list ZW wards districtCode={}", districtCode);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode bulkUpsertZwGeo(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/geo/zw/bulk";
        log.info("TUSO: bulk upsert ZW geo");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Workspace Management ─────────────────────────────────────────

    /** List active workspaces for a facility. */
    public JsonNode listWorkspaces(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/workspaces";
        log.info("TUSO: Listing workspaces facilityId={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Get a workspace by UUID. */
    public JsonNode getWorkspace(UUID workspaceId) {
        String url = baseUrl + "/v1/internal/workspaces/" + workspaceId;
        log.info("TUSO: Getting workspace id={}", workspaceId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Facility Configuration Console (TUSO SoR) ────────────────────
    // NEW methods added for the Facility Configuration Console. Append-only: no existing method was
    // renamed or altered. Distinct names avoid collision with the import/queue-definition methods owned
    // by the absorption/materialisation workstream.

    /** Facility configuration summary (lifecycle state + counts + missing sections). */
    public JsonNode getFacilityConfiguration(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Facility setup/readiness checklist. */
    public JsonNode getFacilitySetupChecklist(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration/setup-checklist";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Queue definitions derived from a facility's active service points (TUSO-owned, read-only). */
    public JsonNode getFacilityConfigQueueDefinitions(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration/queue-definitions";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** TUSO's downstream-readiness view (queue-definition counts + last config change). */
    public JsonNode getFacilityDownstreamReadiness(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration/downstream-readiness";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Facility configuration change history (outbox-derived). */
    public JsonNode getFacilityConfigurationHistory(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration/history";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Emit a facility configuration-update event so downstream (PCT) can reconcile derived queues. */
    public JsonNode publishFacilityConfiguration(long facilityId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/configuration/publish";
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    /**
     * Partial-update a service point. Uses TUSO's POST update alias
     * ({@code /service-points/{id}/update}) rather than PATCH, because the shared RestTemplate is backed
     * by {@code SimpleClientHttpRequestFactory}, which does not support the PATCH method at runtime.
     * Same partial-merge semantics as PATCH on the TUSO side.
     */
    public JsonNode updateServicePoint(long facilityId, String servicePointId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/service-points/" + servicePointId + "/update";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Retire a service point. */
    public JsonNode retireServicePoint(long facilityId, String servicePointId) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/service-points/" + servicePointId + "/retire";
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    /** Create a workspace for a facility. */
    public JsonNode createWorkspace(long facilityId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/workspaces";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    /** Update a workspace (TUSO PUT). */
    public JsonNode updateWorkspace(String workspaceId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/workspaces/" + workspaceId;
        return extractData(restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class));
    }

    /** Retire a workspace. */
    public JsonNode retireWorkspace(String workspaceId) {
        String url = baseUrl + "/v1/internal/workspaces/" + workspaceId + "/retire";
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    // ── Locality gazetteer ───────────────────────────────────────────

    public JsonNode searchLocalities(String districtCode, String q, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/localities/search")
                .queryParam("districtCode", districtCode)
                .queryParam("q", q)
                .queryParam("limit", limit)
                .toUriString();
        log.info("TUSO: search localities districtCode={}", districtCode);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPendingLocalityProposals() {
        String url = baseUrl + "/v1/internal/localities/proposals/pending";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode proposeLocality(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/localities/proposals";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode approveLocalityProposal(long proposalId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/localities/proposals/" + proposalId + "/approve";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode rejectLocalityProposal(long proposalId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/localities/proposals/" + proposalId + "/reject";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ------------------------------------------------------------------
    // Virtual service registry (virtual hospitals) — TUSO SoR
    // ------------------------------------------------------------------

    /** List virtual service-delivery entities (virtual hospitals). Throws on transport failure. */
    public JsonNode listVirtualServices() {
        String url = baseUrl + "/v1/internal/virtual-services";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** One virtual service by its vs_uid. Throws on transport failure / 404. */
    public JsonNode getVirtualService(String vsUid) {
        String url = baseUrl + "/v1/internal/virtual-services/" + vsUid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Governed partial update (seam/queue-def edits) via the POST alias. */
    public JsonNode updateVirtualService(String vsUid, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/virtual-services/" + vsUid + "/update", body);
    }

    /** Add a routing seam to a virtual service. */
    public JsonNode addVirtualServiceRoutingSeam(String vsUid, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/internal/virtual-services/" + vsUid + "/routing-seams", body);
    }

    /** Request activation (fail-closed on TUSO side; lands at ACTIVATION_REQUESTED). */
    public JsonNode activateVirtualService(String vsUid) {
        return postJson(baseUrl + "/v1/internal/virtual-services/" + vsUid + "/activate", Map.of());
    }

    /** Suspend a virtual service. */
    public JsonNode suspendVirtualService(String vsUid, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        return postJson(baseUrl + "/v1/internal/virtual-services/" + vsUid + "/suspend", body);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }
}
