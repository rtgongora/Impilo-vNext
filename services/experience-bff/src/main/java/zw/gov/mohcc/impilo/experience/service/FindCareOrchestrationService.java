package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

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
    public static final int MAX_VIRTUAL_CARE_OPTIONS = 10;

    private static final String IP_RATE_PREFIX = "gateway:findcare:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:findcare:rl:global";

    private final StringRedisTemplate redis;
    private final TusoServiceClient tuso;
    private final NdilaServiceClient ndila;
    private final VarapiServiceClient varapi;
    private final FindCareServiceTaxonomy taxonomy;
    private final KeycloakAdminClient keycloakAdminClient;

    public FindCareOrchestrationService(StringRedisTemplate redis,
                                        TusoServiceClient tuso,
                                        NdilaServiceClient ndila,
                                        VarapiServiceClient varapi,
                                        FindCareServiceTaxonomy taxonomy,
                                        KeycloakAdminClient keycloakAdminClient) {
        this.redis = redis;
        this.tuso = tuso;
        this.ndila = ndila;
        this.varapi = varapi;
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
            Double longitude,
            /**
             * HAR W5 — {@code SERVICE_MATCH} when TUSO holds a live matching capability;
             * {@code REGISTRY_LISTING} when the facility is only known to be registered with HPA and
             * nobody has confirmed what it does. The shell renders the two under different headings,
             * so a register entry is never presented as an offered service.
             */
            String resultLane,
            String regulatoryStatus) {

        public static final String LANE_SERVICE_MATCH = "SERVICE_MATCH";
        public static final String LANE_REGISTRY_LISTING = "REGISTRY_LISTING";
    }

    /**
     * A real, ACTIVE virtual-care option (TUSO virtual-service registry truth). Surfaced only when
     * a live virtual service exists; the shell renders "Start virtual care" against these and never
     * fabricates one. PII-free, disclosure-limited to what a citizen needs to choose.
     */
    public record VirtualCareOption(
            String reference,
            String name,
            String level,
            String province,
            List<String> serviceLines) {}

    /** The orchestrated response: interpretation, ranked results, and honest notes. */
    public record CareSearchResponse(
            String interpretedService,
            List<String> interpretedTokens,
            boolean telemedicineRequested,
            boolean emergencyIntent,
            boolean accessibilityRequested,
            boolean distanceAvailable,
            boolean virtualCareAvailable,
            List<VirtualCareOption> virtualCare,
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
            // Expand the explicit chip token to its related-service family (e.g. MATERNITY ->
            // [MATERNITY, ANTENATAL, OBSTETRICS]) so related capabilities are not siloed — this is
            // what stops "Maternity" and "Antenatal care" from returning disjoint result sets.
            tokens = taxonomy.expand(explicit);
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
        // The whole related-service family is sent (comma-joined) so a search matches any of them;
        // TUSO splits the facet and ORs the tokens.
        String serviceParam = tokens.isEmpty() ? null : String.join(",", tokens);
        JsonNode paged;
        try {
            paged = tuso.publicFacilityServiceSearch(serviceParam, nameQuery, province, district, facilityType,
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

        // (b2) HAR W5 — the second lane. 5,509 facilities are registered with HPA but carry no
        // confirmed capability, so a service-filtered search is structurally blind to them: they
        // exist, they are legally registered, and a citizen searching for "pharmacy" in their
        // district would be told there is nothing there. Rather than invent capabilities, we return
        // them separately and say exactly what we know — registered, services not confirmed. Only
        // when a service was actually interpreted (an unfiltered directory search already includes
        // them), and only within the caller's own province/district.
        if (token != null && (province != null && !province.isBlank()
                || district != null && !district.isBlank())) {
            List<CareResult> registryListings = fetchRegistryListings(province, district, candidates, notes);
            candidates.addAll(registryListings);
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
        // A registry listing sorts below every service match by construction — serviceMatch is false
        // on all of them — so an unconfirmed entry can never outrank a confirmed service.
        candidates.sort(Comparator
                .comparing((CareResult r) -> r.serviceMatch() ? 0 : 1)
                .thenComparing(r -> r.distanceMeters() == null ? Double.MAX_VALUE : r.distanceMeters())
                .thenComparing(r -> r.name() == null ? "" : r.name()));

        // (e) Virtual-care truth: ACTIVE virtual services (TUSO "virtual hospital" registry). This is
        // NOT a per-facility channel — the register does not hold per-facility telemedicine — so it is
        // surfaced as its own option set, never attached to a specific facility card. Best-effort: a
        // failure omits it rather than fabricating availability.
        List<VirtualCareOption> virtualCare = resolveVirtualCare(province, notes);
        boolean virtualCareAvailable = !virtualCare.isEmpty();
        if (telemed && !virtualCareAvailable) {
            notes.add("Per-facility telemedicine is not held in the register, and no live virtual "
                    + "service matched your area, so we could not confirm remote care here.");
        }

        return new CareSearchResponse(token, tokens, telemed, emergency, accessibility,
                distanceAvailable, virtualCareAvailable, virtualCare, notes, candidates, respPage, respSize, total);
    }

    /**
     * Best-effort ACTIVE virtual-care lookup. Asks TUSO for virtual services in the ACTIVE lifecycle
     * only (never CONFIGURED/SUSPENDED), optionally narrows to the caller's province, and shapes a
     * PII-free option list. On any failure it returns empty — the lane omits virtual care rather than
     * inventing it. Capped so an anonymous caller cannot pull an unbounded set.
     */
    private List<VirtualCareOption> resolveVirtualCare(String province, List<String> notes) {
        try {
            JsonNode data = tuso.listVirtualServices("ACTIVE");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            String wantProvince = trimToNull(province);
            List<VirtualCareOption> options = new ArrayList<>();
            for (JsonNode vs : data) {
                if (!"ACTIVE".equalsIgnoreCase(vs.path("lifecycleStatus").asText(""))) {
                    continue; // defence-in-depth: never trust a non-ACTIVE row through the filter
                }
                String vsProvince = vs.hasNonNull("provinceCode") ? vs.get("provinceCode").asText() : null;
                // A national virtual service (no province) is available everywhere; a provincial one
                // only matches when the caller shared that province (or shared none).
                if (wantProvince != null && vsProvince != null && !wantProvince.equalsIgnoreCase(vsProvince)) {
                    continue;
                }
                List<String> lines = new ArrayList<>();
                JsonNode serviceLines = vs.path("serviceLines");
                if (serviceLines.isArray()) {
                    for (JsonNode line : serviceLines) {
                        String name = line.isObject() ? line.path("name").asText(null) : line.asText(null);
                        if (name != null && !name.isBlank()) {
                            lines.add(name);
                        }
                    }
                }
                options.add(new VirtualCareOption(
                        vs.hasNonNull("vsUid") ? vs.get("vsUid").asText() : null,
                        vs.hasNonNull("name") ? vs.get("name").asText() : null,
                        vs.hasNonNull("level") ? vs.get("level").asText() : null,
                        vsProvince,
                        List.copyOf(lines)));
                if (options.size() >= MAX_VIRTUAL_CARE_OPTIONS) {
                    break;
                }
            }
            if (!options.isEmpty()) {
                notes.add("Virtual care is available: you can start a remote consultation with a live "
                        + "virtual service after signing in.");
            }
            return List.copyOf(options);
        } catch (Exception e) {
            log.warn("Virtual-care lookup unavailable, omitting virtual care from find-care result: {}", e.getMessage());
            return List.of();
        }
    }

    /** Public facility profile passthrough (disclosure-limited; capabilities + operating hours). */
    public JsonNode facilityProfile(long id) {
        return tuso.publicFacilityProfile(id);
    }

    /**
     * Verified providers at a facility — a pure composition passthrough of Varapi's public register
     * (Varapi owns the gate and the allow-listed practitioner fields; the BFF adds no truth and no
     * other service's PII). Wraps the array as {@code {"providers":[...]}}; an unknown/empty facility
     * yields {@code {"providers":[]}} (Varapi returns an empty list — no 404, no existence oracle).
     * Never throws on empty. Rate-limit parity with {@link #facilityProfile} (an unmetered read).
     */
    public JsonNode verifiedProvidersAtFacility(long facilityId) {
        JsonNode providers = varapi.publicFacilityPractitioners(facilityId);
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        if (providers != null && providers.isArray()) {
            out.set("providers", providers);
        } else {
            out.set("providers", JsonNodeFactory.instance.arrayNode());
        }
        return out;
    }

    // ── internals ─────────────────────────────────────────────────────────

    /** How many registry listings may accompany a service search — a hint, not a directory dump. */
    static final int MAX_REGISTRY_LISTINGS = 10;

    /**
     * HAR W5 — fetch HPA-registered facilities in the caller's area that carry no confirmed
     * capability, tagged so the shell can present them under their own honest heading.
     *
     * <p>Best-effort: a failure omits the lane rather than failing the search. Facilities already
     * returned as service matches are excluded, so nothing appears twice.</p>
     */
    private List<CareResult> fetchRegistryListings(String province, String district,
                                                   List<CareResult> alreadyReturned, List<String> notes) {
        java.util.Set<Long> seen = alreadyReturned.stream()
                .map(CareResult::facilityId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<CareResult> listings = new ArrayList<>();
        try {
            JsonNode paged = tuso.publicFacilityRegistryListings(
                    province, district, 0, MAX_REGISTRY_LISTINGS + seen.size());
            JsonNode items = paged == null ? null : paged.path("items");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    if (listings.size() >= MAX_REGISTRY_LISTINGS) {
                        break;
                    }
                    Long id = item.hasNonNull("id") ? item.get("id").asLong() : null;
                    if (id != null && seen.contains(id)) {
                        continue;
                    }
                    listings.add(toCareResult(item, false, null, CareResult.LANE_REGISTRY_LISTING));
                }
            }
        } catch (Exception e) {
            // The primary lane already succeeded; a missing second lane is better than a failed search.
            log.warn("Find-care registry-listing lane unavailable: {}", e.getMessage());
            return List.of();
        }
        if (!listings.isEmpty()) {
            notes.add("Some places nearby are registered with the Health Professions Authority, but "
                    + "the services they offer have not been confirmed yet. Call before travelling.");
        }
        return listings;
    }

    private CareResult toCareResult(JsonNode item, boolean serviceMatched, String matchedService) {
        return toCareResult(item, serviceMatched, matchedService,
                serviceMatched ? CareResult.LANE_SERVICE_MATCH : null);
    }

    private CareResult toCareResult(JsonNode item, boolean serviceMatched, String matchedService,
                                    String resultLane) {
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
                lat, lng,
                resultLane,
                item.hasNonNull("regulatoryStatus") ? item.get("regulatoryStatus").asText() : null);
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
                    c.telemedicineAvailable(), c.latitude(), c.longitude(),
                    c.resultLane(), c.regulatoryStatus()));
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
