package zw.gov.mohcc.impilo.inpatient.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to inventory-service (DURA, 8098) for the surgical implant register. inventory owns
 * the implant UNIT registry (UDI/serial/lot) + the patient-implant link — theatre records the implant fact
 * and inventory issues/stamps the traceable unit. Mirrors {@link InventoryConsumptionClient}: it NEVER
 * fabricates a unit id; an outage returns an UNAVAILABLE view and the theatre link stays UNAVAILABLE.
 */
@Service
public class InventoryImplantClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryImplantClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryImplantClient(RestTemplate inpatientRestTemplate,
                                  @Value("${inpatient.integration.inventory.base-url:http://localhost:8098}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Peer ids + projection returned by inventory when an implant unit is recorded against a patient. */
    public record ImplantView(String implantUnitId, String patientImplantId, String udi, String serialNumber,
                              String lotNumber, String status, boolean available) {
        static ImplantView unavailable() { return new ImplantView(null, null, null, null, null, null, false); }
    }

    /** Record an implant unit as implanted in a patient/episode. Returns the inventory peer ids or UNAVAILABLE. */
    @SuppressWarnings("unchecked")
    public ImplantView recordImplant(String patientCpid, String episodeRef, String udi, String serialNumber,
                                     String lotNumber, String deviceType, String bodySite, String laterality,
                                     String implantedBy, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patientCpid", patientCpid);
            payload.put("episodeRef", episodeRef);
            payload.put("udi", udi);
            payload.put("serialNumber", serialNumber);
            payload.put("lotNumber", lotNumber);
            payload.put("deviceType", deviceType);
            payload.put("bodySite", bodySite);
            payload.put("laterality", laterality);
            payload.put("implantedBy", implantedBy);
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/internal/implants/record",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Object data = unwrap(resp.getBody());
            if (data instanceof Map<?, ?> m) {
                Map<String, Object> dm = (Map<String, Object>) m;
                log.info("INPATIENT-THEATRE: recorded implant unit {} (UDI {}) for {}",
                        dm.get("implantUnitId"), udi, patientCpid);
                return new ImplantView(str(dm, "implantUnitId"), str(dm, "patientImplantId"),
                        str(dm, "udi"), str(dm, "serialNumber"), str(dm, "lotNumber"),
                        str(dm, "status"), dm.get("implantUnitId") != null);
            }
            return ImplantView.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: inventory implant register unavailable for UDI {} (best-effort): {}",
                    udi, e.getMessage());
            return ImplantView.unavailable();
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : null;
    }

    private static Object unwrap(Map<String, Object> body) {
        if (body == null) return null;
        return body.containsKey("data") ? body.get("data") : body;
    }

    private HttpHeaders trustHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "inpatient");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType() != null ? ctx.actorType() : "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "TREATMENT");
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_ACTOR_TYPE, "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
