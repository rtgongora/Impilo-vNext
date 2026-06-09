package zw.gov.mohcc.impilo.coverage.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyProgramResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage/subsidies")
public class SubsidyController {

    private final SubsidyProgramRepository subsidyProgramRepository;

    public SubsidyController(SubsidyProgramRepository subsidyProgramRepository) {
        this.subsidyProgramRepository = subsidyProgramRepository;
    }

    @GetMapping
    public ResponseEntity<List<SubsidyProgramResponse>> listActive(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UUID tid = UUID.fromString(tenantId);
        List<SubsidyProgramResponse> rows = subsidyProgramRepository.findByTenantIdAndStatus(tid, "ACTIVE").stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(rows);
    }

    private SubsidyProgramResponse toResponse(SubsidyProgramEntity entity) {
        return new SubsidyProgramResponse(
                entity.getId(),
                entity.getProgramCode(),
                entity.getProgramName(),
                entity.getPayerId(),
                entity.getSubsidyType(),
                entity.getStatus(),
                entity.getAnnualCap(),
                entity.getCurrency(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo());
    }
}
