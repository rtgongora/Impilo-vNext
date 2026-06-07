package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for madi-service — blood donation, blood bank, crossmatch, transfusion, haemovigilance.
 */
@Component
public class MadiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MadiServiceClient.class);
    private static final String API = "/internal/v1/madi";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MadiServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.madiBaseUrl();
    }

    // ── Dashboard ────────────────────────────────────────────────────

    public JsonNode dashboard(String scope, String facilityId, String driveId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/dashboard")
                .queryParam("scope", scope != null ? scope : "local");
        if (facilityId != null && !facilityId.isBlank()) {
            builder.queryParam("facility_id", facilityId);
        }
        if (driveId != null && !driveId.isBlank()) {
            builder.queryParam("drive_id", driveId);
        }
        return get(builder.toUriString(), "dashboard");
    }

    // ── Donors ───────────────────────────────────────────────────────

    public JsonNode registerDonor(Map<String, Object> body) {
        return post(baseUrl + API + "/donors", body, "registerDonor");
    }

    public JsonNode donorByPerson(String personCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/donors/by-person")
                .queryParam("person_cpid", personCpid)
                .toUriString();
        return get(url, "donorByPerson");
    }

    public JsonNode screenDonor(String donorId, Map<String, Object> body) {
        return post(baseUrl + API + "/donors/" + donorId + "/screenings", body, "screenDonor");
    }

    public JsonNode deferDonor(String donorId, Map<String, Object> body) {
        return post(baseUrl + API + "/donors/" + donorId + "/deferrals", body, "deferDonor");
    }

    public JsonNode donorPreferences(String donorId, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, baseUrl + API + "/donors/" + donorId + "/preferences", body, "donorPreferences");
    }

    public JsonNode donorFeedback(String donorId, Map<String, Object> body) {
        return post(baseUrl + API + "/donors/" + donorId + "/feedback", body, "donorFeedback");
    }

    public JsonNode donorHistory(String donorId) {
        return get(baseUrl + API + "/donors/" + donorId + "/history", "donorHistory");
    }

    public JsonNode donorNextEligibility(String donorId) {
        return get(baseUrl + API + "/donors/" + donorId + "/next-eligibility", "donorNextEligibility");
    }

    public JsonNode drivesNearMe(String ndilaSiteRef, double radiusKm) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/donors/drives-near-me")
                .queryParam("ndila_site_ref", ndilaSiteRef)
                .queryParam("radius_km", radiusKm)
                .toUriString();
        return get(url, "drivesNearMe");
    }

    public JsonNode submitPreScreening(String donorId, Map<String, Object> body) {
        return post(baseUrl + API + "/donors/" + donorId + "/pre-screening", body, "submitPreScreening");
    }

    public JsonNode latestPreScreening(String donorId) {
        return get(baseUrl + API + "/donors/" + donorId + "/pre-screening/latest", "latestPreScreening");
    }

    // ── Donation drives ──────────────────────────────────────────────

    public JsonNode createDrive(Map<String, Object> body) {
        return post(baseUrl + API + "/drives", body, "createDrive");
    }

    public JsonNode listDrives() {
        return get(baseUrl + API + "/drives", "listDrives");
    }

    public JsonNode publishDrive(String driveId, Map<String, Object> body) {
        return post(baseUrl + API + "/drives/" + driveId + "/publish", body != null ? body : Map.of(), "publishDrive");
    }

    public JsonNode registerAtDrive(String driveId, Map<String, Object> body) {
        return post(baseUrl + API + "/drives/" + driveId + "/register", body, "registerAtDrive");
    }

    public JsonNode screenAtDrive(String driveId, Map<String, Object> body) {
        return post(baseUrl + API + "/drives/" + driveId + "/screen", body, "screenAtDrive");
    }

    public JsonNode recordDonation(String driveId, Map<String, Object> body) {
        return post(baseUrl + API + "/drives/" + driveId + "/donations", body, "recordDonation");
    }

    public JsonNode closeDrive(String driveId) {
        return post(baseUrl + API + "/drives/" + driveId + "/close", Map.of(), "closeDrive");
    }

    public JsonNode recordSyncConflict(Map<String, Object> body) {
        return post(baseUrl + API + "/drives/sync-conflicts", body, "recordSyncConflict");
    }

    public JsonNode resolveSyncConflict(String conflictId, Map<String, Object> body) {
        return post(baseUrl + API + "/drives/sync-conflicts/" + conflictId + "/resolve", body, "resolveSyncConflict");
    }

    // ── Processing ───────────────────────────────────────────────────

    public JsonNode receiveUnit(Map<String, Object> body) {
        return post(baseUrl + API + "/processing/receive", body, "receiveUnit");
    }

    public JsonNode testBatch(String batchId, Map<String, Object> body) {
        return post(baseUrl + API + "/processing/" + batchId + "/test", body, "testBatch");
    }

    public JsonNode quarantineBatch(String batchId, Map<String, Object> body) {
        return post(baseUrl + API + "/processing/" + batchId + "/quarantine", body, "quarantineBatch");
    }

    public JsonNode releaseBatch(String batchId, Map<String, Object> body) {
        return post(baseUrl + API + "/processing/" + batchId + "/release", body != null ? body : Map.of(), "releaseBatch");
    }

    public JsonNode prepareComponent(Map<String, Object> body) {
        return post(baseUrl + API + "/processing/components", body, "prepareComponent");
    }

    // ── Blood banks ──────────────────────────────────────────────────

    public JsonNode createBloodBank(Map<String, Object> body) {
        return post(baseUrl + API + "/blood-banks", body, "createBloodBank");
    }

    public JsonNode bloodBankStock(String bloodBankId) {
        return get(baseUrl + API + "/blood-banks/" + bloodBankId + "/stock", "bloodBankStock");
    }

    public JsonNode bloodBankMovement(String bloodBankId, Map<String, Object> body) {
        return post(baseUrl + API + "/blood-banks/" + bloodBankId + "/movements", body, "bloodBankMovement");
    }

    public JsonNode bloodBankReconciliation(String bloodBankId, Map<String, Object> body) {
        return post(baseUrl + API + "/blood-banks/" + bloodBankId + "/reconciliations", body, "bloodBankReconciliation");
    }

    public JsonNode bloodBankWastage(String bloodBankId, Map<String, Object> body) {
        return post(baseUrl + API + "/blood-banks/" + bloodBankId + "/wastage", body, "bloodBankWastage");
    }

    public JsonNode bloodBankReturn(String bloodBankId, Map<String, Object> body) {
        return post(baseUrl + API + "/blood-banks/" + bloodBankId + "/returns", body, "bloodBankReturn");
    }

    public JsonNode listFridges(String bloodBankId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/blood-banks/fridges")
                .queryParam("blood_bank_id", bloodBankId)
                .toUriString();
        return get(url, "listFridges");
    }

    public JsonNode fridgeReadings(String fridgeId) {
        return get(baseUrl + API + "/blood-banks/fridges/" + fridgeId + "/readings", "fridgeReadings");
    }

    public JsonNode syncFridgeIot(String fridgeId) {
        return post(baseUrl + API + "/blood-banks/fridges/" + fridgeId + "/sync-iot", Map.of(), "syncFridgeIot");
    }

    // ── Orders ───────────────────────────────────────────────────────

    public JsonNode listOrders(String status, String patientCpid) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/orders");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (patientCpid != null && !patientCpid.isBlank()) {
            builder.queryParam("patient_cpid", patientCpid);
        }
        return get(builder.toUriString(), "listOrders");
    }

    public JsonNode createOrder(Map<String, Object> body) {
        return post(baseUrl + API + "/orders", body, "createOrder");
    }

    public JsonNode getOrder(String orderId) {
        return get(baseUrl + API + "/orders/" + orderId, "getOrder");
    }

    public JsonNode submitOrder(String orderId) {
        return post(baseUrl + API + "/orders/" + orderId + "/submit", Map.of(), "submitOrder");
    }

    public JsonNode collectSample(String orderId, Map<String, Object> body) {
        return post(baseUrl + API + "/orders/" + orderId + "/samples", body, "collectSample");
    }

    public JsonNode crossmatchOrder(String orderId, Map<String, Object> body) {
        return post(baseUrl + API + "/orders/" + orderId + "/crossmatch", body, "crossmatchOrder");
    }

    public JsonNode reserveOrder(String orderId, Map<String, Object> body) {
        return post(baseUrl + API + "/orders/" + orderId + "/reserve", body, "reserveOrder");
    }

    public JsonNode issueOrder(String orderId, Map<String, Object> body) {
        return post(baseUrl + API + "/orders/" + orderId + "/issue", body, "issueOrder");
    }

    // ── Transfusions ─────────────────────────────────────────────────

    public JsonNode listTransfusions(String status, String patientCpid) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/transfusions");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (patientCpid != null && !patientCpid.isBlank()) {
            builder.queryParam("patient_cpid", patientCpid);
        }
        return get(builder.toUriString(), "listTransfusions");
    }

    public JsonNode startTransfusion(Map<String, Object> body) {
        return post(baseUrl + API + "/transfusions", body, "startTransfusion");
    }

    public JsonNode getTransfusionEpisode(String episodeId) {
        return get(baseUrl + API + "/transfusions/" + episodeId, "getTransfusionEpisode");
    }

    public JsonNode preVerifyTransfusion(String episodeId, Map<String, Object> body) {
        return post(baseUrl + API + "/transfusions/" + episodeId + "/pre-verify", body, "preVerifyTransfusion");
    }

    public JsonNode transfusionObservation(String episodeId, Map<String, Object> body) {
        return post(baseUrl + API + "/transfusions/" + episodeId + "/observations", body, "transfusionObservation");
    }

    public JsonNode completeTransfusion(String episodeId, Map<String, Object> body) {
        return post(baseUrl + API + "/transfusions/" + episodeId + "/complete", body, "completeTransfusion");
    }

    public JsonNode verifyTransfusion(String episodeId, Map<String, Object> body) {
        return post(baseUrl + API + "/transfusions/" + episodeId + "/verify", body != null ? body : Map.of(), "verifyTransfusion");
    }

    // ── Haemovigilance ───────────────────────────────────────────────

    public JsonNode reportReaction(Map<String, Object> body) {
        return post(baseUrl + API + "/haemovigilance/reactions", body, "reportReaction");
    }

    public JsonNode openHaemovigilanceCase(Map<String, Object> body) {
        return post(baseUrl + API + "/haemovigilance/cases", body, "openHaemovigilanceCase");
    }

    public JsonNode investigateCase(String caseId, Map<String, Object> body) {
        return post(baseUrl + API + "/haemovigilance/cases/" + caseId + "/investigate", body != null ? body : Map.of(), "investigateCase");
    }

    public JsonNode closeCase(String caseId, Map<String, Object> body) {
        return post(baseUrl + API + "/haemovigilance/cases/" + caseId + "/close", body != null ? body : Map.of(), "closeCase");
    }

    public JsonNode nationalHaemovigilanceDashboard() {
        return get(baseUrl + API + "/haemovigilance/national-dashboard", "nationalHaemovigilanceDashboard");
    }

    // ── Central bank ─────────────────────────────────────────────────

    public JsonNode centralBankMetrics() {
        return get(baseUrl + API + "/central-bank/metrics", "centralBankMetrics");
    }

    public JsonNode listEmergencyRedistributions() {
        return get(baseUrl + API + "/central-bank/emergency-redistributions", "listEmergencyRedistributions");
    }

    public JsonNode requestEmergencyRedistribution(Map<String, Object> body) {
        return post(baseUrl + API + "/central-bank/emergency-redistributions", body, "requestEmergencyRedistribution");
    }

    public JsonNode approveEmergencyRedistribution(String redistributionId, Map<String, Object> body) {
        return post(baseUrl + API + "/central-bank/emergency-redistributions/" + redistributionId + "/approve",
                body != null ? body : Map.of(), "approveEmergencyRedistribution");
    }

    public JsonNode dashboardForecast(String facilityId) {
        String url = baseUrl + API + "/dashboard/forecast";
        if (facilityId != null && !facilityId.isBlank()) {
            url += "?facility_id=" + facilityId;
        }
        return get(url, "dashboardForecast");
    }

    public JsonNode componentTerminologyPins(String jurisdiction) {
        String url = baseUrl + API + "/terminology/component-pins";
        if (jurisdiction != null && !jurisdiction.isBlank()) {
            url += "?jurisdiction=" + jurisdiction;
        }
        return get(url, "componentTerminologyPins");
    }

    public JsonNode initiateEmergencyHandoff(String redistributionId, Map<String, Object> body) {
        return post(baseUrl + API + "/central-bank/emergency-redistributions/" + redistributionId + "/handoff",
                body != null ? body : Map.of(), "initiateEmergencyHandoff");
    }

    private JsonNode get(String url, String operation) {
        log.debug("MADI {}: GET {}", operation, url);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    private JsonNode post(String url, Map<String, Object> body, String operation) {
        log.debug("MADI {}: POST {}", operation, url);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    private JsonNode exchange(HttpMethod method, String url, Map<String, Object> body, String operation) {
        log.debug("MADI {}: {} {}", operation, method, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, method, entity, JsonNode.class).getBody();
    }
}
