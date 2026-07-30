package zw.gov.mohcc.impilo.varapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a regulator's ACTIVE configuration from the Regulatory Configuration Registry, which lives
 * in organization-registry (NCZ-W1A, AD-1).
 *
 * <p>Two reads and no writes. Varapi consumes approved configuration; it never authors it, and it
 * has no way to express authoring through this client. Following the house convention established
 * by {@link TusoClient}, downstream unavailability logs and degrades — it never cascades — because
 * a council's registers being briefly un-reconcilable must not take varapi down.</p>
 *
 * <p>The distinction this client is careful about is <em>empty</em> versus <em>unknown</em>. A
 * council whose pack genuinely declares no registers and a council whose registry could not be
 * reached both produce "no register definitions", and treating those alike would let an outage
 * retire a council's entire register set. {@link #activeDefinitions} therefore returns an empty
 * {@link Optional} for "could not tell" and an empty list for "asked, and there are none".</p>
 */
@Service
public class OrgRegistryConfigClient {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryConfigClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public OrgRegistryConfigClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public boolean isEnabled() {
        VarapiProperties.OrgRegistryProperties p = varapiProperties.getOrgRegistry();
        return p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank();
    }

    /**
     * The configuration packs belonging to a regulator organisation.
     *
     * @return the packs, or {@link Optional#empty()} if the Registry could not be consulted
     */
    public Optional<List<JsonNode>> packsForOrganisation(UUID organizationId) {
        if (!isEnabled() || organizationId == null) {
            return Optional.empty();
        }
        String url = base() + "/v1/regulatory/config/organizations/" + organizationId + "/packs";
        return get(url).map(OrgRegistryConfigClient::toList);
    }

    /**
     * A pack's ACTIVE configuration — what a new case would be governed by.
     *
     * <p>This endpoint is the enforcement point for "only an ACTIVE release materialises": a pack
     * whose only release is DRAFT has no active release and answers 404, which arrives here as an
     * empty list rather than an error. A draft pack therefore cannot create a register, and the
     * materialiser gets that guarantee from the Registry rather than re-deciding it locally.</p>
     *
     * @return the active definitions (possibly empty), or {@link Optional#empty()} if the Registry
     *         could not be consulted
     */
    public Optional<List<JsonNode>> activeDefinitions(UUID packId) {
        if (!isEnabled() || packId == null) {
            return Optional.empty();
        }
        String url = base() + "/v1/regulatory/config/packs/" + packId + "/active";
        return get(url).map(OrgRegistryConfigClient::toList);
    }

    private static List<JsonNode> toList(JsonNode body) {
        if (body == null || !body.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        body.forEach(items::add);
        return items;
    }

    private Optional<JsonNode> get(String url) {
        TrustContext ctx;
        try {
            ctx = TrustContextHolder.require();
        } catch (RuntimeException e) {
            log.warn("Regulatory configuration read skipped — no trust context: {}", e.getMessage());
            return Optional.empty();
        }
        if (ctx.tenantId() == null) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-tenant-id", ctx.tenantId().toString());
            if (ctx.actorId() != null) {
                headers.set("x-actor-id", ctx.actorId());
            }
            if (ctx.actorType() != null) {
                headers.set("x-actor-type", ctx.actorType());
            }
            headers.set("x-purpose-of-use",
                    ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "REGISTRY_ADMIN");
            headers.set("x-device-fingerprint",
                    ctx.deviceFingerprint() != null ? ctx.deviceFingerprint() : "varapi-registry");
            if (ctx.correlationId() != null) {
                headers.set("x-correlation-id", ctx.correlationId().toString());
            }

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            return Optional.of(response.getBody());
        } catch (HttpStatusCodeException e) {
            // A pack with no ACTIVE release answers 404. That is an answer, not a failure: there is
            // nothing active, so there is nothing to materialise. Reporting it as unknown would
            // make an un-activated pack indistinguishable from an unreachable Registry.
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("Regulatory configuration read found nothing active at {}", url);
                return Optional.of(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode());
            }
            log.warn("Regulatory configuration read failed ({}) for {}: {}",
                    e.getStatusCode(), url, e.getMessage());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Regulatory configuration read failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private String base() {
        String b = varapiProperties.getOrgRegistry().getBaseUrl();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
