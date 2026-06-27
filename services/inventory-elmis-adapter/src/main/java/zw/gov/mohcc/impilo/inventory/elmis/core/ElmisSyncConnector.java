package zw.gov.mohcc.impilo.inventory.elmis.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.inventory.elmis.config.ElmisAdapterProperties;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.entity.SyncStateEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes a real sync against the external national eLMIS. Fails closed: when no
 * real endpoint is configured (the default localhost placeholder), the sync ends
 * FAILED/NOT_LIVE rather than fabricating success or hanging IN_PROGRESS (G023).
 */
@Component
public class ElmisSyncConnector {

    private static final Logger log = LoggerFactory.getLogger(ElmisSyncConnector.class);

    /** The placeholder default that means "no real endpoint has been configured". */
    static final String DEFAULT_PLACEHOLDER_URL = "http://localhost:9080/api";

    private final ElmisAdapterProperties properties;
    private final RestTemplate elmisRestTemplate;

    public ElmisSyncConnector(ElmisAdapterProperties properties, RestTemplate elmisRestTemplate) {
        this.properties = properties;
        this.elmisRestTemplate = elmisRestTemplate;
    }

    boolean isLiveConfigured() {
        String url = properties.getConnector().getExternalUrl();
        return url != null && !url.isBlank() && !DEFAULT_PLACEHOLDER_URL.equals(url.trim());
    }

    public SyncResult sync(SyncStateEntity entity) {
        if (!isLiveConfigured()) {
            log.warn("eLMIS sync NOT_LIVE — endpoint not configured [syncType={}, facility={}]",
                    entity.getSyncType(), entity.getFacilityId());
            return SyncResult.notConfigured();
        }

        String url = properties.getConnector().getExternalUrl().trim() + "/sync";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", entity.getTenantId());
        payload.put("facilityId", entity.getFacilityId());
        payload.put("syncType", entity.getSyncType());
        payload.put("facilityCodeSystem", properties.getMapping().getFacilityCodeSystem());
        payload.put("itemCodeSystem", properties.getMapping().getItemCodeSystem());

        try {
            ResponseEntity<JsonNode> response = elmisRestTemplate.postForEntity(url, payload, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return SyncResult.failed("eLMIS endpoint returned HTTP " + response.getStatusCode().value());
            }
            int records = 0;
            JsonNode body = response.getBody();
            if (body != null && body.hasNonNull("recordsSynced")) {
                records = body.get("recordsSynced").asInt(0);
            }
            log.info("eLMIS sync COMPLETED [syncType={}, facility={}, records={}]",
                    entity.getSyncType(), entity.getFacilityId(), records);
            return SyncResult.completed(records);
        } catch (Exception e) {
            log.error("eLMIS sync FAILED [syncType={}, facility={}]: {}",
                    entity.getSyncType(), entity.getFacilityId(), e.getMessage());
            return SyncResult.failed("eLMIS sync error: " + e.getMessage());
        }
    }
}
