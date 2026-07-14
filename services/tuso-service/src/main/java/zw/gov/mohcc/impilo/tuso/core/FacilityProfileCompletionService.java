package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos.CompleteFacilityProfileRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos.GeocodeProvenance;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Governed completion of a facility profile from the facility-mode completeness prompt.
 *
 * <p>Authorization is FAIL-CLOSED and evaluated inside TUSO (never trusted from the
 * experience layer): the acting user must hold an ACTIVE {@code FacilityAdminAppointment}
 * at this facility, or be an active practitioner-in-charge (open-ended assignment,
 * {@code end_date IS NULL}, not rejected/revoked). Everyone else gets 403.</p>
 *
 * <p>Every accepted field change records per-field {@code facility_history} with
 * metadata {@code source=COMPLETENESS_PROMPT}; geocode rows additionally carry the
 * submitted provenance, and manual / low-confidence / low-accuracy geocodes are stamped
 * {@code geocodeVerification=UNVERIFIED}. The facility version bumps once and a single
 * {@code tuso.facility.updated} outbox event is published. {@code regulatory_status}
 * is untouchable from this flow by construction (not in the request DTO).</p>
 */
@Service
public class FacilityProfileCompletionService {

    private static final Logger log = LoggerFactory.getLogger(FacilityProfileCompletionService.class);

    public static final String HISTORY_SOURCE = "COMPLETENESS_PROMPT";
    public static final String GEOCODE_UNVERIFIED = "UNVERIFIED";

    /** Provider confidence below this stamps the geocode UNVERIFIED. */
    static final double MIN_VERIFIED_CONFIDENCE = 0.7d;
    /** Device accuracy radius above this (meters) stamps the geocode UNVERIFIED. */
    static final double MAX_VERIFIED_ACCURACY_METERS = 100d;

    private final FacilityRepository facilityRepository;
    private final FacilityGeoRepository geoRepository;
    private final FacilityHistoryRepository historyRepository;
    private final EventOutboxRepository outboxRepository;
    private final FacilityAdminAppointmentRepository adminAppointmentRepository;
    private final PractitionerInChargeAssignmentRepository picAssignmentRepository;
    private final ObjectMapper objectMapper;

