package zw.gov.mohcc.impilo.ndila.core.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns 6,327 dead PENDING review rows into a workable queue by proposing a starting point for
 * each (HAR W4).
 *
 * <p><b>Where the proposal comes from.</b> Not an external geocoder and not a province centroid.
 * It is the centroid of the health facilities we have <em>already surveyed</em> in the same
 * district — real coordinates from the Ministry's master facility list. HPA gave us a locality for
 * 5,400 of its 5,423 location rows and a plausible coordinate for exactly one; the districts those
 * localities name are districts we already have mapped facilities in.</p>
 *
 * <p><b>What it deliberately does NOT do: publish.</b> Every proposal lands on
 * {@code ndila_geocode_review_queue}, never on {@code ndila_locations}. A district centroid is not
 * where the clinic is — it is where to start looking. Rendered as a pin it would be
 * indistinguishable from a surveyed one, and a care-seeker would travel on it. So a steward or the
 * claiming facility confirms first, and only that confirmation publishes.</p>
 *
 * <p><b>Confidence is reported honestly.</b> A district whose known facilities are spread over more
 * than {@value #TIGHT_SPREAD_KM} km yields LOW; a tight district yields MEDIUM. Nothing here ever
 * yields HIGH — a centroid cannot be high-confidence about an individual building.</p>
 */
@Service
public class HpaGeocodeProposalService {

    private static final Logger log = LoggerFactory.getLogger(HpaGeocodeProposalService.class);

    /** Districts whose surveyed facilities sit within this radius are "tight" enough for MEDIUM. */
    static final double TIGHT_SPREAD_KM = 15.0;

    /** A district with fewer known facilities than this has too thin a basis to average. */
    static final int MIN_DISTRICT_SAMPLE = 3;

    static final String PROPOSED_SOURCE = "DISTRICT_CENTROID_OF_SURVEYED_FACILITIES";

    private final JdbcTemplate jdbc;
    private final NdilaCoordinateValidator coordinateValidator;

    public HpaGeocodeProposalService(JdbcTemplate jdbc, NdilaCoordinateValidator coordinateValidator) {
        this.jdbc = jdbc;
        this.coordinateValidator = coordinateValidator;
    }

    public record ProposalSummary(int queueRowsConsidered, int proposalsWritten, int noGazetteerMatch,
                                  int rejectedByValidator, boolean dryRun) {}

    /** A district we have surveyed coordinates for, and how tightly those coordinates cluster. */
    record DistrictCentroid(String district, BigDecimal latitude, BigDecimal longitude,
                            int sampleSize, double spreadKm) {
        String confidence() {
            return spreadKm <= TIGHT_SPREAD_KM ? "MEDIUM" : "LOW";
        }
    }

    @Transactional
    public ProposalSummary proposeForPendingReviews(UUID tenantId, boolean dryRun, int limit) {
        Map<String, DistrictCentroid> gazetteer = buildDistrictGazetteer(tenantId);
        if (gazetteer.isEmpty()) {
            log.warn("HPA geocode proposals: no surveyed facility coordinates to build a gazetteer from");
            return new ProposalSummary(0, 0, 0, 0, dryRun);
        }

        List<Map<String, Object>> pending = jdbc.queryForList(
                "SELECT id, district, proposed_locality, province FROM ndila_geocode_review_queue "
                        + "WHERE tenant_id = ? AND status = 'PENDING' AND proposal_status IS NULL "
                        + "  AND proposed_latitude IS NULL "
                        + "ORDER BY created_at LIMIT ?",
                tenantId, Math.max(1, limit));

        int written = 0, unmatched = 0, rejected = 0;
        for (Map<String, Object> row : pending) {
            // HPA's locality is the administrative area; it is the only place hint most rows have.
            String hint = firstNonBlank((String) row.get("district"), (String) row.get("proposed_locality"));
            DistrictCentroid centroid = hint == null ? null : gazetteer.get(normalise(hint));
            if (centroid == null) {
                unmatched++;
                continue;
            }
            NdilaCoordinateValidator.Result validation =
                    coordinateValidator.validate(centroid.latitude(), centroid.longitude(), null, "ZWE");
            if (!validation.valid() || !validation.plausible()) {
                // Should not happen — the inputs are surveyed Zimbabwe facilities — but a centroid
                // is arithmetic on data we did not check today, so it goes through the same gate.
                rejected++;
                continue;
            }
            if (!dryRun) {
                jdbc.update(
                        "UPDATE ndila_geocode_review_queue SET proposed_latitude = ?, proposed_longitude = ?, "
                                + "proposed_source = ?, proposed_confidence = ?, proposed_at = NOW(), "
                                + "proposal_status = 'PROPOSED' WHERE id = ?",
                        centroid.latitude(), centroid.longitude(), PROPOSED_SOURCE,
                        centroid.confidence(), row.get("id"));
            }
            written++;
        }
        log.info("HPA geocode proposals: considered={} written={} noGazetteerMatch={} rejected={} dryRun={}",
                pending.size(), written, unmatched, rejected, dryRun);
        return new ProposalSummary(pending.size(), written, unmatched, rejected, dryRun);
    }

    /**
     * Build the gazetteer from surveyed facility coordinates only — never from a proposal, or the
     * next run would average its own guesses.
     */
    Map<String, DistrictCentroid> buildDistrictGazetteer(UUID tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT district, avg(latitude) AS lat, avg(longitude) AS lng, count(*) AS n, "
                        + "       max(latitude) AS max_lat, min(latitude) AS min_lat, "
                        + "       max(longitude) AS max_lng, min(longitude) AS min_lng "
                        + "  FROM ndila_locations "
                        + " WHERE tenant_id = ? AND location_type = ? AND is_active = TRUE "
                        + "   AND latitude IS NOT NULL AND longitude IS NOT NULL "
                        + "   AND district IS NOT NULL AND source <> 'GEOCODED' "
                        + " GROUP BY district",
                tenantId, NdilaLocationVocabulary.LOCATION_TYPE_HEALTH_FACILITY);

        Map<String, DistrictCentroid> gazetteer = new HashMap<>();
        for (Map<String, Object> row : rows) {
            int sample = ((Number) row.get("n")).intValue();
            if (sample < MIN_DISTRICT_SAMPLE) {
                continue;
            }
            String district = (String) row.get("district");
            BigDecimal lat = scale((Number) row.get("lat"));
            BigDecimal lng = scale((Number) row.get("lng"));
            double spreadKm = boundingBoxDiagonalKm(
                    num(row.get("min_lat")), num(row.get("max_lat")),
                    num(row.get("min_lng")), num(row.get("max_lng")));
            gazetteer.put(normalise(district), new DistrictCentroid(district, lat, lng, sample, spreadKm));
        }
        return gazetteer;
    }

    /** Rough diagonal of the district's bounding box — enough to grade confidence, not navigate by. */
    static double boundingBoxDiagonalKm(double minLat, double maxLat, double minLng, double maxLng) {
        double latKm = (maxLat - minLat) * 110.574;
        double midLatRad = Math.toRadians((maxLat + minLat) / 2.0);
        double lngKm = (maxLng - minLng) * 111.320 * Math.cos(midLatRad);
        return Math.sqrt(latKm * latKm + lngKm * lngKm);
    }

    private static double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static BigDecimal scale(Number value) {
        return value == null ? null : new BigDecimal(value.toString()).setScale(7, RoundingMode.HALF_UP);
    }

    static String normalise(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }
}
