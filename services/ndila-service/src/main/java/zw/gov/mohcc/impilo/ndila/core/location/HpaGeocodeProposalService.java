package zw.gov.mohcc.impilo.ndila.core.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Proposes a location for HPA-listed facilities from a curated place gazetteer (HAR W7).
 *
 * <p><b>This replaces a version that did not work.</b> The first implementation keyed each pending
 * review row on its {@code district}, then on {@code proposed_locality}. Measured against the live
 * estate afterwards: of 6,327 queue rows, <b>zero</b> carry either — both columns are written only
 * by the newer importer path, so they are populated for imports that have not happened yet. The
 * service would have proposed nothing and reported success, which is the failure mode this whole
 * programme exists to remove. The key that actually exists in the data is
 * {@code (locality, province)} on {@code ndila_locations}, and across the 2,294 HPA rows that carry
 * a locality there are only <b>212 distinct pairs</b>.</p>
 *
 * <p><b>Two precisions, never blended.</b> {@code ADDRESS} describes one building.
 * {@code SUBURB_CENTROID} is one point shared by every facility in a suburb — 188 facilities sit
 * behind CITY CENTRE/Harare alone. Both are proposals; only their precision differs, and the
 * difference has to survive all the way to the screen. A shared centroid rendered as "1.2 km" is a
 * precise-looking lie.</p>
 *
 * <p><b>Still publishes nothing.</b> Proposals land on {@code ndila_geocode_review_queue}. A
 * gazetteer entry is only usable once a human has marked it {@code REVIEWED}, and a queue row still
 * needs a steward before any pin reaches {@code ndila_locations}.</p>
 */
@Service
public class HpaGeocodeProposalService {

    private static final Logger log = LoggerFactory.getLogger(HpaGeocodeProposalService.class);

    static final String PRECISION_ADDRESS = "ADDRESS";
    static final String PRECISION_SUBURB = "SUBURB_CENTROID";

    /** Only a human-reviewed gazetteer entry may be proposed from. */
    static final String GAZETTEER_REVIEWED = "REVIEWED";

    private final JdbcTemplate jdbc;
    private final NdilaCoordinateValidator coordinateValidator;

    public HpaGeocodeProposalService(JdbcTemplate jdbc, NdilaCoordinateValidator coordinateValidator) {
        this.jdbc = jdbc;
        this.coordinateValidator = coordinateValidator;
    }

    public record ProposalSummary(int queueRowsConsidered, int proposalsWritten, int noGazetteerMatch,
                                  int rejectedByValidator, boolean dryRun) {}

    public record GazetteerBuildSummary(int distinctPlaces, int created, int updated, boolean dryRun) {}

    /**
     * Derive the distinct (locality, province) pairs the HPA estate actually uses, and record each
     * as a gazetteer row awaiting coordinates.
     *
     * <p>Coordinates are deliberately left NULL. There is no geocoding service in this estate, and
     * inventing one from a province centroid would put a pin 100 km from the clinic. What this
     * produces is an honest, countable worklist — 212 rows, ordered by how many facilities depend on
     * each — rather than a silent gap.</p>
     */
    @Transactional
    public GazetteerBuildSummary buildGazetteerFromLocations(UUID tenantId, boolean dryRun) {
        List<Map<String, Object>> places = jdbc.queryForList(
                """
                SELECT trim(locality) AS locality, trim(province) AS province, count(*) AS facility_count
                  FROM ndila_locations
                 WHERE tenant_id = ?
                   AND owner_entity_id LIKE 'HPA-%'
                   AND nullif(trim(locality), '') IS NOT NULL
                   AND nullif(trim(province), '') IS NOT NULL
                 GROUP BY trim(locality), trim(province)
                 ORDER BY count(*) DESC
                """,
                tenantId);

        int created = 0, updated = 0;
        for (Map<String, Object> place : places) {
            String locality = (String) place.get("locality");
            String province = (String) place.get("province");
            int count = ((Number) place.get("facility_count")).intValue();
            if (dryRun) {
                continue;
            }
            // Idempotent: re-running refreshes the facility count without disturbing a coordinate a
            // reviewer has already placed, or resetting their REVIEWED verdict.
            int rows = jdbc.update(
                    """
                    INSERT INTO ndila_place_gazetteer
                        (tenant_id, locality, province, locality_key, province_key, facility_count)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, locality_key, province_key)
                    DO UPDATE SET facility_count = EXCLUDED.facility_count, updated_at = NOW()
                    """,
                    tenantId, locality, province, key(locality), key(province), count);
            if (rows == 1) {
                created++;
            } else {
                updated++;
            }
        }
        log.info("HPA gazetteer build: distinctPlaces={} created={} updated={} dryRun={}",
                places.size(), created, updated, dryRun);
        return new GazetteerBuildSummary(places.size(), created, updated, dryRun);
    }

