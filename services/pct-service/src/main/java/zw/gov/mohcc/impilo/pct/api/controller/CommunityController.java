package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.community.CommunityService;
import zw.gov.mohcc.impilo.pct.persistence.entity.CommunityScreeningEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.CommunityVisitEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.HouseholdEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Community work-context API (PCT) — backs the provider-app outreach screens (household registry, community
 * visits, screening) and the offline reconciliation sync-on-reconnect contract.
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (community/check-in scope, track P). Every write
 * is audited via the outbox.</p>
 */
@RestController
@RequestMapping("/v1/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    private String corr() {
        return TrustContextHolder.require().correlationId().toString();
    }

    // ---- Households -------------------------------------------------------

    @GetMapping("/households")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listHouseholds(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<HouseholdEntity> p = communityService.listHouseholds(facilityId, page, size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("households", p.getContent().stream().map(this::toHousehold).collect(Collectors.toList()));
        out.put("total_elements", p.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    @GetMapping("/households/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHousehold(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(toHousehold(communityService.getHousehold(id)), corr()));
    }

    @PostMapping("/households")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerHousehold(@RequestBody Map<String, Object> body) {
        HouseholdEntity h = communityService.registerHousehold(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toHousehold(h), corr()));
    }

    // ---- Visits -----------------------------------------------------------

    @GetMapping("/households/{id}/visits")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> visits(@PathVariable UUID id) {
        List<Map<String, Object>> out = communityService.visitsForHousehold(id)
                .stream().map(this::toVisit).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    @PostMapping("/visits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordVisit(@RequestBody Map<String, Object> body) {
        CommunityVisitEntity v = communityService.recordVisit(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toVisit(v), corr()));
    }

    // ---- Screenings -------------------------------------------------------

    @PostMapping("/screenings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordScreening(@RequestBody Map<String, Object> body) {
        CommunityScreeningEntity s = communityService.recordScreening(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toScreening(s), corr()));
    }

    // ---- Offline reconciliation ------------------------------------------

    /** Replay a batch captured offline (households/visits/screenings). Idempotent on offline_id. */
    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcile(@RequestBody Map<String, Object> batch) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.reconcile(batch), corr()));
    }

    // ---- mappers ----------------------------------------------------------

    private Map<String, Object> toHousehold(HouseholdEntity h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("household_id", h.getHouseholdId().toString());
        m.put("facility_id", h.getFacilityId().toString());
        m.put("head_of_household", h.getHeadOfHousehold());
        m.put("address", h.getAddress());
        m.put("gps_latitude", h.getGpsLatitude());
        m.put("gps_longitude", h.getGpsLongitude());
        m.put("risk_category", h.getRiskCategory());
        m.put("members", h.getMembers());
        m.put("last_visit_date", h.getLastVisitDate() != null ? h.getLastVisitDate().toString() : null);
        m.put("next_visit_date", h.getNextVisitDate() != null ? h.getNextVisitDate().toString() : null);
        m.put("offline_id", h.getOfflineId());
        m.put("registered_by", h.getRegisteredBy());
        m.put("created_at", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toVisit(CommunityVisitEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visit_id", v.getVisitId().toString());
        m.put("household_id", v.getHouseholdId().toString());
        m.put("facility_id", v.getFacilityId() != null ? v.getFacilityId().toString() : null);
        m.put("visit_type", v.getVisitType());
        m.put("status", v.getStatus());
        m.put("findings", v.getFindings());
        m.put("referrals", v.getReferrals());
        m.put("gps_latitude", v.getGpsLatitude());
        m.put("gps_longitude", v.getGpsLongitude());
        m.put("visit_date", v.getVisitDate() != null ? v.getVisitDate().toString() : null);
        m.put("captured_offline", v.isCapturedOffline());
        m.put("offline_id", v.getOfflineId());
        m.put("visited_by", v.getVisitedBy());
        m.put("created_at", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toScreening(CommunityScreeningEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("screening_id", s.getScreeningId().toString());
        m.put("household_id", s.getHouseholdId() != null ? s.getHouseholdId().toString() : null);
        m.put("visit_id", s.getVisitId() != null ? s.getVisitId().toString() : null);
        m.put("patient_id", s.getPatientId());
        m.put("screening_type", s.getScreeningType());
        m.put("results", s.getResults());
        m.put("referral_made", s.isReferralMade());
        m.put("captured_offline", s.isCapturedOffline());
        m.put("offline_id", s.getOfflineId());
        m.put("screened_by", s.getScreenedBy());
        m.put("created_at", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
        return m;
    }
}
