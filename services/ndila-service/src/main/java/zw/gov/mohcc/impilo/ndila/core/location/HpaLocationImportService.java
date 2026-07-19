package zw.gov.mohcc.impilo.ndila.core.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * HPA location/coordinate consumer for Ndila.
 *
 * <p>Imports address/locality assertions for HPA-reconciled facilities as
 * UNVERIFIED {@code ndila_locations} rows (owner {@code TUSO/FACILITY}, keyed to
 * the tuso facility via {@code HPA-<id>}, {@code source=IMPORT}). Legacy operational
 * detail is used ONLY where the feed corroborated it. A facility with no plausible,
 * identity-corroborated coordinate is NOT given a map pin — it is enqueued in
 * {@code ndila_geocode_review_queue} ("Map location awaiting confirmation") and stays
 * text-searchable. Any candidate coordinate is passed through
 * {@link NdilaCoordinateValidator}; implausible/out-of-country coordinates are
 * rejected to the review queue, never persisted as a public location.</p>
 */
@Service
public class HpaLocationImportService {

    private static final Logger log = LoggerFactory.getLogger(HpaLocationImportService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final NdilaCoordinateValidator coordinateValidator;

    public HpaLocationImportService(JdbcTemplate jdbc,
                                    ObjectMapper objectMapper,
                                    NdilaCoordinateValidator coordinateValidator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.coordinateValidator = coordinateValidator;
    }

    public record ImportRequest(String feedPath, UUID tenantId) {}

    public record ImportSummary(int facilitiesSeen, int locationsAsserted, int coordinatesAccepted,
                                int coordinatesRejected, int geocodeReviewQueued, int idempotentSkipped) {}

    @Transactional
    public ImportSummary importFeed(ImportRequest request) {
        UUID tenantId = resolveTenant(request.tenantId());
        Path feed = Path.of(request.feedPath());
        if (!Files.isReadable(feed)) {
            throw new IllegalArgumentException("HPA feed not readable: " + request.feedPath());
        }
        int seen = 0, asserted = 0, coordOk = 0, coordRej = 0, queued = 0, skipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(feed, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode record = objectMapper.readTree(line);
                JsonNode hpa = record.path("current_hpa");
                long institutionId = hpa.path("hpa_institution_id").asLong();
                if (institutionId == 0) {
                    continue;
                }
                seen++;
                String ownerEntityId = "HPA-" + institutionId;
                String name = text(hpa, "institution_name_raw");
                String province = text(hpa, "province");
                JsonNode legacy = record.path("legacy_enrichment");
                boolean corroborated = legacy != null && !legacy.isNull() && !legacy.isMissingNode();
                String locality = corroborated ? text(legacy, "locality") : null;
                String address = corroborated ? text(legacy, "physical_address") : null;

                // Coordinate handling — the corroborated feed carries no coordinate; any candidate
                // is validated and only a plausible one is persisted as a location pin.
                BigDecimal lat = decimal(corroborated ? legacy : hpa, "latitude");
                BigDecimal lng = decimal(corroborated ? legacy : hpa, "longitude");
                boolean coordPersisted = false;
                if (lat != null && lng != null) {
                    NdilaCoordinateValidator.Result r = coordinateValidator.validate(lat, lng, null, "ZWE");
                    if (r.valid() && r.plausible()) {
                        coordPersisted = true;
                        coordOk++;
                    } else {
                        coordRej++;
                        lat = null;
                        lng = null;
                    }
                }

                // Assert an UNVERIFIED location when we have address/locality/province to record.
                if (address != null || locality != null || province != null) {
                    if (insertLocationIfAbsent(tenantId, ownerEntityId, name, address, locality, province,
                            coordPersisted ? lat : null, coordPersisted ? lng : null)) {
                        asserted++;
                    } else {
                        skipped++;
                    }
                }

                // No plausible, corroborated coordinate → no public pin; queue for confirmation.
                if (!coordPersisted) {
                    if (enqueueGeocodeReview(tenantId, ownerEntityId, name, province)) {
                        queued++;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("HPA feed read failed: " + e.getMessage(), e);
        }
        log.info("HPA location import: facilities={} locations={} coordOk={} coordRejected={} geocodeQueued={} skipped={}",
                seen, asserted, coordOk, coordRej, queued, skipped);
        return new ImportSummary(seen, asserted, coordOk, coordRej, queued, skipped);
    }

    private boolean insertLocationIfAbsent(UUID tenantId, String ownerEntityId, String name, String address,
                                           String locality, String province, BigDecimal lat, BigDecimal lng) {
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM ndila_locations WHERE tenant_id=? AND owner_service='TUSO' "
                        + "AND owner_entity_id=? AND source='IMPORT'",
                Integer.class, tenantId, ownerEntityId);
        if (existing != null && existing > 0) {
            return false;
        }
        jdbc.update("INSERT INTO ndila_locations (tenant_id, owner_service, owner_entity_type, owner_entity_id, "
                        + "name, description, location_type, latitude, longitude, locality, province, source, "
                        + "verification_status, sensitivity_level) "
                        + "VALUES (?, 'TUSO', 'FACILITY', ?, ?, ?, 'HEALTH_FACILITY', ?, ?, ?, ?, 'IMPORT', 'UNVERIFIED', 'PUBLIC')",
                tenantId, ownerEntityId, name != null ? name : ownerEntityId, address, lat, lng, locality, province);
        return true;
    }

    private boolean enqueueGeocodeReview(UUID tenantId, String ownerEntityId, String name, String province) {
        int rows = jdbc.update(
                "INSERT INTO ndila_geocode_review_queue (tenant_id, owner_service, owner_entity_type, "
                        + "owner_entity_id, facility_name, province, reason, status) "
                        + "VALUES (?, 'TUSO', 'FACILITY', ?, ?, ?, 'MISSING_COORDINATES', 'PENDING') "
                        + "ON CONFLICT (tenant_id, owner_service, owner_entity_id) DO NOTHING",
                tenantId, ownerEntityId, name != null ? name : ownerEntityId, province);
        return rows > 0;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.decimalValue();
    }

    private UUID resolveTenant(UUID requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return TrustContextHolder.require().tenantId();
        } catch (Exception e) {
            throw new IllegalStateException("No tenant context for HPA location import");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
