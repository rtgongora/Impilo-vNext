package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Council / EC-number resolver (L3 provider lane).
 *
 * <p>Resolves a council registration number (e.g. an MDPCZ/NMCZ "EC number") to
 * its provider's person anchor for the silent identifier-resolution chain. The
 * registration number <b>resolves a profile but never authenticates</b>
 * (LOGIN-PROVIDERID-DENY) — the result is explicitly non-authenticating.</p>
 *
 * <p>Anti-enumeration is the caller's responsibility (tshepo-identity applies the
 * uniform shape + constant-time floor); this resolver just returns an empty
 * Optional on any miss without leaking a reason.</p>
 */
@Service
public class CouncilRegistrationResolverService {

    private static final Logger log = LoggerFactory.getLogger(CouncilRegistrationResolverService.class);

    private final ProviderCouncilRegistrationRecordRepository registrationRepo;

    public CouncilRegistrationResolverService(ProviderCouncilRegistrationRecordRepository registrationRepo) {
        this.registrationRepo = registrationRepo;
    }

    /**
     * Resolve a registration number to its provider anchor.
     *
     * @param tenantId           tenant scope
     * @param councilCode        optional council code (e.g. MDPCZ) to disambiguate; may be null/blank
     * @param registrationNumber the council/EC registration number
     * @return a non-authenticating provider reference, or empty on any miss
     */
    @Transactional(readOnly = true)
    public Optional<CouncilProviderRef> resolve(UUID tenantId, String councilCode, String registrationNumber) {
        if (tenantId == null || registrationNumber == null || registrationNumber.isBlank()) {
            return Optional.empty();
        }
        String number = registrationNumber.trim();
        Optional<ProviderCouncilRegistrationRecordEntity> record =
                (councilCode != null && !councilCode.isBlank())
                        ? registrationRepo
                            .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                                tenantId, councilCode.trim(), number)
                        : registrationRepo.findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, number);

        return record.map(this::toRef);
    }

    private CouncilProviderRef toRef(ProviderCouncilRegistrationRecordEntity record) {
        ProviderEntity provider = record.getProvider();
        UUID healthId = provider != null ? provider.getImpiloHealthId() : null;
        String providerPublicId = provider != null ? provider.getProviderPublicId() : null;
        String councilCode = record.getCouncil() != null ? record.getCouncil().getCouncilCode() : null;
        log.debug("council registration resolved to provider anchor (non-authenticating)");
        return new CouncilProviderRef(
                healthId != null ? healthId.toString() : null,
                providerPublicId,
                councilCode,
                record.getRegistrationNumber(),
                record.getStatus(),
                // The council/EC number resolves a profile but can never authenticate.
                false);
    }

    /**
     * Non-authenticating provider reference resolved from a council registration number.
     */
    public record CouncilProviderRef(
            String impiloHealthId,
            String providerPublicId,
            String councilCode,
            String registrationNumber,
            String registrationStatus,
            boolean canAuthenticate
    ) {
    }
}
