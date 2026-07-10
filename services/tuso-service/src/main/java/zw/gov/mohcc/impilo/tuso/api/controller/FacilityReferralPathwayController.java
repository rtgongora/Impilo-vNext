package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRelationshipEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRelationshipRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Referral pathway for a facility: the REFERS_TO chain up to the national apex.
 * Pathways are structural registry truth (derived by level/district/province or
 * manually curated) — this endpoint walks them, it never invents hops.
 */
@RestController
@RequestMapping("/v1/internal/facilities")
public class FacilityReferralPathwayController {

    private static final int MAX_HOPS = 5;

    private final FacilityRelationshipRepository relationshipRepository;

    public FacilityReferralPathwayController(FacilityRelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    @GetMapping("/{facilityId}/referral-pathway")
    public ResponseEntity<ApiResponse<Map<String, Object>>> referralPathway(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> hops = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        Long current = facilityId;
        for (int hop = 0; hop < MAX_HOPS && current != null && seen.add(current); hop++) {
            FacilityEntity target = relationshipRepository.findBySourceIdAndActiveTrue(current).stream()
                    .filter(r -> "REFERS_TO".equals(r.getRelationship()))
                    .map(FacilityRelationshipEntity::getTarget)
                    .findFirst()
                    .orElse(null);
            if (target == null) break;
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("facilityId", target.getId());
            step.put("name", target.getName());
            step.put("level", target.getLevel());
            step.put("facilityType", target.getFacilityType());
            step.put("district", target.getDistrict());
            step.put("province", target.getProvince());
            hops.add(step);
            current = target.getId();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityId", facilityId);
        payload.put("pathway", hops);
        payload.put("terminal", hops.isEmpty() || current == null);
        return ResponseEntity.ok(ApiResponse.ok(payload, ctx.correlationId().toString()));
    }
}
