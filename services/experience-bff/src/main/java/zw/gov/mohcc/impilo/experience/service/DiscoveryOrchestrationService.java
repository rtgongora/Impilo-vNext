package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified public discovery — one anonymous search across the REAL per-domain
 * public lanes (care/facilities, marketplace goods, coverage plans, wellness
 * screening) plus the virtual-services list. Pure composition: it fans out to
 * the existing gateway services/clients and normalises their results into one
 * honest, category-labelled list. The internal service boundaries (Tuso, Msika,
 * Coverage, Simba, Ndila, Varapi) stay hidden from the public caller.
 *
 * Honesty invariants:
 *  - Only fields a source actually provides are surfaced. No fabricated stock,
 *    "open now", ratings, wait-times or online status is synthesised here.
 *  - A lane that fails is skipped with an honest note; discovery still returns.
 *  - Fan-out is SEQUENTIAL on purpose: the downstream clients read request-scoped
 *    trust headers via RequestContextHolder, which a raw async thread would lose.
 */
@Service
public class DiscoveryOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryOrchestrationService.class);

    /** Per-source cap when the caller asks for "all" categories, to keep the blend readable. */
    private static final int ALL_PER_SOURCE = 4;

    private final FindCareOrchestrationService findCare;
    private final MsikaServiceClient msika;
    private final CoverageServiceClient coverage;
    private final SimbaServiceClient simba;

    public DiscoveryOrchestrationService(FindCareOrchestrationService findCare,
                                         MsikaServiceClient msika,
                                         CoverageServiceClient coverage,
                                         SimbaServiceClient simba) {
        this.findCare = findCare;
        this.msika = msika;
        this.coverage = coverage;
        this.simba = simba;
    }

    /** A single normalised discovery result. Nullable fields are simply absent for that source. */
    public record DiscoveryResult(
            String category,     // care | pharmacy | diagnostics | virtual | marketplace | coverage | wellness
            String type,         // human label, e.g. "Facility", "Pharmacy", "Health product", "Coverage plan"
            String id,
            String title,
            String subtitle,
            List<String> meta,   // honest chips (service-match, price display, age range, ...)
            Double distanceMeters,
            Double etaMinutes,
            Double latitude,
            Double longitude,
            String availability, // honest availability label, or null when the source has none
            String href,         // where the primary action goes (public route or sign-in boundary)
            String actionLabel) {}

    public record DiscoveryResponse(
            String query,
            String category,
            List<DiscoveryResult> results,
            Map<String, Integer> categoryCounts,
            List<String> notes) {}

    public DiscoveryResponse search(String q, String category, String lat, String lng,
                                    int page, int size, String clientIp) {
        String cat = (category == null || category.isBlank()) ? "all" : category.trim().toLowerCase();
        boolean all = cat.equals("all");
        List<DiscoveryResult> results = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (all || cat.equals("care") || cat.equals("pharmacy") || cat.equals("diagnostics") || cat.equals("virtual")) {
            addFindCare(cat, all, q, lat, lng, page, size, clientIp, results, notes);
        }
        if (all || cat.equals("marketplace") || cat.equals("medicines")) {
            addMarketplace(q, all, results, notes);
        }
        if (all || cat.equals("coverage")) {
            addCoverage(all, results, notes);
        }
        if (all || cat.equals("wellness")) {
            addWellness(all, results, notes);
        }

        // Rank the unfiltered blend care-first. Previously the order was simply the order
        // the lanes were called, so an "All" search with no stated intent led with oxygen
        // cylinders and resuscitators — goods are legitimate results, but a person who has
        // not expressed a product intent is looking for care. Goods stay first-class and
        // searchable; they just stop being the front page of a care-discovery surface.
        // Within a rank, closer things come first when a distance is actually known.
        if (all) {
            results.sort(Comparator
                    .comparingInt((DiscoveryResult r) -> categoryRank(r.category()))
                    .thenComparing(r -> r.distanceMeters() == null
                            ? Double.MAX_VALUE : r.distanceMeters()));
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DiscoveryResult r : results) {
            counts.merge(r.category(), 1, Integer::sum);
        }
        return new DiscoveryResponse(q, cat, results, counts, notes);
    }

    /** Care before commerce. Lower sorts earlier; anything unrecognised sorts last. */
    private static int categoryRank(String category) {
        if (category == null) return 99;
        return switch (category) {
            case "care" -> 0;
            case "virtual" -> 1;
            case "pharmacy" -> 2;
            case "diagnostics" -> 3;
            case "wellness" -> 4;
            case "coverage" -> 5;
            case "marketplace" -> 6;
            default -> 99;
        };
    }

    private void addFindCare(String cat, boolean all, String q, String lat, String lng,
                             int page, int size, String clientIp,
                             List<DiscoveryResult> out, List<String> notes) {
        String facilityType = cat.equals("pharmacy") ? "PHARMACY" : null;
        String query = q;
        if (query == null || query.isBlank()) {
            if (cat.equals("pharmacy")) query = "pharmacy";
            else if (cat.equals("diagnostics")) query = "laboratory";
            // Virtual care is a national registry listing, not a proximity search:
            // resolveVirtualCare() asks TUSO for ACTIVE virtual services and ignores the
            // query entirely. Seeding one here only satisfies the input gate below — the
            // virtual branch discards the facility results anyway — so the public lane can
            // list real virtual services without first demanding a need or a location.
            else if (cat.equals("virtual")) query = "virtual";
        }
        // The find-care lane needs a need or a location — it does not enumerate the whole
        // registry. Without either, prompt honestly instead of reporting a false outage.
        boolean hasInput = (query != null && !query.isBlank()) || (lat != null && !lat.isBlank());
        if (!hasInput) {
            notes.add("Search a service or share your location to see facilities and care near you.");
            return;
        }
        int perSource = all ? ALL_PER_SOURCE : Math.max(1, size);
        try {
            FindCareOrchestrationService.CareSearchResponse res =
                    findCare.search(query, null, lat, lng, null, null, facilityType, page, perSource, clientIp);

            if (cat.equals("virtual")) {
                addVirtual(res, out);
                if (res.virtualCare() == null || res.virtualCare().isEmpty()) {
                    notes.add("No virtual services are published for this need yet. Try a service name, or browse care.");
                }
                return;
            }

            String facCat = cat.equals("pharmacy") ? "pharmacy"
                    : cat.equals("diagnostics") ? "diagnostics" : "care";
            if (res.results() != null) {
                for (FindCareOrchestrationService.CareResult f : res.results()) {
                    List<String> meta = new ArrayList<>();
                    meta.add(f.serviceMatch() && f.matchedService() != null
                            ? "Offers " + humanize(f.matchedService()) : "In directory");
                    if (f.operationalStatusUnverified()) {
                        meta.add("Hours not verified");
                    }
                    out.add(new DiscoveryResult(
                            facCat, facilityTypeLabel(f.facilityType()), String.valueOf(f.facilityId()),
                            f.name(), joinMeta(humanize(f.facilityType()), humanize(f.level()), f.district(), f.province()),
                            meta, f.distanceMeters(), f.etaMinutes(), f.latitude(), f.longitude(),
                            null, "/welcome/find-care/" + f.facilityId(), "View services"));
                }
            }
            // In an "all" blend, also surface any real virtual-service options alongside facilities.
            if (all) {
                addVirtual(res, out);
            }
        } catch (Exception e) {
            log.warn("Discovery find-care lane failed: {}", e.getMessage());
            notes.add("Care and facility results are temporarily unavailable.");
        }
    }

    private void addVirtual(FindCareOrchestrationService.CareSearchResponse res, List<DiscoveryResult> out) {
        if (res.virtualCare() == null) {
            return;
        }
        for (FindCareOrchestrationService.VirtualCareOption v : res.virtualCare()) {
            out.add(new DiscoveryResult(
                    "virtual", "Virtual service", v.reference(), v.name(),
                    "Virtual clinic" + (v.level() != null ? " · " + humanize(v.level()) : ""),
                    v.serviceLines() == null ? List.of() : v.serviceLines(),
                    null, null, null, null, null,
                    "/auth/login?returnTo=%2Fcitizen%2Fvirtual-care", "Start virtual care"));
        }
    }

    private void addMarketplace(String q, boolean all, List<DiscoveryResult> out, List<String> notes) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (q != null && !q.isBlank()) {
                params.add("q", q);
            }
            params.add("page", "0");
            params.add("size", String.valueOf(all ? ALL_PER_SOURCE : 20));
            // The marketplace lane returns the unwrapped `data` node: an object
            // shaped {results:[...],total,page,size}. Older/other shapes may be a
            // bare array, so accept both rather than silently rendering nothing.
            JsonNode listings = arrayOf(msika.publicListings(params), "results");
            if (listings != null && listings.isArray()) {
                int added = 0;
                for (JsonNode l : listings) {
                    if (all && added >= ALL_PER_SOURCE) {
                        break;
                    }
                    String title = text(l, "title");
                    if (title == null) {
                        continue;
                    }
                    List<String> meta = new ArrayList<>();
                    addIfPresent(meta, text(l, "priceDisplay"));
                    addIfPresent(meta, humanize(text(l, "riskClassification")));
                    addIfPresent(meta, humanize(text(l, "fulfilmentMode")));
                    out.add(new DiscoveryResult(
                            "marketplace", "Health product", text(l, "id"), title,
                            text(l, "summary"), meta, null, null, null, null, null,
                            "/welcome/marketplace", "View listing"));
                    added++;
                }
            }
        } catch (Exception e) {
            log.warn("Discovery marketplace lane failed: {}", e.getMessage());
            notes.add("Marketplace results are temporarily unavailable.");
        }
    }

    private void addCoverage(boolean all, List<DiscoveryResult> out, List<String> notes) {
        try {
            JsonNode plans = arrayOf(coverage.publicCoveragePlans(), "results");
            if (plans != null && plans.isArray()) {
                int added = 0;
                for (JsonNode p : plans) {
                    if (all && added >= ALL_PER_SOURCE) {
                        break;
                    }
                    String name = text(p, "planName");
                    if (name == null) {
                        continue;
                    }
                    List<String> meta = new ArrayList<>();
                    addIfPresent(meta, humanize(text(p, "planType")));
                    addIfPresent(meta, humanize(text(p, "status")));
                    out.add(new DiscoveryResult(
                            "coverage", "Coverage plan", text(p, "planCode"), name,
                            joinMeta(humanize(text(p, "planType")), null, null, null),
                            meta, null, null, null, null, null,
                            "/welcome/coverage", "Compare cover"));
                    added++;
                }
            }
        } catch (Exception e) {
            log.warn("Discovery coverage lane failed: {}", e.getMessage());
            notes.add("Coverage results are temporarily unavailable.");
        }
    }

    private void addWellness(boolean all, List<DiscoveryResult> out, List<String> notes) {
        try {
            JsonNode programmes = arrayOf(simba.publicScreeningProgrammes(), "results");
            if (programmes != null && programmes.isArray()) {
                int added = 0;
                for (JsonNode w : programmes) {
                    if (all && added >= ALL_PER_SOURCE) {
                        break;
                    }
                    String title = text(w, "title");
                    if (title == null) {
                        continue;
                    }
                    List<String> meta = new ArrayList<>();
                    String ages = ageRange(w);
                    addIfPresent(meta, ages);
                    Integer freq = intOrNull(w, "frequencyMonths");
                    if (freq != null && freq > 0) {
                        meta.add("Every " + freq + " month" + (freq == 1 ? "" : "s"));
                    }
                    out.add(new DiscoveryResult(
                            "wellness", "Screening programme", text(w, "programmeCode"), title,
                            text(w, "description"), meta, null, null, null, null, null,
                            "/welcome/wellness", "Explore"));
                    added++;
                }
            }
        } catch (Exception e) {
            log.warn("Discovery wellness lane failed: {}", e.getMessage());
            notes.add("Wellness results are temporarily unavailable.");
        }
    }

    // ---- helpers ----

    /**
     * Public lanes differ in envelope: some return a bare JSON array, others the
     * unwrapped {@code data} object holding a collection field. Normalise both to
     * an array node so one lane's shape never silently renders as "no results".
     */
    private static JsonNode arrayOf(JsonNode node, String collectionField) {
        if (node == null || node.isArray()) {
            return node;
        }
        JsonNode inner = node.get(collectionField);
        return inner != null && inner.isArray() ? inner : null;
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) && n.get(field).isNumber() ? n.get(field).asInt() : null;
    }

    private static String ageRange(JsonNode w) {
        Integer min = intOrNull(w, "ageMin");
        Integer max = intOrNull(w, "ageMax");
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return "Ages " + min + "–" + max;
        }
        return min != null ? "Ages " + min + "+" : "Up to age " + max;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }

    private static String joinMeta(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(p);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String facilityTypeLabel(String facilityType) {
        if (facilityType == null) {
            return "Facility";
        }
        String upper = facilityType.toUpperCase();
        if (upper.contains("PHARMAC")) {
            return "Pharmacy";
        }
        if (upper.contains("LAB")) {
            return "Laboratory";
        }
        return "Facility";
    }

    /** Turn an UPPER_SNAKE enum into a readable label, leaving already-humanised text alone. */
    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        // Humanise UPPER_SNAKE tokens and long all-caps words, but leave short acronyms
        // (OTC, ART, HIV, BP) and already-humanised text alone.
        boolean enumLike = trimmed.matches("[A-Z0-9_ ]+") && (trimmed.contains("_") || trimmed.length() > 4);
        if (!enumLike) {
            return trimmed;
        }
        String lower = trimmed.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
