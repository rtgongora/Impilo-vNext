package zw.gov.mohcc.impilo.clinical.interpretation;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZIBO-backed {@link ObservationDefinitionProvider}: fetches the governed (incl. cited DRAFT)
 * {@code ObservationDefinition} artifacts from ZIBO, parses their {@code qualifiedInterval}s, and serves
 * them from an in-memory cache keyed by LOINC and local code. Refreshed on a TTL.
 *
 * <p>Fail-closed: any fetch/parse error leaves the previous cache in place (or empty on first load) and
 * returns no intervals — so vitals resolve to {@code NO_REFERENCE_RANGE} rather than a fabricated range.
 * Trust headers (tenant/correlation/authorization) are forwarded by the shared forwarding RestTemplate.</p>
 *
 * <p>Being a concrete bean, this supersedes {@link EmptyObservationDefinitionProvider}. Disable with
 * {@code impilo.clinical.zibo.enabled=false} to fall back to the empty provider.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.clinical.zibo.enabled", havingValue = "true", matchIfMissing = true)
public class ZiboObservationDefinitionProvider implements ObservationDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(ZiboObservationDefinitionProvider.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final long ttlMillis;

    private final ConcurrentHashMap<String, List<QualifiedInterval>> byLoinc = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<QualifiedInterval>> byLocal = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0L;
    private volatile boolean everLoaded = false;

    public ZiboObservationDefinitionProvider(
            @Qualifier("clinicalLlmRestTemplate") RestTemplate restTemplate,
            @Value("${impilo.clinical.zibo.base-url:http://localhost:8085}") String baseUrl,
            @Value("${impilo.clinical.zibo.cache-ttl-ms:600000}") long ttlMillis) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.ttlMillis = ttlMillis;
        log.info("ZIBO ObservationDefinition provider active (base={}, ttl={}ms).", baseUrl, ttlMillis);
    }

    @Override
    public List<QualifiedInterval> findIntervals(String loincCode, String localCode) {
        refreshIfStale();
        List<QualifiedInterval> out = new ArrayList<>();
        if (loincCode != null) {
            out.addAll(byLoinc.getOrDefault(loincCode, List.of()));
        }
        if (localCode != null) {
            out.addAll(byLocal.getOrDefault(localCode, List.of()));
        }
        return out;
    }

    private void refreshIfStale() {
        if (everLoaded && (now() - loadedAt) < ttlMillis) {
            return;
        }
        synchronized (this) {
            if (everLoaded && (now() - loadedAt) < ttlMillis) {
                return;
            }
            reload();
        }
    }

    private void reload() {
        try {
            String url = baseUrl + "/v1/artifacts/observation-definitions";
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            JsonNode data = body == null ? null : body.get("data");
            if (data == null || !data.isArray()) {
                log.warn("ZIBO ObservationDefinition fetch returned no data array — keeping existing cache.");
                markLoaded();
                return;
            }

            ConcurrentHashMap<String, List<QualifiedInterval>> newByLoinc = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<QualifiedInterval>> newByLocal = new ConcurrentHashMap<>();
            int count = 0;
            for (JsonNode artifact : data) {
                JsonNode content = artifact.get("content");
                if (content == null) {
                    continue;
                }
                String artifactId = text(artifact, "artifactId");
                String version = text(artifact, "version");
                String canonicalUrl = text(artifact, "canonicalUrl");
                List<QualifiedInterval> intervals =
                        ObservationDefinitionParser.parse(content, artifactId, version, canonicalUrl);
                if (intervals.isEmpty()) {
                    continue;
                }
                String loinc = ObservationDefinitionParser.loincOf(content);
                String local = ObservationDefinitionParser.localCodeOf(content);
                if (loinc != null) {
                    newByLoinc.computeIfAbsent(loinc, k -> new ArrayList<>()).addAll(intervals);
                }
                if (local != null) {
                    newByLocal.computeIfAbsent(local, k -> new ArrayList<>()).addAll(intervals);
                }
                count++;
            }
            byLoinc.clear();
            byLoinc.putAll(newByLoinc);
            byLocal.clear();
            byLocal.putAll(newByLocal);
            markLoaded();
            log.info("Loaded {} governed ObservationDefinitions from ZIBO ({} LOINC, {} local keys).",
                    count, byLoinc.size(), byLocal.size());
        } catch (Exception e) {
            // Fail-closed: keep whatever we had (empty on first load); never fabricate.
            log.warn("ZIBO ObservationDefinition fetch failed — serving {} (fail-closed): {}",
                    everLoaded ? "stale cache" : "no ranges", e.getMessage());
            markLoaded();
        }
    }

    private void markLoaded() {
        loadedAt = now();
        everLoaded = true;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode n = parent.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    // Visible for testing — allow a test to inject parsed cache without HTTP.
    void primeCacheForTest(Map<String, List<QualifiedInterval>> loinc, Map<String, List<QualifiedInterval>> local) {
        byLoinc.putAll(loinc);
        byLocal.putAll(local);
        markLoaded();
    }
}
