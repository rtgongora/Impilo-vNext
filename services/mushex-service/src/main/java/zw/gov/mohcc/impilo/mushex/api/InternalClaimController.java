package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAdjudicationRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimCreateRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimRepository;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/claims")
public class InternalClaimController {

    private final ClaimService claimService;
    private final ClaimRepository claimRepository;

    public InternalClaimController(ClaimService claimService, ClaimRepository claimRepository) {
        this.claimService = claimService;
        this.claimRepository = claimRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClaimEntity>> createClaim(
            @Valid @RequestBody ClaimCreateRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        UUID facilityId = request.facilityId() != null
                ? UUID.fromString(request.facilityId())
                : ctx.facilityId();

        ClaimEntity claim = claimService.createClaim(
                request.billId(),
                request.insurerId(),
                facilityId,
                request.totals()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/adjudicate")
    public ResponseEntity<ApiResponse<ClaimEntity>> adjudicateClaim(
            @PathVariable String id,
            @Valid @RequestBody ClaimAdjudicationRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimEntity claim = claimService.recordAdjudication(
                id,
                request.decision(),
                request.patientResidual(),
                request.insurerPayable()
        );

        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> snapshot(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {

        int effectiveLimit = Math.min(limit, 500);
        List<ClaimEntity> items;

        if (cursor != null && !cursor.isBlank()) {
            items = claimRepository.findSnapshotPage(cursor, PageRequest.of(0, effectiveLimit + 1));
        } else {
            items = claimRepository.findSnapshotPageFromStart(PageRequest.of(0, effectiveLimit + 1));
        }

        boolean hasMore = items.size() > effectiveLimit;
        List<ClaimEntity> page = hasMore ? items.subList(0, effectiveLimit) : items;
        String nextCursor = hasMore ? page.get(page.size() - 1).getClaimId() : null;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("as_of", OffsetDateTime.now().toString());
        response.put("cursor", cursor);
        response.put("limit", effectiveLimit);
        response.put("has_more", hasMore);
        response.put("next_cursor", nextCursor);
        response.put("items", page);

        return ResponseEntity.ok(response);
    }
}
