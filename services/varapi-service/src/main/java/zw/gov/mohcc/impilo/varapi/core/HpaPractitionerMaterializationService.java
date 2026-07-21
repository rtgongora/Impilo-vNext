package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Materialise HPA practitioner-in-charge candidates into national provider
 * identities.
 *
 * <p>Each distinct HPA practitioner (keyed by council registration number) becomes
 * a {@code PRELOADED} {@link ProviderEntity} — a regulator-listed but <b>unclaimed</b>
 * record. PRELOADED derives to {@code status=INACTIVE, active=false}, and a provider
 * grants no operational access until the real person claims it (person-first login +
 * identity proofing binds their verified Health ID). So creating these records confers
 * NO login, facility-mode, work-context or authority — exactly parallel to a
 * regulator-listed (but not operational) facility.</p>
 *
 * <p>HPA is the regulator that maintains these registrations, so the records are
 * authoritative as to <i>professional existence</i>; the identity spine still governs
 * <i>account access</i>. The provenance is stamped {@code bootstrap_origin=COUNCIL_IMPORT};
 * trust/registry stay conservative (self-asserted / pending-verification) until the
 * person is independently verified at claim time.</p>
 *
 * <p>Idempotent: a candidate row already carrying {@code resolved_provider_id} is
 * skipped, so a re-run creates nothing new. Commits in chunks so the persistence
 * context never spans the whole ~4k-provider set.</p>
 */
@Service
public class HpaPractitionerMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(HpaPractitionerMaterializationService.class);

    static final String LIFECYCLE_PRELOADED = ProviderLifecycleStatus.PRELOADED.name();
    static final String BOOTSTRAP_ORIGIN = "COUNCIL_IMPORT";
    static final int CHUNK_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final ProviderRepository providerRepository;
    private final TransactionTemplate txTemplate;

    public HpaPractitionerMaterializationService(JdbcTemplate jdbc,
                                                 ProviderRepository providerRepository,
                                                 PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.providerRepository = providerRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public record MaterializeSummary(int distinctPractitioners, int providersCreated,
                                     int candidatesResolved, int skippedNoRegistration) {}

    public MaterializeSummary materializeProviders(UUID requestedTenant) {
        UUID tenantId = resolveTenant(requestedTenant);

        // Distinct, not-yet-resolved practitioners keyed by council registration number.
        List<Map<String, Object>> practitioners = jdbc.queryForList(
                "SELECT practitioner_registration_number AS regno, min(practitioner_name) AS nm "
                        + "FROM varapi.hpa_practitioner_candidate "
                        + "WHERE tenant_id = ? AND resolved_provider_id IS NULL "
                        + "  AND practitioner_registration_number IS NOT NULL "
                        + "  AND practitioner_registration_number <> '' "
                        + "GROUP BY practitioner_registration_number",
                tenantId);

        Integer skippedNoReg = jdbc.queryForObject(
                "SELECT count(*) FROM varapi.hpa_practitioner_candidate "
                        + "WHERE tenant_id = ? AND resolved_provider_id IS NULL "
                        + "  AND (practitioner_registration_number IS NULL OR practitioner_registration_number = '')",
                Integer.class, tenantId);

        UUID batchId = UUID.randomUUID();
        int created = 0, resolved = 0;
        for (int i = 0; i < practitioners.size(); i += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = practitioners.subList(i, Math.min(i + CHUNK_SIZE, practitioners.size()));
            int[] agg = txTemplate.execute(status -> {
                int c = 0, r = 0;
                for (Map<String, Object> row : chunk) {
                    String regno = String.valueOf(row.get("regno")).trim();
                    String name = row.get("nm") != null ? String.valueOf(row.get("nm")) : regno;
                    ProviderEntity provider = createPreloadedProvider(tenantId, batchId, name, regno);
                    c++;
                    // Link every candidate row for this registration to the new provider.
                    r += jdbc.update(
                            "UPDATE varapi.hpa_practitioner_candidate "
                                    + "SET resolved_provider_id = ?, varapi_resolution_status = 'RESOLVED', updated_at = now() "
                                    + "WHERE tenant_id = ? AND practitioner_registration_number = ? "
                                    + "  AND resolved_provider_id IS NULL",
                            provider.getId(), tenantId, regno);
                }
                return new int[]{c, r};
            });
            created += agg[0];
            resolved += agg[1];
        }

        log.info("HPA practitioner materialisation: distinct={} providersCreated={} candidatesResolved={} skippedNoReg={}",
                practitioners.size(), created, resolved, skippedNoReg != null ? skippedNoReg : 0);
        return new MaterializeSummary(practitioners.size(), created, resolved, skippedNoReg != null ? skippedNoReg : 0);
    }

    private ProviderEntity createPreloadedProvider(UUID tenantId, UUID batchId, String name, String registrationNumber) {
        ProviderEntity provider = new ProviderEntity();
        provider.setTenantId(tenantId);
        // Provisional person anchor, rebound to the claimant's verified Health ID at claim time.
        provider.setImpiloHealthId(UUID.randomUUID());
        provider.setProviderPublicId(generateProviderPublicId());
        String[] parts = splitName(name);
        provider.setGivenName(parts[0]);
        provider.setFamilyName(parts[1]);
        provider.setPracticeNumber(clip(registrationNumber, 50));
        // Canonical axis first; PRELOADED projects to status=INACTIVE, active=false — no access.
        provider.setLifecycleStatus(LIFECYCLE_PRELOADED);
        provider.deriveStatusProjections();
        provider.setBootstrapOrigin(BOOTSTRAP_ORIGIN);
        // Conservative honesty axes: the person is not independently verified until claim.
        provider.setTrustLevel(ProviderTrustLevel.SELF_ASSERTED.name());
        provider.setRegistryStatus(ProviderRegistryStatus.PENDING_VERIFICATION.name());
        provider.setPreloadBatchId(batchId);
        provider.setVersion(1);
        provider.setCreatedBy("HPA_IMPORT");
        provider.setUpdatedBy("HPA_IMPORT");
        return providerRepository.save(provider);
    }

    /** given = first token, family = last token (single-token names repeat into family). */
    private static String[] splitName(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"", ""};
        }
        String[] tokens = raw.trim().split("\\s+");
        String given = clip(tokens[0], 255);
        String family = clip(tokens[tokens.length - 1], 255);
        return new String[]{given, family};
    }

    private static String generateProviderPublicId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    private static String clip(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private UUID resolveTenant(UUID requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return TrustContextHolder.require().tenantId();
        } catch (Exception e) {
            throw new IllegalStateException("No tenant context for HPA practitioner materialisation");
        }
    }
}
