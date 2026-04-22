package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.EligibilityCheckRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.EligibilityResult;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderEligibilityRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderEligibilityResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PrivilegeRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login eligibility checks consumed by TSHEPO during authentication.
 *
 * Determines whether a provider is eligible to access clinical systems
 * based on their registration status, license validity, and facility
 * privileges. Called by TSHEPO's ext_authz pipeline to enforce
 * practice-level access control.
 *
 * Eligibility criteria:
 *   1. Provider exists and status is ACTIVE
 *   2. At least one ACTIVE license that is not expired
 *   3. If facilityId is provided, has an APPROVED privilege for that facility
 *   4. Returns step-up flags for enhanced authentication when needed
 */
@Service
public class EligibilityService {

    private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

    private final ProviderRepository providerRepository;
    private final LicenseRepository licenseRepository;
    private final PrivilegeRepository privilegeRepository;
    private final TokenService tokenService;
    private final CertificateService certificateService;
    private final ComplianceService complianceService;
    private final AffiliationService affiliationService;
    private final PractitionerInChargeService picService;

    public EligibilityService(ProviderRepository providerRepository,
                              LicenseRepository licenseRepository,
                              PrivilegeRepository privilegeRepository,
                              TokenService tokenService,
                              CertificateService certificateService,
                              ComplianceService complianceService,
                              AffiliationService affiliationService,
                              PractitionerInChargeService picService) {
        this.providerRepository = providerRepository;
        this.licenseRepository = licenseRepository;
        this.privilegeRepository = privilegeRepository;
        this.tokenService = tokenService;
        this.certificateService = certificateService;
        this.complianceService = complianceService;
        this.affiliationService = affiliationService;
        this.picService = picService;
    }

    // ---- Public API ----

    /**
     * Check whether a provider is eligible to access clinical systems.
     *
     * Resolution order:
     *   1. If providerPublicId is provided, resolve directly
     *   2. Otherwise, attempt VA token verification via TokenService
     *
     * @param request the eligibility check parameters
     * @return the eligibility result with coded reasons if ineligible
     */
    @Transactional(readOnly = true)
    public EligibilityResult checkEligibility(EligibilityCheckRequest request) {
        TrustContextHolder.require();
        log.debug("Checking eligibility: providerPublicId={}, haVaToken={}, facilityId={}",
                request.providerPublicId(),
                request.vaToken() != null && !request.vaToken().isBlank(),
                request.facilityId());

        List<String> reasons = new ArrayList<>();
        String resolvedProviderPublicId = null;

        // Step 1: Resolve provider identity
        if (request.providerPublicId() != null && !request.providerPublicId().isBlank()) {
            resolvedProviderPublicId = request.providerPublicId();
        } else if (request.vaToken() != null && !request.vaToken().isBlank()) {
            resolvedProviderPublicId = tokenService.verifyToken(request.vaToken());
            if (resolvedProviderPublicId == null) {
                reasons.add("INVALID_TOKEN");
                return new EligibilityResult(false, reasons, null, false, null);
            }
        } else {
            reasons.add("NO_IDENTITY_PROVIDED");
            return new EligibilityResult(false, reasons, null, false, null);
        }

        // Step 2: Check provider exists and is ACTIVE
        Optional<ProviderEntity> providerOpt =
                providerRepository.findByProviderPublicId(resolvedProviderPublicId);
        if (providerOpt.isEmpty()) {
            reasons.add("PROVIDER_NOT_FOUND");
            return new EligibilityResult(false, reasons, null, false, resolvedProviderPublicId);
        }

        ProviderEntity provider = providerOpt.get();
        if (!provider.isActive()) {
            reasons.add("PROVIDER_NOT_ACTIVE");
            log.debug("Provider not eligible: status={}", provider.getStatus());
            return new EligibilityResult(false, reasons, null, false, resolvedProviderPublicId);
        }

        // Step 3: Check for at least one ACTIVE, non-expired license
        Optional<LicenseEntity> activeLicenseOpt =
                licenseRepository.findActiveByProviderId(provider.getId());
        LocalDate licenseValidUntil = null;

        if (activeLicenseOpt.isEmpty()) {
            reasons.add("NO_ACTIVE_LICENSE");
            log.debug("Provider not eligible: no active license");
        } else {
            LicenseEntity activeLicense = activeLicenseOpt.get();
            licenseValidUntil = activeLicense.getValidTo();

            if (activeLicense.isExpired()) {
                reasons.add("LICENSE_EXPIRED");
                log.debug("Provider not eligible: license expired on {}", activeLicense.getValidTo());
            }
        }

        // Step 4: Check facility privilege if facilityId provided
        boolean stepUpRequired = false;
        if (request.facilityId() != null) {
            List<PrivilegeEntity> facilityPrivileges =
                    privilegeRepository.findByProviderIdAndFacilityIdAndStatus(
                            provider.getId(), request.facilityId(), "APPROVED");
            if (facilityPrivileges.isEmpty()) {
                reasons.add("NO_FACILITY_PRIVILEGE");
                log.debug("Provider not eligible: no APPROVED privilege for facilityId={}",
                        request.facilityId());
            }

            // Check if step-up authentication is needed (e.g., sensitive facility)
            // Step-up is required if any privilege for this facility demands it
            for (PrivilegeEntity priv : facilityPrivileges) {
                if ("RESTRICTED".equals(priv.getScope())) {
                    stepUpRequired = true;
                    break;
                }
            }
        }

        boolean eligible = reasons.isEmpty();
        log.info("Eligibility check complete: providerPublicId={}, eligible={}, reasons={}",
                resolvedProviderPublicId, eligible, reasons);

        return new EligibilityResult(
                eligible,
                reasons,
                licenseValidUntil,
                stepUpRequired,
                resolvedProviderPublicId
        );
    }

