package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Citizen "find health services" orchestrator — the service-aware access-to-care search behind the
 * public {@code /internal/v1/public/gateway/find-care} lane.
 *
 * <p>This composes registry truth into a person-facing answer without ever inventing it. The flow:
 * <ol>
 *   <li>Map free text → capability token via {@link FindCareServiceTaxonomy} (or take an explicit
 *       {@code service} token as-is).</li>
 *   <li>Ask TUSO's public facility register for facilities that carry an <b>ACTIVE</b> matching
 *       capability (TUSO is the system of record — a facility is never shown as offering a service
 *       it does not hold).</li>
 *   <li>If the caller shared a location, ask Ndila (geography/routing SoR) for road distance + ETA
 *       to each candidate; otherwise skip distance.</li>
 *   <li>Rank service-match first, then travel distance (nearest first). Operational "open now" is
 *       never asserted from stale status — it is surfaced as unverified.</li>
 * </ol>
 *
 * <p>Public-lane discipline mirrors {@link PublicSosIntakeService}: PII-free results, a synthesized
 * service-account bearer, Redis rate limits (fail-open — a read must not be lost because the limiter
 * is down), and bounded inputs. It composes; it is not a source of truth.</p>
 */
@Service
public class FindCareOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(FindCareOrchestrationService.class);

    // ── Abuse thresholds (reads are lighter than the SOS write lane) ──
    public static final int MAX_REQUESTS_PER_IP = 60;
    public static final int IP_WINDOW_SECONDS = 60;
    public static final int MAX_REQUESTS_GLOBAL = 600;
    public static final int GLOBAL_WINDOW_SECONDS = 60;
    public static final int MAX_QUERY_CHARS = 200;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_DISTANCE_CANDIDATES = 50;

    private static final String IP_RATE_PREFIX = "gateway:findcare:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:findcare:rl:global";

    private final StringRedisTemplate redis;
    private final TusoServiceClient tuso;
    private final NdilaServiceClient ndila;
    private final FindCareServiceTaxonomy taxonomy;
    private final KeycloakAdminClient keycloakAdminClient;

    public FindCareOrchestrationService(StringRedisTemplate redis,
                                        TusoServiceClient tuso,
                                        NdilaServiceClient ndila,
                                        FindCareServiceTaxonomy taxonomy,
                                        KeycloakAdminClient keycloakAdminClient) {
        this.redis = redis;
        this.tuso = tuso;
        this.ndila = ndila;
        this.taxonomy = taxonomy;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /** A single PII-safe find-care result (registry-derived; no contacts, no internal ids). */
    public record CareResult(
            Long facilityId,
            String facilityUuid,
            String name,
            String facilityType,
            String level,
            String district,
            String province,
            boolean serviceMatch,
            String matchedService,
            Double distanceMeters,
            Double etaMinutes,
            String operationalStatus,
            boolean operationalStatusUnverified,
            Boolean openNow,
            Boolean telemedicineAvailable,
            Double latitude,
            Double longitude) {}

    /** The orchestrated response: interpretation, ranked results, and honest notes. */
    public record CareSearchResponse(
            String interpretedService,
            List<String> interpretedTokens,
            boolean telemedicineRequested,
            boolean emergencyIntent,
            boolean accessibilityRequested,
            boolean distanceAvailable,
            List<String> notes,
            List<CareResult> results,
            int page,
            int size,
            long total) {}

    /** Client error (oversized / invalid input). Maps to HTTP 400. */
    public static class FindCareValidationException extends RuntimeException {
        public FindCareValidationException(String message) { super(message); }
    }

    /** Too many searches — carries a retry-after hint. Maps to HTTP 429. */
    public static class FindCareRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public FindCareRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    /**
     * Run a service-aware find-care search.
     *
     * @param query        free-text care need (interpreted to a capability token); optional
     * @param service      explicit capability token (bypasses interpretation); optional
     * @param lat          origin latitude (enables distance ranking); optional
     * @param lng          origin longitude; optional
     * @param province     province filter; optional
     * @param district     district filter; optional
     * @param facilityType facility-type filter; optional
     * @param page         zero-based page
     * @param size         page size (capped at {@value #MAX_PAGE_SIZE})
     * @param clientIp     caller IP for rate limiting
     */
    public CareSearchResponse search(String query, String service, String lat, String lng,
                                     String province, String district, String facilityType,
                                     int page, int size, String clientIp) {
        enforceWindow(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP, IP_WINDOW_SECONDS,
                "Too many searches from this connection. Please slow down and try again shortly.");
        enforceWindow(GLOBAL_RATE_KEY, MAX_REQUESTS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "The service is very busy right now. Please try again shortly.");

        String q = cap(trimToNull(query), MAX_QUERY_CHARS);
        String explicit = trimToNull(service);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<String> notes = new ArrayList<>();

        // (a) Resolve the capability token: explicit param wins, else interpret free text.
        String token;
        List<String> tokens;
        boolean telemed = false, emergency = false, accessibility = false;
        String nameQuery = null;
        if (explicit != null) {
            token = explicit;
            tokens = List.of(explicit);
        } else if (q != null) {
            FindCareServiceTaxonomy.Interpretation interp = taxonomy.interpret(q);
            token = interp.primaryToken();
            tokens = interp.serviceTokens();
            telemed = interp.telemedicine();
            emergency = interp.emergency();
            accessibility = interp.accessibility();
            if (!interp.recognized()) {
                // Nothing matched the taxonomy: fall back to a plain name search over the directory.
                nameQuery = q;
                notes.add("We could not match your words to a specific service, so these are facilities "
                        + "matching your text. Try a service like \"maternity\", \"x-ray\" or \"pharmacy\".");
            }
        } else {
            token = null;
            tokens = List.of();
            notes.add("No service or search term was given, so these are facilities in the area.");
        }

        // (b) Registry truth: facilities carrying an ACTIVE matching capability (or plain directory).
        JsonNode paged;
        try {
            paged = tuso.publicFacilityServiceSearch(token, nameQuery, province, district, facilityType,
                    safePage, safeSize);
        } catch (Exception e) {
            log.warn("Find-care registry search failed: {}", e.getMessage());
            throw new RuntimeException("registry_unavailable", e);
        }

        List<CareResult> candidates = new ArrayList<>();
        long total = 0;
        int respPage = safePage, respSize = safeSize;
        if (paged != null) {
            total = paged.path("totalElements").asLong(0);
            respPage = paged.path("page").asInt(safePage);
            respSize = paged.path("size").asInt(safeSize);
            JsonNode items = paged.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    candidates.add(toCareResult(item, token != null, token));
                }
            }
        }

        // (c) Distance + ETA when the caller shared a location. Ndila first; haversine fallback.
        boolean haveOrigin = isNumeric(lat) && isNumeric(lng);
        boolean distanceAvailable = false;
        if (!haveOrigin) {
            notes.add("Share your location to sort these by how near they are to you.");
        } else if (!candidates.isEmpty()) {
            distanceAvailable = applyDistances(new BigDecimal(lat.trim()), new BigDecimal(lng.trim()),
                    candidates, notes);
        }

        // (d) Rank: service-match first, then nearest (nulls last), then name for stability.
        candidates.sort(Comparator
                .comparing((CareResult r) -> r.serviceMatch() ? 0 : 1)
                .thenComparing(r -> r.distanceMeters() == null ? Double.MAX_VALUE : r.distanceMeters())
                .thenComparing(r -> r.name() == null ? "" : r.name()));

        if (telemed) {
            notes.add("Telemedicine availability per facility is not yet held in the register; "
                    + "we could not confirm which of these offer remote consultations.");
        }

        return new CareSearchResponse(token, tokens, telemed, emergency, accessibility,
                distanceAvailable, notes, candidates, respPage, respSize, total);
    }

    /** Public facility profile passthrough (disclosure-limited; capabilities + operating hours). */
    public JsonNode facilityProfile(long id) {
        return tuso.publicFacilityProfile(id);
    }

    // ── internals ─────────────────────────────────────────────────────────

    private CareResult toCareResult(JsonNode item, boolean serviceMatched, String matchedService) {
        Double lat = item.hasNonNull("latitude") ? item.get("latitude").asDouble() : null;
        Double lng = item.hasNonNull("longitude") ? item.get("longitude").asDouble() : null;
        String opStatus = item.hasNonNull("operationalStatus") ? item.get("operationalStatus").asText() : null;
        return new CareResult(
                item.hasNonNull("id") ? item.get("id").asLong() : null,
                item.hasNonNull("facilityUuid") ? item.get("facilityUuid").asText() : null,
                item.hasNonNull("name") ? item.get("name").asText() : null,
                item.hasNonNull("type") ? item.get("type").asText() : null,
                item.hasNonNull("level") ? item.get("level").asText() : null,
                item.hasNonNull("district") ? item.get("district").asText() : null,
                item.hasNonNull("province") ? item.get("province").asText() : null,
                serviceMatched,
                serviceMatched ? matchedService : null,
                null, // distanceMeters — set in applyDistances
                null, // etaMinutes — set in applyDistances
                opStatus,
                true,  // operationalStatusUnverified — register status is not a live signal
                null,  // openNow — not derivable from the search summary (no operating hours here)
                null,  // telemedicineAvailable — not held per-facility in the register yet
                lat, lng);
    }

    /**
     * Fills distanceMeters/etaMinutes on candidates that have coordinates. Tries Ndila's
     * distance-matrix first; on any failure or a denied/empty response, falls back to a haversine
     * straight-line distance (ETA left null — we do not fabricate a travel time). Returns whether any
     * distance was produced.
     */
    private boolean applyDistances(BigDecimal originLat, BigDecimal originLng,
                                   List<CareResult> candidates, List<String> notes) {
        // Index the candidates that carry coordinates (Ndila can only rank those).
        List<Integer> withCoords = new ArrayList<>();
        List<Map<String, Object>> destinations = new ArrayList<>();
        for (int i = 0; i < candidates.size() && withCoords.size() < MAX_DISTANCE_CANDIDATES; i++) {
            CareResult c = candidates.get(i);
            if (c.latitude() != null && c.longitude() != null) {
                withCoords.add(i);
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("latitude", c.latitude());
                d.put("longitude", c.longitude());
                destinations.add(d);
            }
        }
        if (destinations.isEmpty()) {
            notes.add("None of these facilities have mapped coordinates, so distance is unavailable.");
            return false;
        }

        double[] distances = new double[destinations.size()];
        double[] etaSeconds = new double[destinations.size()];
        boolean etaAvailable = false;
        boolean ndilaOk = false;
        try {
            JsonNode resp = ndila.distanceMatrixFromOrigin(originLat, originLng, destinations);
            if (resp != null && resp.path("success").asBoolean(false)) {
                JsonNode dRow = resp.path("distancesMeters").path(0);
                JsonNode tRow = resp.path("durationsSeconds").path(0);
                if (dRow.isArray() && dRow.size() == destinations.size()) {
                    for (int j = 0; j < destinations.size(); j++) {
                        distances[j] = dRow.get(j).asDouble();
                        if (tRow.isArray() && tRow.size() == destinations.size()) {
                            etaSeconds[j] = tRow.get(j).asDouble();
                            etaAvailable = true;
                        }
                    }
                    ndilaOk = true;
                }
            }
        } catch (Exception e) {
            log.warn("Ndila distance-matrix unavailable, falling back to straight-line distance: {}", e.getMessage());
        }

        if (!ndilaOk) {
            // Fallback: straight-line distance only; ETA is not invented.
            for (int j = 0; j < destinations.size(); j++) {
                CareResult c = candidates.get(withCoords.get(j));
                distances[j] = haversineMeters(originLat.doubleValue(), originLng.doubleValue(),
                        c.latitude(), c.longitude());
            }
            etaAvailable = false;
            notes.add("Distances are approximate straight-line estimates (routing service unavailable).");
        }

        for (int j = 0; j < withCoords.size(); j++) {
            int idx = withCoords.get(j);
            CareResult c = candidates.get(idx);
            Double etaMin = (ndilaOk && etaAvailable) ? Math.round(etaSeconds[j] / 6.0) / 10.0 : null;
            candidates.set(idx, new CareResult(
                    c.facilityId(), c.facilityUuid(), c.name(), c.facilityType(), c.level(),
                    c.district(), c.province(), c.serviceMatch(), c.matchedService(),
                    Math.round(distances[j] * 10.0) / 10.0, etaMin,
                    c.operationalStatus(), c.operationalStatusUnverified(), c.openNow(),
                    c.telemedicineAvailable(), c.latitude(), c.longitude()));
        }
        return true;
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @SuppressWarnings("unused")
    private String resolveBearer() {
        try {
            return keycloakAdminClient.serviceAccountBearer();
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceWindow(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new FindCareRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (FindCareRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            // A public read must not be lost because the rate-limiter store is unavailable.
            log.warn("Find-care rate-limit store unavailable, allowing request (fail-open): {}", e.getMessage());
        }
    }

    private static boolean isNumeric(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            new BigDecimal(v.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String cap(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