    /**
     * Propose a coordinate for each pending review row whose place has a reviewed gazetteer entry.
     *
     * <p>The queue carries no locality of its own, so each row is joined back to its
     * {@code ndila_locations} record by {@code owner_entity_id} — the join the previous version was
     * missing.</p>
     */
    @Transactional
    public ProposalSummary proposeForPendingReviews(UUID tenantId, boolean dryRun, int limit) {
        List<Map<String, Object>> pending = jdbc.queryForList(
                """
                SELECT q.id,
                       g.latitude, g.longitude, g.precision_tier, g.coordinate_source,
                       l.locality, l.province
                  FROM ndila_geocode_review_queue q
                  JOIN ndila_locations l
                    ON l.tenant_id = q.tenant_id AND l.owner_entity_id = q.owner_entity_id
                  LEFT JOIN ndila_place_gazetteer g
                    ON g.tenant_id = q.tenant_id
                   AND g.locality_key = upper(trim(l.locality))
                   AND g.province_key = upper(trim(l.province))
                   AND g.status = ?
                 WHERE q.tenant_id = ?
                   AND q.status = 'PENDING'
                   AND q.proposal_status IS NULL
                   AND q.proposed_latitude IS NULL
                 ORDER BY q.created_at
                 LIMIT ?
                """,
                GAZETTEER_REVIEWED, tenantId, Math.max(1, limit));

        int written = 0, unmatched = 0, rejected = 0;
        for (Map<String, Object> row : pending) {
            BigDecimal lat = (BigDecimal) row.get("latitude");
            BigDecimal lng = (BigDecimal) row.get("longitude");
            if (lat == null || lng == null) {
                // No reviewed gazetteer entry for this place yet. Not an error — it is the worklist.
                unmatched++;
                continue;
            }
            NdilaCoordinateValidator.Result validation =
                    coordinateValidator.validate(lat, lng, null, "ZWE");
            if (!validation.valid() || !validation.plausible()) {
                // A reviewed entry should already be plausible, but a coordinate never reaches the
                // queue without passing the same gate every other coordinate passes.
                rejected++;
                continue;
            }
            if (!dryRun) {
                jdbc.update(
                        """
                        UPDATE ndila_geocode_review_queue
                           SET proposed_latitude = ?, proposed_longitude = ?,
                               proposed_source = ?, proposed_precision = ?,
                               proposed_confidence = ?, proposed_at = NOW(),
                               proposal_status = 'PROPOSED'
                         WHERE id = ?
                        """,
                        lat, lng,
                        (String) row.get("coordinate_source"),
                        (String) row.get("precision_tier"),
                        confidenceFor((String) row.get("precision_tier")),
                        row.get("id"));
            }
            written++;
        }
        log.info("HPA geocode proposals: considered={} written={} noReviewedPlace={} rejected={} dryRun={}",
                pending.size(), written, unmatched, rejected, dryRun);
        return new ProposalSummary(pending.size(), written, unmatched, rejected, dryRun);
    }

    /** One geocoded street address, as supplied by whatever placed it. */
    public record GeocodedAddress(String ownerEntityId, BigDecimal latitude, BigDecimal longitude,
                                  String geocoder) {}

    public record AddressProposalSummary(int submitted, int accepted, int rejectedByValidator,
                                         int noPendingReview, boolean dryRun) {}