    /**
     * Check provider eligibility for TUSO interoperability.
     * Returns detailed eligibility status for facility access decisions.
     */
    @Transactional(readOnly = true)
    public ProviderEligibilityResponse checkEligibility(ProviderEligibilityRequest request) {
        log.info("TUSO eligibility check: providerId={}, facilityId={}",
                request.providerId(), request.facilityId());

        List<String> reasons = new ArrayList<>();
        ProviderEntity provider = null;

        if (request.providerId() != null) {
            provider = providerRepository.findById(request.providerId()).orElse(null);
        } else if (request.providerPublicId() != null) {
            provider = providerRepository.findByProviderPublicId(request.providerPublicId()).orElse(null);
        }

        if (provider == null) {
            return ProviderEligibilityResponse.ineligible(
                    request.providerId(),
                    request.providerPublicId(),
                    new String[]{"PROVIDER_NOT_FOUND"}
            );
        }

        if (!provider.isActive()) {
            reasons.add("PROVIDER_NOT_ACTIVE");
        }

        boolean hasValidLicense = false;
        LocalDate licenseExpiry = null;
        if (request.checkLicenseStatus()) {
            Optional<LicenseEntity> activeLicense = licenseRepository.findActiveByProviderId(provider.getId());
            if (activeLicense.isPresent() && !activeLicense.get().isExpired()) {
                hasValidLicense = true;
                licenseExpiry = activeLicense.get().getValidTo();
            } else {
                reasons.add("NO_VALID_LICENSE");
            }
        }

        boolean hasCert = false;
        LocalDate certExpiry = null;
        if (request.checkCertificateStatus()) {
            var cert = certificateService.getCurrentCertificate(provider.getId());
            if (cert != null && cert.isActive()) {
                hasCert = true;
                certExpiry = cert.getExpiryDate();
            } else {
                reasons.add("NO_ACTIVE_CERTIFICATE");
            }
        }

        boolean noCompliance = true;
        if (request.checkComplianceStatus()) {
            var overdue = complianceService.getOverdueActions(provider.getId());
            if (!overdue.isEmpty()) {
                noCompliance = false;
                reasons.add("OVERDUE_COMPLIANCE");
            }
        }

        boolean hasAffil = true;
        if (request.checkAffiliationStatus() && request.facilityId() != null) {
            hasAffil = affiliationService.hasActiveAffiliation(provider.getId(), request.facilityId());
            if (!hasAffil) {
                reasons.add("NO_ACTIVE_AFFILIATION");
            }
        }

        boolean canServePic = false;
        if (request.checkPicEligibility() && request.facilityId() != null) {
            canServePic = picService.isProviderEligibleForPic(provider.getId());
            if (!canServePic) {
                reasons.add("NOT_PIC_ELIGIBLE");
            }
        }

        return new ProviderEligibilityResponse(
                provider.getId(),
                provider.getProviderPublicId(),
                reasons.isEmpty(),
                reasons.isEmpty() ? "ELIGIBLE" : "INELIGIBLE",
                hasValidLicense,
                hasCert,
                noCompliance,
                hasAffil,
                canServePic,
                licenseExpiry,
                certExpiry,
                reasons.toArray(new String[0])
        );
    }

    @Transactional(readOnly = true)
    public ProviderEligibilityResponse checkEligibilityForFacility(Long providerId, Long facilityId) {
        return checkEligibility(ProviderEligibilityRequest.fullCheck(providerId, facilityId));
    }

    @Transactional(readOnly = true)
    public ProviderEligibilityResponse getEligibilitySummary(Long providerId) {
        return checkEligibility(ProviderEligibilityRequest.fullCheck(providerId, null));
    }

    @Transactional(readOnly = true)
    public ProviderEligibilityResponse checkPicEligibility(Long providerId, Long facilityId) {
        var request = ProviderEligibilityRequest.fullCheck(providerId, facilityId);
        return checkEligibility(new ProviderEligibilityRequest(
                request.providerId(),
                request.providerPublicId(),
                request.facilityId(),
                request.contextType(),
                true, false, false, false, false
        ));
    }

    @Transactional(readOnly = true)
    public List<ProviderEligibilityResponse> getEligibleProvidersForFacility(Long facilityId) {
        var affiliations = affiliationService.getActiveAffiliationsByFacility(facilityId);
        return affiliations.stream()
                .map(a -> checkEligibilityForFacility(a.getProvider().getId(), facilityId))
                .filter(r -> r.eligible())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean facilityHasActiveProvider(Long facilityId, Long excludeProviderId) {
        var affiliations = affiliationService.getActiveAffiliationsByFacility(facilityId);
        return affiliations.stream()
                .filter(a -> excludeProviderId == null || !a.getProvider().getId().equals(excludeProviderId))
                .anyMatch(a -> a.getProvider().isActive());
    }
}
