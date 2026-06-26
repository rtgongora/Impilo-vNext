package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyConsumeRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyProgramResponse;
import zw.gov.mohcc.impilo.coverage.core.SubsidyEnrolmentService;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage/subsidies")
public class SubsidyController {

    private final SubsidyProgramRepository subsidyProgramRepository;
    private final SubsidyEnrolmentService enrolmentService;

    public SubsidyController(SubsidyProgramRepository subsidyProgramRepository,
                            SubsidyEnrolmentService enrolmentService) {
        this.subsidyProgramRepository = subsidyProgramRepository;
        this.enrolmentService = enrolmentService;
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

    /** Enrol a member into a subsidy programme (member↔subsidy↔balance link). */
    @PostMapping("/enrolments")
    public ResponseEntity<SubsidyEnrolmentResponse> enrol(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody SubsidyEnrolmentRequest body) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrolmentService.enrol(tid, body));
    }

    @GetMapping("/enrolments")
    public ResponseEntity<List<SubsidyEnrolmentResponse>> listEnrolments(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam("member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(enrolmentService.listForMember(tid, memberCpid));
    }

    @GetMapping("/enrolments/{id}")
    public ResponseEntity<SubsidyEnrolmentResponse> getEnrolment(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable("id") UUID id) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(enrolmentService.get(tid, id));
    }

    /** Draw down subsidy value against the annual cap (enforces the cap). */
    @PostMapping("/enrolments/{id}/consume")
    public ResponseEntity<SubsidyEnrolmentResponse> consume(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody SubsidyConsumeRequest body) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(enrolmentService.consume(tid, id, body));
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
