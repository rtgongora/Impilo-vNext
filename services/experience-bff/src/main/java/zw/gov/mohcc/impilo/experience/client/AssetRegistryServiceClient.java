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

import java.util.UUID;

/**
 * HTTP client for {@code asset-registry-service}.
 */
@Component
public class AssetRegistryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AssetRegistryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AssetRegistryServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.assetRegistryBaseUrl();
    }

    public JsonNode listAssets(String facilityId, String status, int cursor, int limit) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/assets")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (facilityId != null && !facilityId.isBlank()) {
            b.queryParam("facility_id", facilityId);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return getJson(b.toUriString());
    }

    public JsonNode getAsset(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/assets/" + assetId);
    }

    public JsonNode snapshotAssets(int cursor, int limit, String asOf) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/snapshots/assets")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (asOf != null && !asOf.isBlank()) {
            b.queryParam("as_of", asOf);
        }
        return getJson(b.toUriString());
    }

    public JsonNode upsertAsset(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/assets/" + assetId, body);
    }

    public JsonNode updateAssetStatus(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/assets/" + assetId + "/status", body);
    }

    public JsonNode deleteAsset(UUID assetId) {
        log.debug("Asset DELETE {}", assetId);
        ResponseEntity<JsonNode> r = restTemplate.exchange(
                baseUrl + "/internal/v1/assets/" + assetId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, null),
                JsonNode.class);
        return r.getBody();
    }

    public JsonNode getFixedAssetDetails(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/details");
    }

    public JsonNode getFixedAssetDepreciationSchedule(UUID assetId) {
        return getJson(baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/depreciation/schedule");
    }

    public JsonNode upsertFixedAssetDetails(UUID assetId, JsonNode body) {
        return exchangeJson(HttpMethod.PUT, baseUrl + "/internal/v1/fixed-assets/assets/" + assetId + "/details", body);
    }

    // ── Equipment operations (WS#5) ──────────────────────────────────────────

    public JsonNode listEquipment(String facilityId, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/equipment")
                .queryParam("page", page).queryParam("size", size);
        if (facilityId != null && !facilityId.isBlank()) b.queryParam("facility_id", facilityId);
        return getJson(b.toUriString());
    }

    public JsonNode registerEquipment(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment", body);
    }

    public JsonNode getEquipment(UUID equipmentId) {
        return getJson(baseUrl + "/internal/v1/equipment/" + equipmentId);
    }

    public JsonNode getEquipmentDetail(UUID equipmentId) {
        return getJson(baseUrl + "/internal/v1/equipment/" + equipmentId + "/detail");
    }

    public JsonNode updateEquipmentMetadata(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/metadata", body);
    }

    public JsonNode changeEquipmentStatus(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/status", body);
    }

    public JsonNode initiateTransfer(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/transfers", body);
    }

    public JsonNode approveTransfer(UUID transferId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/transfers/" + transferId + "/approve", null);
    }

    public JsonNode confirmReceipt(UUID transferId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/transfers/" + transferId + "/receive", null);
    }

    public JsonNode openMaintenance(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/maintenance", body);
    }

    public JsonNode updateMaintenance(UUID taskId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/maintenance/" + taskId, body);
    }

    public JsonNode completeMaintenance(UUID taskId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/maintenance/" + taskId + "/complete", body);
    }

    public JsonNode maintenanceDue() {
        return getJson(baseUrl + "/internal/v1/equipment/maintenance/due");
    }

    public JsonNode openMaintenanceList() {
        return getJson(baseUrl + "/internal/v1/equipment/maintenance/open");
    }

    public JsonNode recordCalibration(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/calibration", body);
    }

    public JsonNode calibrationDue() {
        return getJson(baseUrl + "/internal/v1/equipment/calibration/due");
    }

    public JsonNode reportFault(UUID equipmentId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/" + equipmentId + "/faults", body);
    }

    public JsonNode linkRito(UUID faultId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/faults/" + faultId + "/link-rito", body);
    }

    public JsonNode raiseAlert(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/alerts", body);
    }

    public JsonNode resolveAlert(UUID alertId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/alerts/" + alertId + "/resolve", null);
    }

    public JsonNode markAlertKhuluma(UUID alertId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/alerts/" + alertId + "/khuluma-requested", null);
    }

    public JsonNode activeAlerts() {
        return getJson(baseUrl + "/internal/v1/equipment/alerts/active");
    }

    public JsonNode createReadinessProfile(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/readiness/profiles", body);
    }

    public JsonNode readiness(String facilityRef) {
        return getJson(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/equipment/readiness")
                .queryParam("facility_ref", facilityRef).toUriString());
    }

    public JsonNode createKit(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/deployment-kits", body);
    }

    public JsonNode deployKit(UUID kitId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/deployment-kits/" + kitId + "/deploy", null);
    }

    public JsonNode returnKit(UUID kitId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/deployment-kits/" + kitId + "/return", body);
    }

    public JsonNode kits() {
        return getJson(baseUrl + "/internal/v1/equipment/deployment-kits");
    }

    public JsonNode openAudit(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/audits", body);
    }

    public JsonNode confirmAuditItem(UUID itemId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/audits/items/" + itemId, body);
    }

    public JsonNode completeAudit(UUID auditId) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/equipment/audits/" + auditId + "/complete", null);
    }

    public JsonNode audits(String facilityRef) {
        return getJson(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/equipment/audits")
                .queryParam("facility_ref", facilityRef).toUriString());
    }

    public JsonNode auditItems(UUID auditId) {
        return getJson(baseUrl + "/internal/v1/equipment/audits/" + auditId + "/items");
    }

    private JsonNode getJson(String url) {
        log.debug("Asset registry GET {}", url);
        ResponseEntity<JsonNode> r = restTemplate.getForEntity(url, JsonNode.class);
        return r.getBody();
    }

    private JsonNode exchangeJson(HttpMethod method, String url, JsonNode body) {
        log.debug("Asset registry {} {}", method, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JsonNode> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> r = restTemplate.exchange(url, method, entity, JsonNode.class);
        return r.getBody();
    }
}
