package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.SubmitProviderAccessRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAccessRequestRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;

/**
 * Claim-by-registration-number for HPA-imported practitioners (HAR W3).
 *
 * <p><b>Why this exists.</b> {@code ProviderBootstrapService.claimProfile} needs a single-use claim
 * token, and the HPA import minted none — there is no contact channel for these 4,241 people, so
 * issuing 4,241 tokens would be useless. The practitioner instead arrives person-first (a verified
 * Health ID), states the council registration number HPA recorded against them, and asks to be
 * linked.</p>
 *
 * <p><b>What it deliberately does NOT do: bind.</b> No provider row is mutated here. The request is
 * recorded on the existing {@code provider_access_request} rail as a {@code COUNCIL_NUMBER} request
 * for a human decision. Matching a registration number is not proof of identity — anyone can read a
 * registration number off a certificate on a wall — so an automatic bind would be an
 * account-takeover primitive against a person who has never used the platform.</p>
 *
 * <p><b>Anti-enumeration.</b> The caller receives an identical acknowledgement whether or not the
 * number resolves to a preloaded profile. The resolution outcome is written only into the
 * reviewer-facing evidence summary, so a prober cannot use this endpoint to discover which
 * registration numbers exist — the same discipline {@code FacilityClaimService} applies to facility
 * claims.</p>
 */
@Service
public class HpaPractitionerClaimService {

    private static final Logger log = LoggerFactory.getLogger(HpaPractitionerClaimService.class);

    private static final String LIFECYCLE_PRELOADED =
            zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus.PRELOADED.name();
    private static final String ORIGIN_COUNCIL_IMPORT = "COUNCIL_IMPORT";

    /** The single message every claimant sees, resolved or not. */
    static final String UNIFORM_ACKNOWLEDGEMENT =
            "Your request has been recorded and will be reviewed by the registry. "
                    + "If the registration number matches a listed profile, you will be contacted to "
                    + "complete verification. Nothing is linked to your account until a registrar approves it.";

    private final ProviderAccessRequestService accessRequestService;
    private final ProviderAccessRequestRepository accessRequestRepository;
    private final ProviderRepository providerRepository;

    public HpaPractitionerClaimService(ProviderAccessRequestService accessRequestService,
                                       ProviderAccessRequestRepository accessRequestRepository,
                                       ProviderRepository providerRepository) {
        this.accessRequestService = accessRequestService;
        this.accessRequestRepository = accessRequestRepository;
        this.providerRepository = providerRepository;
    }

    /** Uniform acknowledgement — carries no signal about whether the number resolved. */
    public record ClaimAcknowledgement(String requestPublicId, String status, String message) {}

    @Transactional
    public ClaimAcknowledgement claimByRegistrationNumber(String registrationNumber, String councilCode) {
        TrustContext ctx = TrustContextHolder.require();
        if (registrationNumber == null || registrationNumber.isBlank()) {
            throw new IllegalArgumentException("A council registration number is required");
        }
        String regno = registrationNumber.trim();

        // Recorded on the shared access-request rail: masks the number, routes to a reviewer,
        // publishes the submitted event, and binds nothing.
        ProviderAccessRequestEntity request = accessRequestService.submit(new SubmitProviderAccessRequest(
                "COUNCIL_NUMBER", null, councilCode, regno, null, null,
                "Practitioner-initiated claim of an HPA-listed profile",
                null, null, null, null));

        // Resolve for the REVIEWER only. This never reaches the caller.
        Optional<ProviderEntity> match = providerRepository
                .findFirstByTenantIdAndPracticeNumberIgnoreCase(ctx.tenantId(), regno)
                .filter(p -> LIFECYCLE_PRELOADED.equals(p.getLifecycleStatus()))
                .filter(p -> ORIGIN_COUNCIL_IMPORT.equalsIgnoreCase(p.getBootstrapOrigin()));

        if (match.isPresent()) {
            ProviderEntity provider = match.get();
            request.setProviderPublicId(provider.getProviderPublicId());
            request.setEvidenceSummary(
                    "Matches an HPA-listed preloaded profile awaiting claim. Verify the claimant's "
                            + "identity against the council register before approving — a registration "
                            + "number alone is not proof of identity.");
            accessRequestRepository.save(request);
        } else {
            request.setEvidenceSummary(
                    "No HPA-listed preloaded profile matches this registration number. Treat as a "
                            + "new-registration enquiry.");
            accessRequestRepository.save(request);
        }

        // Log without the outcome so the application log is not an enumeration oracle either.
        log.info("HAR W3 practitioner claim recorded request={} (outcome withheld from caller)",
                request.getPublicId());

        return new ClaimAcknowledgement(request.getPublicId(), request.getStatus(), UNIFORM_ACKNOWLEDGEMENT);
    }
}
