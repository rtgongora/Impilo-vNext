package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PracticeEstablishmentCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PracticeEstablishmentCaseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pre-licensing practice establishment (ROM-W6, R7). A prospective owner builds a practice profile
 * BEFORE any facility exists; the canonical tuso.facility is created only on APPROVAL. The
 * FacilityApplicationType enum is untouched — this is a new case rail on the V018 spine.
 *
 * <p>The facility-only-on-approval rule is additionally enforced by a DB check constraint
 * (chk_facility_only_on_approval) so it cannot be violated even by a direct write.</p>
 */
@Service
public class PracticeEstablishmentService {

    private final PracticeEstablishmentCaseRepository repository;

    public PracticeEstablishmentService(PracticeEstablishmentCaseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PracticeEstablishmentCaseEntity createDraft(UUID tenantId, UUID applicantHealthId,
                                                       String proposedName, String categoryCode,
                                                       String proposedProvince) {
        PracticeEstablishmentCaseEntity c = new PracticeEstablishmentCaseEntity();
        c.setTenantId(tenantId);
        c.setApplicantHealthId(applicantHealthId);
        c.setProposedName(proposedName);
        c.setCategoryCode(categoryCode);
        c.setProposedProvince(proposedProvince);
        c.setStatus("DRAFT");
        return repository.save(c);
    }

    @Transactional
    public PracticeEstablishmentCaseEntity submit(UUID tenantId, UUID establishmentId) {
        PracticeEstablishmentCaseEntity c = require(tenantId, establishmentId);
        if (!"DRAFT".equals(c.getStatus()) && !"RFI_OPEN".equals(c.getStatus())) {
            throw new IllegalStateException("Only a DRAFT/RFI_OPEN establishment can be submitted");
        }
        c.setStatus("SUBMITTED");
        return repository.save(c);
    }

    /**
     * Approve the establishment and materialise the canonical facility. The facility UUID is
     * created here and stamped on the case; only in the APPROVED state may a facility exist.
     */
    @Transactional
    public PracticeEstablishmentCaseEntity approve(UUID tenantId, UUID establishmentId,
                                                   String decidedBy, String decisionNotes) {
        PracticeEstablishmentCaseEntity c = require(tenantId, establishmentId);
        c.setStatus("APPROVED");
        // The canonical facility comes into existence at approval. (Full tuso.facility row
        // materialisation via the facility service is the W6 wiring follow-on; the identity and
        // the invariant are established here.)
        c.setCreatedFacilityUuid(UUID.randomUUID());
        c.setDecidedBy(decidedBy);
        c.setDecisionNotes(decisionNotes);
        c.setDecidedAt(OffsetDateTime.now());
        return repository.save(c);
    }

    @Transactional
    public PracticeEstablishmentCaseEntity reject(UUID tenantId, UUID establishmentId,
                                                  String decidedBy, String decisionNotes) {
        PracticeEstablishmentCaseEntity c = require(tenantId, establishmentId);
        c.setStatus("REJECTED");
        c.setDecidedBy(decidedBy);
        c.setDecisionNotes(decisionNotes);
        c.setDecidedAt(OffsetDateTime.now());
        return repository.save(c);
    }

    @Transactional(readOnly = true)
    public List<PracticeEstablishmentCaseEntity> forApplicant(UUID tenantId, UUID applicantHealthId) {
        return repository.findByTenantIdAndApplicantHealthIdOrderByCreatedAtDesc(tenantId, applicantHealthId);
    }

    private PracticeEstablishmentCaseEntity require(UUID tenantId, UUID establishmentId) {
        PracticeEstablishmentCaseEntity c = repository.findById(establishmentId)
                .orElseThrow(() -> new IllegalArgumentException("Establishment not found: " + establishmentId));
        if (!tenantId.equals(c.getTenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return c;
    }
}
