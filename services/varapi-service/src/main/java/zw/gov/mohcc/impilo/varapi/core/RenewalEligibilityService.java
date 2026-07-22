package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.core.CpdService.CpdSummary;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Licence-renewal eligibility gate (ROM-W5). Renewal is gated on the council's CPD requirement,
 * read as VARAPI-adjudicated status (never raw Fundo records — doctrine §9). Serves both sides of
 * the slice: the applicant sees whether they can renew and what is outstanding; the renewal action
 * consults the same gate. Fundo evidences; Varapi adjudicates; renewal consumes.
 */
@Service
public class RenewalEligibilityService {

    private final ProviderRepository providerRepository;
    private final CpdService cpdService;

    public RenewalEligibilityService(ProviderRepository providerRepository, CpdService cpdService) {
        this.providerRepository = providerRepository;
        this.cpdService = cpdService;
    }

    public record RenewalEligibility(boolean linked, String providerPublicId, boolean eligible,
                                     boolean cpdRequirementMet, int earnedPoints, int requiredPoints,
                                     List<String> outstanding) {
        static RenewalEligibility unlinked() {
            return new RenewalEligibility(false, null, false, false, 0, 0, List.of("No linked professional registration."));
        }
    }

    /** Evaluate for the authenticated person (Health-ID → provider). */
    @Transactional(readOnly = true)
    public RenewalEligibility evaluateForPerson(UUID tenantId, String personHealthId) {
        UUID healthId = parseUuid(personHealthId);
        if (healthId == null) {
            return RenewalEligibility.unlinked();
        }
        ProviderEntity provider = providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId).orElse(null);
        if (provider == null) {
            return RenewalEligibility.unlinked();
        }
        return evaluate(provider.getProviderPublicId());
    }

    /** Evaluate for a specific provider public id. */
    @Transactional(readOnly = true)
    public RenewalEligibility evaluate(String providerPublicId) {
        CpdSummary cpd = cpdService.getCpdSummary(providerPublicId);
        List<String> outstanding = new ArrayList<>();
        boolean cpdMet = cpd.requirementMet();
        if (!cpdMet) {
            int shortfall = Math.max(0, cpd.requiredPoints() - cpd.earnedPoints());
            outstanding.add(shortfall > 0
                    ? "CPD requirement not met — " + shortfall + " point(s) outstanding this cycle."
                    : "CPD requirement not yet confirmed for the current cycle.");
        }
        boolean eligible = cpdMet;
        return new RenewalEligibility(true, providerPublicId, eligible, cpdMet,
                cpd.earnedPoints(), cpd.requiredPoints(), outstanding);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
