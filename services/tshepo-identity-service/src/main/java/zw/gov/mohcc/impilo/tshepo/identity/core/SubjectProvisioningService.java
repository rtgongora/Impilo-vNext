package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provisional clinical-subject minting for unknown patients (trauma, mass-casualty,
 * unconscious arrivals) — Identity Contract §7.2 step 4.
 *
 * <p>Clinical callers may not receive a Health ID, even transiently. This service is
 * the translator seam: it orchestrates the VITO identity-plane mint (which returns the
 * new HID), immediately maps HID→CPID via {@code id_mapping}, and hands the caller
 * only {@code {cpid, status}}. tshepo-identity is thereby the only party that ever
 * sees the mint's Health ID.</p>
 */
@Service
public class SubjectProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(SubjectProvisioningService.class);

    private final RestTemplate vitoRestTemplate;
    private final IdResolutionService idResolutionService;
    private final ObjectMapper objectMapper;

    public SubjectProvisioningService(@Qualifier("vitoRestTemplate") RestTemplate vitoRestTemplate,
                                      IdResolutionService idResolutionService,
                                      ObjectMapper objectMapper) {
        this.vitoRestTemplate = vitoRestTemplate;
        this.idResolutionService = idResolutionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Mint a provisional identity for an unknown patient and return only the CPID.
     *
     * @param tenantId       tenant scope (trusted header)
     * @param actorId        acting user/service for audit (may be null)
     * @param idempotencyKey caller idempotency key, forwarded to VITO so retried
     *                       mints do not create duplicate identities
     * @param mintRequest    optional estimated demographics + scene descriptor
     */
    public Map<String, Object> provisionSubject(UUID tenantId, String actorId,
                                                String idempotencyKey,
                                                Map<String, Object> mintRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // The trust-forwarding interceptor carries tenant/actor/pod context; these
        // three mark this call as the trust core's own internal orchestration.
        headers.set("X-Access-Mode", "INTERNAL");
        headers.set("X-Service-Id", "tshepo-identity");
        headers.set("Idempotency-Key",
                idempotencyKey != null && !idempotencyKey.isBlank()
                        ? idempotencyKey : UUID.randomUUID().toString());
        headers.set("X-Tenant-ID", tenantId.toString());
        if (actorId != null && !actorId.isBlank()) {
            headers.set("X-Actor-Id", actorId);
        }

        ResponseEntity<String> response = vitoRestTemplate.postForEntity(
                "/internal/v1/identities/provisional",
                new HttpEntity<>(mintRequest != null ? mintRequest : Map.of(), headers),
                String.class);
        if (response.getStatusCode() != HttpStatus.CREATED
                && response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("VITO provisional mint failed: " + response.getStatusCode());
        }

        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            String healthIdText = body.path("health_id").asText(null);
            if (healthIdText == null) {
                throw new IllegalStateException("VITO provisional mint returned no health_id");
            }
            UUID healthId = UUID.fromString(healthIdText);

            IdMappingEntity mapping = idResolutionService.findOrCreateMapping(tenantId, healthId, null);

            // The Health ID stops here — clinical callers receive the CPID only.
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cpid", mapping.getCpid().toString());
            result.put("status", body.path("status").asText("PROVISIONAL"));
            if (body.hasNonNull("descriptor")) {
                result.put("descriptor", body.get("descriptor").asText());
            }
            log.info("Provisioned clinical subject cpid={} (tenant={})", mapping.getCpid(), tenantId);
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process VITO provisional mint response", e);
        }
    }
}
