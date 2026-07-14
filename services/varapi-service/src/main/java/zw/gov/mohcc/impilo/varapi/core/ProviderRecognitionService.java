package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecognitionResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers "is this Health ID a recognised, licensed provider in good standing?"
 * for downstream advantage surfaces (badge, COSTA fee exemption, coverage
 * subsidy opt-in, marketplace professional self-authorization).
 *
 * <p>Recognition truth is the CANONICAL lifecycle axis only (Wave-2
 * consolidation): a provider is recognised iff {@link ProviderEntity#isActive()}
 * — i.e. lifecycle in REGISTERED / CLAIMED / LICENCED_ACTIVE /
 * LICENCE_DUE_FOR_RENEWAL / RENEWAL_IN_PROGRESS. SUSPENDED / RESTRICTED /
 * REMOVED / LAPSED etc. are NOT recognised.</p>
 *
 * <p>Doctrine: recognition drives administrative/financial advantages only —
 * never clinical-triage priority — and follows the {@code EligibilityController}
 * generic-response doctrine (identical shape and code path for unknown IDs).</p>
 */
@Service
public class ProviderRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(ProviderRecognitionService.class);

    private final ProviderRepository providerRepository;

    public ProviderRecognitionService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public ProviderRecognitionResponse recognitionByHealthId(String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID hid = parseUuid(healthId);
        if (hid == null) {
            // Malformed IDs get the same generic body; no exception, no 4xx —
            // same contract as an unknown-but-well-formed ID.
            return ProviderRecognitionResponse.notRecognised();
        }
        Optional<ProviderEntity> providerOpt =
                providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), hid);
        if (providerOpt.isEmpty()) {
            return ProviderRecognitionResponse.notRecognised();
        }
        ProviderEntity provider = providerOpt.get();
        if (!provider.isActive()) {
            // Suspended / restricted / lapsed providers are indistinguishable
            // from unknown IDs — enumeration resistance.
            return ProviderRecognitionResponse.notRecognised();
        }
        log.debug("Recognition resolved for tenant={} (recognised provider)", ctx.tenantId());
        return new ProviderRecognitionResponse(
                true,
                provider.getLicenceStatus(),
                provider.getProfession(),
                provider.getCadre());
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
