package zw.gov.mohcc.impilo.pct.core.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.*;

/**
 * Community work-context service (PCT) — the sixth work context (journey §5.5).
 *
 * <p>Backs the provider-app outreach screens (household registry, community visits, screening) that were
 * previously NotWired to a backend context. PCT is the SoR for the clinical encounter, so this lives here.</p>
 *
 * <p><strong>Offline reconciliation:</strong> records carry a client-generated {@code offlineId}. The
 * {@link #reconcile} entry point replays a batch captured offline and is idempotent on {@code offlineId} —
 * re-sending the same record returns the already-persisted row rather than duplicating it. This is the
 * sync-on-reconnect contract the field worker depends on.</p>
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code CHECKIN-SCOPE}/community family,
 * track P). Outbox events let downstream surfaces (surveillance, COSTA community value) consume the visit.</p>
 */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private final HouseholdRepository householdRepository;
    private final CommunityVisitRepository visitRepository;
    private final CommunityScreeningRepository screeningRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CommunityService(HouseholdRepository householdRepository,
                            CommunityVisitRepository visitRepository,
                            CommunityScreeningRepository screeningRepository,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.householdRepository = householdRepository;
        this.visitRepository = visitRepository;
        this.screeningRepository = screeningRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // ---- Households -------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<HouseholdEntity> listHouseholds(UUID facilityId, int page, int size) {
        return householdRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(
                TrustContextHolder.require().tenantId(), facilityId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public HouseholdEntity getHousehold(UUID id) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        HouseholdEntity h = householdRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Household not found: " + id));
        if (!h.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Household not found: " + id);
        }
        return h;
    }

    @Transactional
    public HouseholdEntity registerHousehold(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String offlineId = str(body.get("offline_id"));
        if (offlineId != null) {
            Optional<HouseholdEntity> existing = householdRepository.findByTenantIdAndOfflineId(ctx.tenantId(), offlineId);
            if (existing.isPresent()) {
                return existing.get(); // idempotent replay
            }
        }
        HouseholdEntity h = new HouseholdEntity();
        h.setTenantId(ctx.tenantId());
        h.setFacilityId(UUID.fromString(required(body, "facility_id")));
        h.setHeadOfHousehold(required(body, "head_of_household"));
        h.setAddress(str(body.get("address")));
        h.setGpsLatitude(dbl(body.get("gps_latitude")));
        h.setGpsLongitude(dbl(body.get("gps_longitude")));
        h.setRiskCategory(upperOr(body.get("risk_category"), "STANDARD"));
        h.setMembers(toJson(body.getOrDefault("members", List.of())));
        h.setOfflineId(offlineId);
        h.setRegisteredBy(ctx.actorId());
        h = householdRepository.save(h);
        emit("HOUSEHOLD", h.getHouseholdId().toString(), "HOUSEHOLD_REGISTERED",
                Map.of("householdId", h.getHouseholdId().toString(), "facilityId", h.getFacilityId().toString(),
                        "actorId", ctx.actorId()));
        return h;
    }

    // ---- Visits -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommunityVisitEntity> visitsForHousehold(UUID householdId) {
        return visitRepository.findByTenantIdAndHouseholdIdOrderByCreatedAtDesc(
                TrustContextHolder.require().tenantId(), householdId);
    }

    @Transactional
    public CommunityVisitEntity recordVisit(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String offlineId = str(body.get("offline_id"));
        if (offlineId != null) {
            Optional<CommunityVisitEntity> existing = visitRepository.findByTenantIdAndOfflineId(ctx.tenantId(), offlineId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        CommunityVisitEntity v = new CommunityVisitEntity();
        v.setTenantId(ctx.tenantId());
        v.setHouseholdId(UUID.fromString(required(body, "household_id")));
        String fac = str(body.get("facility_id"));
        if (fac != null) v.setFacilityId(UUID.fromString(fac));
        v.setVisitType(upperOr(body.get("visit_type"), "HOUSEHOLD_VISIT"));
        v.setStatus(upperOr(body.get("status"), "COMPLETED"));
        v.setFindings(str(body.get("findings")));
        v.setReferrals(toJson(body.getOrDefault("referrals", List.of())));
        v.setGpsLatitude(dbl(body.get("gps_latitude")));
        v.setGpsLongitude(dbl(body.get("gps_longitude")));
        String vd = str(body.get("visit_date"));
        if (vd != null) v.setVisitDate(LocalDate.parse(vd));
        v.setCapturedOffline(bool(body.get("captured_offline")));
        v.setOfflineId(offlineId);
        v.setVisitedBy(ctx.actorId());
        v = visitRepository.save(v);
        emit("COMMUNITY_VISIT", v.getVisitId().toString(), "COMMUNITY_VISIT_RECORDED",
                Map.of("visitId", v.getVisitId().toString(), "householdId", v.getHouseholdId().toString(),
                        "visitType", v.getVisitType(), "capturedOffline", v.isCapturedOffline(), "actorId", ctx.actorId()));
        return v;
    }

    // ---- Screenings -------------------------------------------------------

    @Transactional
    public CommunityScreeningEntity recordScreening(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String offlineId = str(body.get("offline_id"));
        if (offlineId != null) {
            Optional<CommunityScreeningEntity> existing = screeningRepository.findByTenantIdAndOfflineId(ctx.tenantId(), offlineId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        CommunityScreeningEntity s = new CommunityScreeningEntity();
        s.setTenantId(ctx.tenantId());
        String hh = str(body.get("household_id"));
        if (hh != null) s.setHouseholdId(UUID.fromString(hh));
        String vid = str(body.get("visit_id"));
        if (vid != null) s.setVisitId(UUID.fromString(vid));
        s.setPatientId(required(body, "patient_id"));
        s.setScreeningType(required(body, "screening_type"));
        s.setResults(toJson(body.getOrDefault("results", Map.of())));
        s.setReferralMade(bool(body.get("referral_made")));
        s.setCapturedOffline(bool(body.get("captured_offline")));
        s.setOfflineId(offlineId);
        s.setScreenedBy(ctx.actorId());
        s = screeningRepository.save(s);
        emit("COMMUNITY_SCREENING", s.getScreeningId().toString(), "COMMUNITY_SCREENING_RECORDED",
                Map.of("screeningId", s.getScreeningId().toString(), "patientId", s.getPatientId(),
                        "screeningType", s.getScreeningType(), "actorId", ctx.actorId()));
        return s;
    }

    // ---- Offline reconciliation ------------------------------------------

    /**
     * Replay a batch of records captured offline. Each record is keyed by its {@code offline_id} and applied
     * idempotently: a record whose {@code offline_id} already exists is treated as already-synced (no
     * duplicate). The result reports per-kind counts and the server ids assigned, so the client can clear its
     * local queue and map local→server ids.
     */
    @Transactional
    public Map<String, Object> reconcile(Map<String, Object> batch) {
        List<Map<String, Object>> households = listOfMaps(batch.get("households"));
        List<Map<String, Object>> visits = listOfMaps(batch.get("visits"));
        List<Map<String, Object>> screenings = listOfMaps(batch.get("screenings"));

        List<Map<String, Object>> hhResults = new ArrayList<>();
        for (Map<String, Object> h : households) {
            HouseholdEntity e = registerHousehold(h);
            hhResults.add(idMap(str(h.get("offline_id")), e.getHouseholdId().toString(), "household_id"));
        }
        List<Map<String, Object>> visitResults = new ArrayList<>();
        for (Map<String, Object> v : visits) {
            CommunityVisitEntity e = recordVisit(v);
            visitResults.add(idMap(str(v.get("offline_id")), e.getVisitId().toString(), "visit_id"));
        }
        List<Map<String, Object>> screenResults = new ArrayList<>();
        for (Map<String, Object> s : screenings) {
            CommunityScreeningEntity e = recordScreening(s);
            screenResults.add(idMap(str(s.get("offline_id")), e.getScreeningId().toString(), "screening_id"));
        }

        log.info("pct.community.reconciled households={} visits={} screenings={} by={} correlationId={}",
                hhResults.size(), visitResults.size(), screenResults.size(),
                TrustContextHolder.require().actorId(), TrustContextHolder.require().correlationId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("households", hhResults);
        out.put("visits", visitResults);
        out.put("screenings", screenResults);
        out.put("reconciled_count", hhResults.size() + visitResults.size() + screenResults.size());
        return out;
    }

    // ---- helpers ----------------------------------------------------------

    private void emit(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private static Map<String, Object> idMap(String offlineId, String serverId, String idKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offline_id", offlineId);
        m.put(idKey, serverId);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return String.valueOf(v).trim();
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String upperOr(Object v, String def) {
        String s = str(v);
        return s == null ? def : s.toUpperCase(Locale.ROOT);
    }

    private static Double dbl(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return null; }
    }

    private static boolean bool(Object v) {
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise community payload: {}", e.getMessage());
            return "{}";
        }
    }
}
