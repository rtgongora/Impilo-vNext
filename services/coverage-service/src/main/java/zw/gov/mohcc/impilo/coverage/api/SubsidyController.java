package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.coverage.api.dto.EnrollMemberSubsidyRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyConsumeRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrollmentResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyProgramResponse;
import zw.gov.mohcc.impilo.coverage.core.SubsidyEnrolmentService;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage/subsidies")
public class SubsidyController {

    private final SubsidyProgramRepository subsidyProgramRepository;
    // Subsidy value enrolment with annual-cap drawdown (/enrolments)
    private final SubsidyEnrolmentService enrolmentService;
    // V013 unification: /enrollments (exemption-category link) is now served by
    // the SAME ledgered cv_subsidy_enrolments model; DTO shapes are unchanged.
    private final SubsidyEnrolmentRepository subsidyEnrolmentRepository;

    public SubsidyController(SubsidyProgramRepository subsidyProgramRepository,
                             SubsidyEnrolmentService enrolmentService,
                             SubsidyEnrolmentRepository subsidyEnrolmentRepository) {
        this.subsidyProgramRepository = subsidyProgramRepository;
        this.enrolmentService = enrolmentService;
        this.subsidyEnrolmentRepository = subsidyEnrolmentRepository;
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

    // ── Subsidy value enrolment + annual-cap drawdown (PT, L4) ──────────────

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

    // ── Per-member exemption-category enrolment (unified onto the ledgered model, V013) ──

    /**
     * Enrol a member into a subsidy programme, recording the per-member exemption category that
     * downstream costing uses to apply waivers/exemptions. Since V013 this writes the UNIFIED
     * ledgered model (cv_subsidy_enrolments) — the legacy cv_subsidy_enrollments table is
     * read-only deprecated. The external DTO shape is unchanged.
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

        SubsidyEnrolmentEntity enrolment = new SubsidyEnrolmentEntity();
        enrolment.setTenantId(tid);
        enrolment.setSubsidyProgramId(program.getId());
        enrolment.setMemberCpid(request.memberCpid());
        enrolment.setStatus("ACTIVE");
        enrolment.setCurrency(program.getCurrency() != null ? program.getCurrency() : "USD");
        enrolment.setExemptionCategory(request.exemptionCategory().trim().toUpperCase(Locale.ROOT));
        enrolment.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now());
        SubsidyEnrolmentEntity saved = subsidyEnrolmentRepository.save(enrolment);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved, program.getProgramCode()));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<List<SubsidyEnrollmentResponse>> listMemberEnrollments(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        List<SubsidyEnrollmentResponse> rows = subsidyEnrolmentRepository
                .findByTenantIdAndMemberCpid(tid, memberCpid).stream()
                .filter(e -> e.getExemptionCategory() != null)
                .sorted((a, b) -> nullSafeCompare(b.getCreatedAt(), a.getCreatedAt()))
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
        SubsidyEnrolmentEntity enrolment = subsidyEnrolmentRepository.findByIdAndTenantId(id, tid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrolment not found"));
        enrolment.setStatus("ENDED");
        enrolment.setEffectiveTo(LocalDate.now());
        SubsidyEnrolmentEntity saved = subsidyEnrolmentRepository.save(enrolment);
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

    /** Legacy /enrollments DTO shape, now sourced from the unified ledgered entity. */
    private SubsidyEnrollmentResponse toResponse(SubsidyEnrolmentEntity e, String programCode) {
        return new SubsidyEnrollmentResponse(
                e.getId(),
                e.getMemberCpid(),
                e.getSubsidyProgramId(),
                programCode,
                e.getExemptionCategory(),
                e.getStatus(),
                e.getEffectiveFrom(),
                e.getEffectiveTo());
    }

    private static int nullSafeCompare(java.time.OffsetDateTime a, java.time.OffsetDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
}
