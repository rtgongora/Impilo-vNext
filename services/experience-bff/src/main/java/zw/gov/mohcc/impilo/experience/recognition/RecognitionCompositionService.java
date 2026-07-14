package zw.gov.mohcc.impilo.experience.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composes the two recognition sources — VARAPI (licensed providers) and
 * Vashandi (employed health workforce) — into one minimal read-model for
 * experience surfaces (badge chip, profile benefits card).
 *
 * <p>Short-TTL in-process cache (default 30s) keeps the chip cheap on hot
 * surfaces without hiding a licence suspension for long. Both sources FAIL
 * OPEN to "not recognised": recognition is a perk, never a gate, so an
 * unavailable registry must never break a journey (and must never grant an
 * advantage either).</p>
 *
 * <p>Doctrine: recognition drives administrative/financial advantages and
 * display-only badges — NEVER clinical-triage priority or queue ordering.</p>
 */
@Service
public class RecognitionCompositionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionCompositionService.class);

    /** Minimal composed recognition read-model. */
    public record RecognitionView(
            boolean recognised,
            String recognitionClass,   // LICENSED_PROVIDER | HEALTH_WORKFORCE | BOTH | null
            String profession,
            String cadre,
            String licenceStatus) {

        public static RecognitionView notRecognised() {
            return new RecognitionView(false, null, null, null, null);
        }
    }

    private record CacheEntry(RecognitionView view, Instant expiresAt) {}

    private final VarapiServiceClient varapiClient;
    private final VashandiServiceClient vashandiClient;
    private final Duration ttl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RecognitionCompositionService(
            VarapiServiceClient varapiClient,
            VashandiServiceClient vashandiClient,
            @Value("${impilo.recognition.cache-ttl-seconds:30}") long ttlSeconds) {
        this.varapiClient = varapiClient;
        this.vashandiClient = vashandiClient;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    public RecognitionView resolve(String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return RecognitionView.notRecognised();
        }
        String key = healthId.trim();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.view();
        }
        RecognitionView view = compose(key);
        // Opportunistic janitor so the map cannot grow unbounded on scan abuse.
        if (cache.size() > 10_000) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(view, Instant.now().plus(ttl)));
        return view;
    }

    private RecognitionView compose(String healthId) {
        JsonNode provider = safeFetch(() -> varapiClient.getRecognitionByHealthId(healthId), "varapi");
        JsonNode workforce = safeFetch(() -> vashandiClient.getRecognitionByHealthId(healthId), "vashandi");

        boolean providerRecognised = provider != null && provider.path("recognised").asBoolean(false);
        boolean workforceRecognised = workforce != null && workforce.path("recognised").asBoolean(false);

        if (!providerRecognised && !workforceRecognised) {
            return RecognitionView.notRecognised();
        }
        String recognitionClass = providerRecognised && workforceRecognised
                ? "BOTH"
                : providerRecognised ? "LICENSED_PROVIDER" : "HEALTH_WORKFORCE";
        String profession = firstNonBlank(
                providerRecognised ? text(provider, "profession") : null,
                workforceRecognised ? text(workforce, "profession") : null);
        String cadre = firstNonBlank(
                providerRecognised ? text(provider, "cadre") : null,
                workforceRecognised ? text(workforce, "cadre") : null);
        String licenceStatus = providerRecognised ? text(provider, "licenceStatus") : null;
        return new RecognitionView(true, recognitionClass, profession, cadre, licenceStatus);
    }

    private JsonNode safeFetch(java.util.function.Supplier<JsonNode> call, String source) {
        try {
            return call.get();
        } catch (Exception e) {
            // Fail open to NOT recognised — a down registry never blocks the journey.
            log.debug("Recognition source {} unavailable: {}", source, e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
