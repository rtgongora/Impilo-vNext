package zw.gov.mohcc.impilo.coverage.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.PreauthResponse;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.PreauthRequestEntity;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.PreauthRequestRepository;

@RestController
@RequestMapping("/internal/v1/coverage/preauths")
public class MemberPreauthsController {

    private final PreauthRequestRepository preauthRepository;
    private final MemberCoverageRepository memberCoverageRepository;

    public MemberPreauthsController(
            PreauthRequestRepository preauthRepository,
            MemberCoverageRepository memberCoverageRepository) {
        this.preauthRepository = preauthRepository;
        this.memberCoverageRepository = memberCoverageRepository;
    }

    @GetMapping
    public ResponseEntity<List<PreauthResponse>> listForMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        List<UUID> coverageIds = memberCoverageRepository.findByTenantIdAndClientId(tid, memberCpid).stream()
                .map(MemberCoverageEntity::getId)
                .toList();
        if (coverageIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(preauthRepository.findByTenantIdAndCoverageIdIn(tid, coverageIds).stream()
                .map(this::toResponse)
                .toList());
    }

    private PreauthResponse toResponse(PreauthRequestEntity e) {
        return new PreauthResponse(
                e.getId(), e.getCoverageId(), e.getFacilityId(),
                e.getProviderId(), e.getRequestType(), e.getStatus(),
                e.getRequestedItems(), e.getDecisionJson(),
                e.getRequestedAt(), e.getDecidedAt(), e.getExpiresAt(),
                e.getQuantityRequested(), e.getQuantityApproved(), e.getQuantityConsumed(), e.getAnnualLimit());
    }
}
