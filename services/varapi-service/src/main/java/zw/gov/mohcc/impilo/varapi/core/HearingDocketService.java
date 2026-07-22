package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hearings, committee dockets and appeals (ROM-W8, R6). The case-owning service owns the docket;
 * org-registry owns the committee organ + membership. Two invariants:
 *
 * <p><b>Docket-scoped visibility (doctrine §4.2):</b> a committee member sees ONLY cases docketed
 * to them — {@link #memberCanSee} is the in-band guard (authz V046 SHADOW mirrors it at the PDP).</p>
 *
 * <p><b>Recusal (doctrine §4.4):</b> a member may not be docketed to a case whose subject is
 * themselves.</p>
 */
@Service
public class HearingDocketService {

    public static class RecusalRequiredException extends RuntimeException {
        public RecusalRequiredException() { super("A member cannot sit on a case against themselves (recusal required)."); }
    }

    private final ProviderDisciplinaryCaseRepository caseRepository;
    private final DisciplinaryHearingRepository hearingRepository;
    private final CaseDocketAssignmentRepository docketRepository;
    private final DisciplinaryAppealRepository appealRepository;

    public HearingDocketService(ProviderDisciplinaryCaseRepository caseRepository,
                                DisciplinaryHearingRepository hearingRepository,
                                CaseDocketAssignmentRepository docketRepository,
                                DisciplinaryAppealRepository appealRepository) {
        this.caseRepository = caseRepository;
        this.hearingRepository = hearingRepository;
        this.docketRepository = docketRepository;
        this.appealRepository = appealRepository;
    }

    /** Docket a case to a committee member. Recusal-guarded (member != respondent). */
    @Transactional
    public CaseDocketAssignmentEntity assignToDocket(UUID tenantId, Long caseId, String memberHealthId, String committeeRef) {
        ProviderDisciplinaryCaseEntity c = caseRepository.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        ProviderEntity subject = c.getProvider();
        if (subject != null && subject.getImpiloHealthId() != null && memberHealthId != null
                && subject.getImpiloHealthId().toString().equalsIgnoreCase(memberHealthId.trim())) {
            throw new RecusalRequiredException();
        }
        CaseDocketAssignmentEntity a = new CaseDocketAssignmentEntity();
        a.setTenantId(tenantId);
        a.setCaseId(caseId);
        a.setCommitteeMemberHealthId(memberHealthId);
        a.setCommitteeRef(committeeRef);
        a.setStatus("ACTIVE");
        return docketRepository.save(a);
    }

    /** Docket-scoped visibility: true only when the member is actively docketed to the case. */
    @Transactional(readOnly = true)
    public boolean memberCanSee(UUID tenantId, Long caseId, String memberHealthId) {
        return docketRepository.existsByTenantIdAndCaseIdAndCommitteeMemberHealthIdAndStatus(
                tenantId, caseId, memberHealthId, "ACTIVE");
    }

    /** The cases a member may see — exactly their active docket. */
    @Transactional(readOnly = true)
    public List<CaseDocketAssignmentEntity> myDocket(UUID tenantId, String memberHealthId) {
        return docketRepository.findByTenantIdAndCommitteeMemberHealthIdAndStatus(tenantId, memberHealthId, "ACTIVE");
    }

    @Transactional
    public DisciplinaryHearingEntity scheduleHearing(UUID tenantId, Long caseId, String committeeRef, OffsetDateTime at) {
        DisciplinaryHearingEntity h = new DisciplinaryHearingEntity();
        h.setTenantId(tenantId);
        h.setCaseId(caseId);
        h.setCommitteeRef(committeeRef);
        h.setScheduledAt(at);
        h.setStatus("SCHEDULED");
        return hearingRepository.save(h);
    }

    /** File an appeal referencing the appealed determination. */
    @Transactional
    public DisciplinaryAppealEntity fileAppeal(UUID tenantId, Long caseId, String appealedDecision,
                                               String grounds, String filedBy) {
        DisciplinaryAppealEntity a = new DisciplinaryAppealEntity();
        a.setTenantId(tenantId);
        a.setCaseId(caseId);
        a.setAppealedDecision(appealedDecision);
        a.setGrounds(grounds);
        a.setFiledBy(filedBy);
        a.setStatus("FILED");
        return appealRepository.save(a);
    }
}
