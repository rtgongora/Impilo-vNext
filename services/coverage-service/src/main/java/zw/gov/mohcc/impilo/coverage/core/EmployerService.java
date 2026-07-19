package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.*;
import zw.gov.mohcc.impilo.coverage.repository.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Employer / group administration (spec §24). Rosters MUST use stage -> validate -> apply;
 * they never write directly into active membership tables without validation.
 */
@Service
public class EmployerService {

    private static final Set<String> ACTIVE = Set.of("DRAFT", "DECLARED", "PENDING_VERIFICATION", "VERIFIED", "ACTIVE", "GRACE_PERIOD");

    private final EmployerRepository employerRepository;
    private final EmployerRosterBatchRepository batchRepository;
    private final EmployerRosterRowRepository rowRepository;
    private final CoveragePlanRepository planRepository;
    private final MemberCoverageRepository memberRepository;
    private final CoverageEventService eventService;

    public EmployerService(EmployerRepository employerRepository, EmployerRosterBatchRepository batchRepository,
                           EmployerRosterRowRepository rowRepository, CoveragePlanRepository planRepository,
                           MemberCoverageRepository memberRepository, CoverageEventService eventService) {
        this.employerRepository = employerRepository;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.eventService = eventService;
    }

    public record RosterRowInput(String clientId, String memberNumber, String relationship) {}

    @Transactional(readOnly = true)
    public List<EmployerEntity> listEmployers(UUID tenantId) {
        return employerRepository.findByTenantId(tenantId);
    }

    @Transactional
    public EmployerEntity register(UUID tenantId, String podId, String employerCode, String name,
                                   String contractNumber, UUID payerRef) {
        employerRepository.findByTenantIdAndEmployerCode(tenantId, employerCode).ifPresent(e -> {
            throw new IllegalArgumentException("Employer code already exists: " + employerCode);
        });
        EmployerEntity e = EmployerEntity.create(tenantId, podId, employerCode, name);
        e.setContractNumber(contractNumber);
        e.setPayerRef(payerRef);
        return employerRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<EmployerRosterBatchEntity> batches(UUID tenantId, UUID employerId) {
        return batchRepository.findByTenantIdAndEmployerId(tenantId, employerId);
    }

    @Transactional(readOnly = true)
    public EmployerRosterBatchEntity batch(UUID tenantId, UUID batchId) {
        return batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Roster batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<EmployerRosterRowEntity> rows(UUID batchId) {
        return rowRepository.findByBatchId(batchId);
    }

    /** Stage a roster (no active-table writes yet). */
    @Transactional
    public EmployerRosterBatchEntity stage(UUID tenantId, String podId, UUID employerId, UUID planId,
                                           String uploadedBy, List<RosterRowInput> rows) {
        EmployerEntity employer = employerRepository.findByIdAndTenantId(employerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employer not found: " + employerId));
        EmployerRosterBatchEntity batch = EmployerRosterBatchEntity.create(tenantId, podId, employer.getId(), planId, uploadedBy);
        batch.setTotalRows(rows != null ? rows.size() : 0);
        batchRepository.save(batch);
        if (rows != null) {
            for (RosterRowInput r : rows) {
                rowRepository.save(EmployerRosterRowEntity.create(tenantId, batch.getId(),
                        r.clientId(), r.memberNumber(), r.relationship()));
            }
        }
        return batch;
    }

    /** Validate staged rows against plan + duplicate rules. */
    @Transactional
    public EmployerRosterBatchEntity validate(UUID tenantId, UUID batchId) {
        EmployerRosterBatchEntity batch = batch(tenantId, batchId);
        CoveragePlanEntity plan = planRepository.findById(batch.getPlanId())
                .filter(p -> p.getTenantId().equals(tenantId) && "ACTIVE".equals(p.getStatus())).orElse(null);
        int valid = 0, invalid = 0;
        for (EmployerRosterRowEntity row : rowRepository.findByBatchId(batchId)) {
            String err = null;
            if (plan == null) err = "PLAN_INACTIVE";
            else if (row.getClientId() == null || row.getClientId().isBlank()) err = "CLIENT_ID_MISSING";
            else {
                boolean dup = memberRepository.findByTenantIdAndClientId(tenantId, row.getClientId()).stream()
                        .anyMatch(m -> m.getPlanId().equals(batch.getPlanId()) && ACTIVE.contains(m.getStatus()));
                if (dup) err = "ALREADY_ENROLLED";
            }
            row.setValidationStatus(err == null ? "VALID" : "INVALID");
            row.setValidationError(err);
            rowRepository.save(row);
            if (err == null) valid++; else invalid++;
        }
        batch.setValidRows(valid);
        batch.setInvalidRows(invalid);
        batch.setStatus("VALIDATED");
        batch.touch();
        return batchRepository.save(batch);
    }

    /** Apply VALID rows — create memberships (the only active-table write). */
    @Transactional
    public EmployerRosterBatchEntity apply(UUID tenantId, String podId, UUID batchId, UUID correlationId) {
        EmployerRosterBatchEntity batch = batch(tenantId, batchId);
        if (!"VALIDATED".equals(batch.getStatus())) {
            throw new IllegalStateException("Roster must be VALIDATED before apply (status: " + batch.getStatus() + ")");
        }
        int applied = 0;
        for (EmployerRosterRowEntity row : rowRepository.findByBatchId(batchId)) {
            if (!"VALID".equals(row.getValidationStatus())) continue;
            MemberCoverageEntity m = new MemberCoverageEntity(tenantId, podId, row.getClientId(), batch.getPlanId(),
                    row.getMemberNumber(), row.getRelationship(), LocalDate.now());
            m.setStatus("ACTIVE");
            m.setMemberCategory("EMPLOYER_SPONSORED");
            memberRepository.save(m);
            row.setValidationStatus("APPLIED");
            row.setAppliedMembershipId(m.getId());
            rowRepository.save(row);
            applied++;
        }
        batch.setAppliedRows(applied);
        batch.setStatus("APPLIED");
        batch.touch();
        batchRepository.save(batch);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch_id", batch.getId().toString());
        payload.put("employer_id", batch.getEmployerId().toString());
        payload.put("applied_rows", applied);
        eventService.emitDomain("EMPLOYER_ROSTER", batch.getId().toString(), "coverage.roster.applied",
                correlationId, tenantId, podId, batch.getEmployerId().toString(), "EMPLOYER_ROSTER", payload);
        return batch;
    }
}