    /**
     * Record address-precision proposals for facilities whose street address has been geocoded
     * elsewhere.
     *
     * <p>There is no geocoding service inside this estate, and inventing one would mean either a
     * network dependency this deployment does not have or a fabricated coordinate. So the geocoding
     * step happens outside and its <em>results</em> arrive here — which makes the external
     * dependency a defined input (owner id → coordinate → geocoder name) rather than an open
     * question, and keeps every coordinate that reaches the platform on the same validated path.</p>
     *
     * <p>Each point still passes {@link NdilaCoordinateValidator} — an outside geocoder is exactly
     * the kind of source that returns a plausible-looking point in the wrong country — and still
     * lands as a <em>proposal</em> on the review queue. Address precision means "we know which
     * building"; it does not mean "nobody needs to check".</p>
     */
    @Transactional
    public AddressProposalSummary proposeFromGeocodedAddresses(UUID tenantId,
                                                               List<GeocodedAddress> geocoded,
                                                               boolean dryRun) {
        if (geocoded == null || geocoded.isEmpty()) {
            return new AddressProposalSummary(0, 0, 0, 0, dryRun);
        }
        int accepted = 0, rejected = 0, noPending = 0;
        for (GeocodedAddress point : geocoded) {
            if (point == null || point.ownerEntityId() == null
                    || point.latitude() == null || point.longitude() == null) {
                rejected++;
                continue;
            }
            NdilaCoordinateValidator.Result validation =
                    coordinateValidator.validate(point.latitude(), point.longitude(), null, "ZWE");
            if (!validation.valid() || !validation.plausible()) {
                rejected++;
                continue;
            }
            if (dryRun) {
                accepted++;
                continue;
            }
            int updated = jdbc.update(
                    """
                    UPDATE ndila_geocode_review_queue
                       SET proposed_latitude = ?, proposed_longitude = ?,
                           proposed_source = ?, proposed_precision = ?,
                           proposed_confidence = ?, proposed_at = NOW(),
                           proposal_status = 'PROPOSED'
                     WHERE tenant_id = ? AND owner_entity_id = ?
                       AND status = 'PENDING' AND proposal_status IS NULL
                    """,
                    point.latitude(), point.longitude(),
                    point.geocoder() == null ? "EXTERNAL_GEOCODER" : point.geocoder(),
                    PRECISION_ADDRESS, confidenceFor(PRECISION_ADDRESS),
                    tenantId, point.ownerEntityId());
            if (updated == 0) {
                // Already proposed, already resolved, or not queued — never force it.
                noPending++;
            } else {
                accepted += updated;
            }
        }
        log.info("HPA address proposals: submitted={} accepted={} rejected={} noPendingReview={} dryRun={}",
                geocoded.size(), accepted, rejected, noPending, dryRun);
        return new AddressProposalSummary(geocoded.size(), accepted, rejected, noPending, dryRun);
    }

    /**
     * Facilities whose recovered street address is ready to be geocoded — the input a geocoding run
     * consumes. Only rows still awaiting a coordinate are offered, so a run cannot re-place a
     * facility a steward has already settled.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> addressesAwaitingGeocode(UUID tenantId, int limit) {
        return jdbc.queryForList(
                """
                SELECT l.owner_entity_id, l.name, l.address_line1, l.locality, l.province
                  FROM ndila_geocode_review_queue q
                  JOIN ndila_locations l
                    ON l.tenant_id = q.tenant_id AND l.owner_entity_id = q.owner_entity_id
                 WHERE q.tenant_id = ?
                   AND q.status = 'PENDING'
                   AND q.proposal_status IS NULL
                   AND nullif(trim(coalesce(l.address_line1, '')), '') IS NOT NULL
                 ORDER BY l.province, l.name
                 LIMIT ?
                """,
                tenantId, Math.min(Math.max(limit, 1), 5000));
    }

    /**
     * A street address describes a building; a suburb centroid describes a neighbourhood. Neither is
     * ever HIGH — that word belongs to a surveyed coordinate, and nothing here surveys anything.
     */
    static String confidenceFor(String precisionTier) {
        return PRECISION_ADDRESS.equals(precisionTier) ? "MEDIUM" : "LOW";
    }

    static String key(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
