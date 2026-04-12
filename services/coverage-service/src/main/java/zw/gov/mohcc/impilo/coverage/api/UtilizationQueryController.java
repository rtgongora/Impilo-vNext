package zw.gov.mohcc.impilo.coverage.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.domain.UtilizationSummaryEntity;
import zw.gov.mohcc.impilo.coverage.repository.UtilizationSummaryRepository;

@RestController
@RequestMapping("/internal/v1/coverage")
public class UtilizationQueryController {

    private final UtilizationSummaryRepository utilizationSummaryRepository;

    public UtilizationQueryController(UtilizationSummaryRepository utilizationSummaryRepository) {
        this.utilizationSummaryRepository = utilizationSummaryRepository;
    }

    @GetMapping("/utilization")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UtilizationSummaryEntity>> listForMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(utilizationSummaryRepository.findByTenantIdAndMemberCpidOrderByBenefitCodeAscPeriodStartDesc(
                tid, memberCpid));
    }
}