    public FacilityProfileCompletionService(FacilityRepository facilityRepository,
                                            FacilityGeoRepository geoRepository,
                                            FacilityHistoryRepository historyRepository,
                                            EventOutboxRepository outboxRepository,
                                            FacilityAdminAppointmentRepository adminAppointmentRepository,
                                            PractitionerInChargeAssignmentRepository picAssignmentRepository,
                                            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.geoRepository = geoRepository;
        this.historyRepository = historyRepository;
        this.outboxRepository = outboxRepository;
        this.adminAppointmentRepository = adminAppointmentRepository;
        this.picAssignmentRepository = picAssignmentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Apply a governed profile-completion payload and return the recomputed completeness.
     */
    @Transactional
    public FacilityCompletenessService.FacilityCompleteness completeProfile(
            Long facilityId, CompleteFacilityProfileRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Facility not found: " + facilityId));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }

        requireActiveManagerOrPic(facility, actorId);
        validateGeocode(request);

        String geocodeVerification = geocodeVerification(request.geocodeProvenance());
        boolean[] changed = {false};

        // ── facility completeness-governed fields ────────────────────────────
        applyText(facilityId, "facility_type", facility::getFacilityType,
                request.facilityType(), facility::setFacilityType, actorId, null, null, changed);
        applyText(facilityId, "ownership", facility::getOwnership,
                request.ownership(), facility::setOwnership, actorId, null, null, changed);
        applyText(facilityId, "operational_status", facility::getOperationalStatus,
                request.operationalStatus(), facility::setOperationalStatus, actorId, null, null, changed);

        if (request.latitude() != null && !valueEquals(request.latitude(), facility.getLatitude())) {
            recordHistory(facilityId, "latitude", toStringOrNull(facility.getLatitude()),
                    request.latitude().toPlainString(), actorId,
                    request.geocodeProvenance(), geocodeVerification);
            facility.setLatitude(request.latitude());
            changed[0] = true;
        }
        if (request.longitude() != null && !valueEquals(request.longitude(), facility.getLongitude())) {
            recordHistory(facilityId, "longitude", toStringOrNull(facility.getLongitude()),
                    request.longitude().toPlainString(), actorId,
                    request.geocodeProvenance(), geocodeVerification);
            facility.setLongitude(request.longitude());
            changed[0] = true;
        }

        // ── geo profile (upsert, per-field history) ───────────────────────────
        FacilityGeoEntity geo = geoRepository.findByFacilityId(facilityId).orElse(null);
        boolean geoIsNew = geo == null;
        if (geoIsNew && hasAnyGeoField(request)) {
            geo = new FacilityGeoEntity();
            geo.setFacility(facility);
        }
        if (geo != null) {
            applyText(facilityId, "address_line1", geo::getAddressLine1,
                    request.addressLine1(), geo::setAddressLine1, actorId, null, null, changed);
            applyText(facilityId, "address_line2", geo::getAddressLine2,
                    request.addressLine2(), geo::setAddressLine2, actorId, null, null, changed);
            applyText(facilityId, "city", geo::getCity,
                    request.city(), geo::setCity, actorId, null, null, changed);
            applyText(facilityId, "geo_province", geo::getProvince,
                    request.province(), geo::setProvince, actorId, null, null, changed);
            applyText(facilityId, "geo_district", geo::getDistrict,
                    request.district(), geo::setDistrict, actorId, null, null, changed);
            applyText(facilityId, "ward", geo::getWard,
                    request.ward(), geo::setWard, actorId, null, null, changed);
            applyText(facilityId, "postal_code", geo::getPostalCode,
                    request.postalCode(), geo::setPostalCode, actorId, null, null, changed);
            if (!geoIsNew || changed[0]) {
                geo = geoRepository.save(geo);
            }
        }

        if (!changed[0]) {
            log.info("Facility {} complete-profile: no effective changes submitted by {}", facilityId, actorId);
            return FacilityCompletenessService.evaluate(facility, geo);
        }

        facility.setVersion(facility.getVersion() + 1);
        facility.setUpdatedBy(actorId);
        facility = facilityRepository.save(facility);

        publishUpdated(facility);
        log.info("Facility {} profile completed by {} (version {})",
                facilityId, actorId, facility.getVersion());

        return FacilityCompletenessService.evaluate(facility, geo);
    }

    // ── authz (fail-closed) ──────────────────────────────────────────────────

    private void requireActiveManagerOrPic(FacilityEntity facility, String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No acting user identity; facility profile completion is a governed write");
        }
        UUID facilityUuid = facility.getFacilityUuid();
        boolean activeAdmin = facilityUuid != null
                && adminAppointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                        facilityUuid, actorId, FacilityAdminAppointmentEntity.STATE_ACTIVE);
        if (activeAdmin) {
            return;
        }
        boolean activePic = picAssignmentRepository
                .findByFacilityIdAndEndDateIsNull(facility.getId()).stream()
                .filter(a -> !FacilityAdminAppointmentEntity.STATE_REJECTED.equalsIgnoreCase(a.getApprovalState())
                        && !FacilityAdminAppointmentEntity.STATE_REVOKED.equalsIgnoreCase(a.getApprovalState()))
                .anyMatch(a -> actorId.equals(a.getProviderPublicId())
                        || (a.getImpiloHealthId() != null && actorId.equals(a.getImpiloHealthId().toString())));
        if (!activePic) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Actor holds no ACTIVE facility-administrator appointment and is not the active "
                            + "practitioner-in-charge at this facility");
        }
    }

    // ── validation ───────────────────────────────────────────────────────────

    private static void validateGeocode(CompleteFacilityProfileRequest request) {
        boolean hasLat = request.latitude() != null;
        boolean hasLng = request.longitude() != null;
        if (hasLat != hasLng) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude and longitude must be submitted together");
        }
        if (!hasLat) {
            return;
        }
        if (request.latitude().abs().compareTo(new BigDecimal("90")) > 0
                || request.longitude().abs().compareTo(new BigDecimal("180")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude must be in [-90,90] and longitude in [-180,180]");
        }
        GeocodeProvenance provenance = request.geocodeProvenance();
        if (provenance == null || provenance.source() == null
                || !FacilityCompletenessDtos.GEOCODE_SOURCES.contains(provenance.source())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "geocodeProvenance.source (DEVICE_GPS | ADDRESS_SEARCH | MANUAL) is required "
                            + "when submitting coordinates");
        }
        if (provenance.confidence() != null
                && (provenance.confidence() < 0d || provenance.confidence() > 1d)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "geocodeProvenance.confidence must be in [0,1]");
        }
        if (provenance.accuracyMeters() != null && provenance.accuracyMeters() < 0d) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "geocodeProvenance.accuracyMeters must be >= 0");
        }
    }

    /** Manual or low-confidence/low-accuracy geocodes are stamped UNVERIFIED in history. */
    static String geocodeVerification(GeocodeProvenance provenance) {
        if (provenance == null) {
            return null;
        }
        boolean unverified = "MANUAL".equals(provenance.source())
                || (provenance.confidence() != null && provenance.confidence() < MIN_VERIFIED_CONFIDENCE)
                || (provenance.accuracyMeters() != null && provenance.accuracyMeters() > MAX_VERIFIED_ACCURACY_METERS);
        return unverified ? GEOCODE_UNVERIFIED : null;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void applyText(Long facilityId, String fieldName, Supplier<String> getter,
                           String newValue, Consumer<String> setter, String actorId,
                           GeocodeProvenance provenance, String geocodeVerification,
                           boolean[] changed) {
        if (newValue == null || newValue.isBlank()) {
            return;
        }
        String oldValue = getter.get();
        if (newValue.equals(oldValue)) {
            return;
        }
        recordHistory(facilityId, fieldName, oldValue, newValue, actorId, provenance, geocodeVerification);
        setter.accept(newValue);
        changed[0] = true;
    }

    private void recordHistory(Long facilityId, String fieldName, String oldValue, String newValue,
                               String actorId, GeocodeProvenance provenance, String geocodeVerification) {
        FacilityHistoryEntity history = new FacilityHistoryEntity();
        history.setFacility(facilityRepository.getReferenceById(facilityId));
        history.setChangeType("UPDATE");
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedBy(actorId);
        history.setReason(historyMetadata(provenance, geocodeVerification));
        historyRepository.save(history);
    }

    /**
     * Serialized history metadata carried on the {@code reason} column (facility_history
     * has no jsonb metadata column; the reason field is TEXT and holds the structured
     * provenance for audit consumers).
     */
    private String historyMetadata(GeocodeProvenance provenance, String geocodeVerification) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", HISTORY_SOURCE);
        if (provenance != null) {
            Map<String, Object> prov = new LinkedHashMap<>();
            prov.put("source", provenance.source());
            if (provenance.provider() != null) prov.put("provider", provenance.provider());
            if (provenance.confidence() != null) prov.put("confidence", provenance.confidence());
            if (provenance.accuracyMeters() != null) prov.put("accuracyMeters", provenance.accuracyMeters());
            meta.put("geocodeProvenance", prov);
        }
        if (geocodeVerification != null) {
            meta.put("geocodeVerification", geocodeVerification);
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            // Never lose the governed write over metadata serialization; keep an honest marker.
            return "source=" + HISTORY_SOURCE;
        }
    }

    private void publishUpdated(FacilityEntity facility) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY");
        event.setAggregateId(facility.getId().toString());
        event.setEventType("tuso.facility.updated");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityId", facility.getId());
        payload.put("tenantId", facility.getTenantId().toString());
        payload.put("facilityCode", facility.getFacilityCode());
        payload.put("name", facility.getName());
        payload.put("status", facility.getStatus());
        payload.put("version", facility.getVersion());
        payload.put("action", "UPDATED");
        payload.put("trigger", HISTORY_SOURCE);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static boolean hasAnyGeoField(CompleteFacilityProfileRequest r) {
        return notBlank(r.addressLine1()) || notBlank(r.addressLine2()) || notBlank(r.city())
                || notBlank(r.province()) || notBlank(r.district()) || notBlank(r.ward())
                || notBlank(r.postalCode());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean valueEquals(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : (b != null && a.compareTo(b) == 0);
    }

    private static String toStringOrNull(Object o) {
        return Objects.toString(o, null);
    }
}
