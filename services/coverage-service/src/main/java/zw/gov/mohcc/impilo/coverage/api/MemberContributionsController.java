package zw.gov.mohcc.impilo.coverage.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.MemberContributionResponse;
import zw.gov.mohcc.impilo.coverage.domain.MemberContributionEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.MemberContributionRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

@RestController
@RequestMapping("/internal/v1/coverage/contributions")
public class MemberContributionsController {

    private final MemberContributionRepository contributionRepository;
    private final MemberCoverageRepository memberCoverageRepository;

    public MemberContributionsController(
            MemberContributionRepository contributionRepository,
            MemberCoverageRepository memberCoverageRepository) {
        this.contributionRepository = contributionRepository;
        this.memberCoverageRepository = memberCoverageRepository;
    }

    @GetMapping
    public ResponseEntity<List<MemberContributionResponse>> listForMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        List<UUID> memberRowIds = memberCoverageRepository.findByTenantIdAndClientId(tid, memberCpid).stream()
                .map(MemberCoverageEntity::getId)
                .toList();
        if (memberRowIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(contributionRepository.findByTenantIdAndMemberIdIn(tid, memberRowIds).stream()
                .map(this::toResponse)
                .toList());
    }

    private MemberContributionResponse toResponse(MemberContributionEntity e) {
        return new MemberContributionResponse(
                e.getId(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getAmount(),
                e.getCurrency(),
                e.getStatus(),
                e.getPaidAt(),
                e.getPaymentRef());
    }
}
