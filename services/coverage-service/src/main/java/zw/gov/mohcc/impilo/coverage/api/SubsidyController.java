package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.coverage.api.dto.EnrollMemberSubsidyRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrollmentResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyProgramResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrollmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrollmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage/subsidies")
public class SubsidyController {

    private final SubsidyProgramRepository subsidyProgramRepository;
    private final SubsidyEnrollmentRepository subsidyEnrollmentRepository;

    public SubsidyController(SubsidyProgramRepository subsidyProgramRepository,
                             SubsidyEnrollmentRepository subsidyEnrollmentRepository) {
        this.subsidyProgramRepository = subsidyProgramRepository;
        this.subsidyEnrollmentRepository = subsidyEnrollmentRepository;
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

    /**
     * Enrol a member into a subsidy programme, recording the per-member exemption category that
     * downstream costing uses to apply waivers/exemptions.
     */
    @PostMapping("/enrollments")
    @Transactional
    public ResponseEntity<SubsidyEnrollmentResponse> enrollMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody EnrollMemberSubsidyRequest request) {
        UUID tid = UUID.fromString(tenantId);
        SubsidyProgramEntity program = subsidyProgramRepository.findByTenantIdAndStatus(tid, "ACTIVE").stream()
                .filter(p -> p.getProgramCode().equalsIgnoreCase(request.programCode()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Active subsidy programme not found: " + request.programCode()));

        SubsidyEnrollmentEntity enrollment = new SubsidyEnrollmentEntity(
                tid, "national-spine", request.memberCpid(), program.getId(),
                request.exemptionCategory().trim().toUpperCase(Locale.ROOT),
                request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now());
        SubsidyEnrollmentEntity saved = subsidyEnrollmentRepository.save(enrollment);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved, program.getProgramCode()));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<List<SubsidyEnrollmentResponse>> listMemberEnrollments(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        List<SubsidyEnrollmentResponse> rows = subsidyEnrollmentRepository
                .findByTenantIdAndClientIdOrderByCreatedAtDesc(tid, memberCpid).stream()
                .map(e -> toResponse(e, programCode(e.getSubsidyProgramId())))
                .toList();
        return ResponseEntity.ok(rows);
    }

    /** End an active enrolment (e.g. eligibility lapsed). */
    @PostMapping("/enrollments/{id}/end")
    @Transactional
    public ResponseEntity<SubsidyEnrollmentResponse> endEnrollment(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        SubsidyEnrollmentEntity enrollment = subsidyEnrollmentRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrolment not found"));
        enrollment.setStatus("ENDED");
        enrollment.setEffectiveTo(LocalDate.now());
        SubsidyEnrollmentEntity saved = subsidyEnrollmentRepository.save(enrollment);
        return ResponseEntity.ok(toResponse(saved, programCode(saved.getSubsidyProgramId())));
    }

    private String programCode(UUID programId) {
        return subsidyProgramRepository.findById(programId)
                .map(SubsidyProgramEntity::getProgramCode)
                .orElse(null);
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

    private SubsidyEnrollmentResponse toResponse(SubsidyEnrollmentEntity e, String programCode) {
        return new SubsidyEnrollmentResponse(
                e.getId(),
                e.getClientId(),
                e.getSubsidyProgramId(),
                programCode,
                e.getExemptionCategory(),
                e.getStatus(),
                e.getEffectiveFrom(),
                e.getEffectiveTo());
    }
}
