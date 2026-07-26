package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backfills council register records for the HPA-materialised practitioners (HAR W2).
 *
 * <p><b>The defect this repairs.</b> {@code HpaPractitionerMaterializationService} creates a
 * {@code ProviderEntity} and nothing else, so the practitioner's registration number lives only in
 * {@code provider.practice_number}. But {@code PublicPractitionerVerificationService.verify()}
 * resolves solely through {@code provider_council_registration_records} — a table the import never
 * wrote. Every public "verify a practitioner by registration number" lookup on all ~4,241 imported
 * practitioners therefore returns NOT_FOUND, which is exactly backwards: HPA asserted these
 * registrations, and the platform answers as if it had never heard of them.</p>
 *
 * <p><b>What is asserted, and what is not.</b> The record is written against the <b>HPA</b> council
 * — never a guessed professional council, because every seeded council carries the same placeholder
 * registration-number pattern and prefix inference would be undefensible. {@code register_id} stays
 * NULL: no statutory register is being claimed. The status is
 * {@link #STATUS_LISTED_PENDING_COUNCIL_VERIFICATION}, which is deliberately outside every existing
 * verified-registry vocabulary, so this backfill cannot widen any access gate. Registration and
 * expiry dates stay NULL — the HPA institution return does not carry them, and inventing them would
 * fabricate a currency claim.</p>
 *
 * <p>The provider's own axes ({@code PRELOADED} / {@code PENDING_VERIFICATION} /
 * {@code SELF_ASSERTED}) are left untouched: this makes the registration <em>findable</em>, not
 * <em>verified</em>.</p>
 */
@Service
public class HpaPractitionerRegisterBackfillService {

    private static final Logger log = LoggerFactory.getLogger(HpaPractitionerRegisterBackfillService.class);

    static final int CHUNK_SIZE = 200;

    /**
     * HPA listed this registration on an institution return; the practitioner's own professional
     * council has not verified it. Intentionally not a member of any VERIFIED_REGISTRY set.
     */
    public static final String STATUS_LISTED_PENDING_COUNCIL_VERIFICATION =
            "LISTED_PENDING_COUNCIL_VERIFICATION";

    static final String SOURCE_REFERENCE = "HPA_FACILITIES_2026_07_17:hpa_practitioner_candidate";
    static final String ISSUED_UNDER_AUTHORITY = "Health Professions Authority (institution return)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    public HpaPractitionerRegisterBackfillService(JdbcTemplate jdbc,
                                                  PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public record BackfillSummary(int candidates, int recordsCreated, int skippedNoCouncil,
                                  int skippedAlreadyPresent, boolean dryRun, int remaining) {}

    /**
     * Create one HPA register record per imported practitioner that lacks one.
     *
     * <p>Idempotent: the candidate query excludes providers that already carry a record for the HPA
     * council, and the insert repeats that guard inside the transaction.</p>
     */
    public BackfillSummary backfill(UUID tenantId, boolean dryRun, Integer limit) {
        Long hpaCouncilId = hpaCouncilId(tenantId);
        if (hpaCouncilId == null) {
            log.warn("HAR W2 register backfill: no HPA council row for tenant {} — nothing written", tenantId);
            return new BackfillSummary(0, 0, 0, 0, dryRun, remaining(tenantId));
        }

        int cap = limit == null ? Integer.MAX_VALUE : Math.max(limit, 1);
        List<Map<String, Object>> candidates = jdbc.queryForList(
                "SELECT p.id AS provider_id, p.practice_number AS regno "
                        + "FROM varapi.provider p "
                        + "WHERE p.tenant_id = ? "
                        + "  AND p.bootstrap_origin = 'COUNCIL_IMPORT' "
                        + "  AND p.practice_number IS NOT NULL AND p.practice_number <> '' "
                        + "  AND NOT EXISTS (SELECT 1 FROM varapi.provider_council_registration_records r "
                        + "                  WHERE r.provider_id = p.id AND r.council_id = ?) "
                        + "ORDER BY p.id LIMIT ?",
                tenantId, hpaCouncilId, cap == Integer.MAX_VALUE ? 100000 : cap);

        if (dryRun) {
            return new BackfillSummary(candidates.size(), candidates.size(), 0, 0, true, remaining(tenantId));
        }

        int created = 0;
        for (int i = 0; i < candidates.size(); i += CHUNK_SIZE) {
            List<Map<String, Object>> chunk =
                    candidates.subList(i, Math.min(i + CHUNK_SIZE, candidates.size()));
            Integer n = txTemplate.execute(status -> {
                int c = 0;
                for (Map<String, Object> row : chunk) {
                    Long providerId = ((Number) row.get("provider_id")).longValue();
                    String regno = String.valueOf(row.get("regno")).trim();
                    c += jdbc.update(
                            "INSERT INTO varapi.provider_council_registration_records "
                                    + "(tenant_id, provider_id, council_id, registration_number, status, "
                                    + " issued_under_authority, source_reference, created_at, updated_at) "
                                    + "SELECT ?, ?, ?, ?, ?, ?, ?, now(), now() "
                                    + "WHERE NOT EXISTS (SELECT 1 FROM varapi.provider_council_registration_records r "
                                    + "                  WHERE r.provider_id = ? AND r.council_id = ?)",
                            tenantId, providerId, hpaCouncilId, clip(regno, 120),
                            STATUS_LISTED_PENDING_COUNCIL_VERIFICATION,
                            ISSUED_UNDER_AUTHORITY, SOURCE_REFERENCE,
                            providerId, hpaCouncilId);
                }
                return c;
            });
            created += n == null ? 0 : n;
        }

        int left = remaining(tenantId);
        log.info("HAR W2 register backfill: candidates={} created={} remaining={}",
                candidates.size(), created, left);
        return new BackfillSummary(candidates.size(), created, 0, candidates.size() - created, false, left);
    }

    /** Imported practitioners with a registration number but still no HPA register record. */
    public int remaining(UUID tenantId) {
        Long hpaCouncilId = hpaCouncilId(tenantId);
        if (hpaCouncilId == null) {
            return 0;
        }
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM varapi.provider p "
                        + "WHERE p.tenant_id = ? AND p.bootstrap_origin = 'COUNCIL_IMPORT' "
                        + "  AND p.practice_number IS NOT NULL AND p.practice_number <> '' "
                        + "  AND NOT EXISTS (SELECT 1 FROM varapi.provider_council_registration_records r "
                        + "                  WHERE r.provider_id = p.id AND r.council_id = ?)",
                Integer.class, tenantId, hpaCouncilId);
        return n == null ? 0 : n;
    }

    /**
     * Imported practitioners whose HPA row carried no registration number. They get no register
     * record — ever — and become named work for the regulator instead of a silent drop.
     */
    public int unresolvableCount(UUID tenantId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM varapi.hpa_practitioner_candidate "
                        + "WHERE tenant_id = ? AND (practitioner_registration_number IS NULL "
                        + "   OR practitioner_registration_number = '')",
                Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    public Map<String, Object> summary(UUID tenantId) {
        Integer records = jdbc.queryForObject(
                "SELECT count(*) FROM varapi.provider_council_registration_records WHERE tenant_id = ?",
                Integer.class, tenantId);
        return Map.of(
                "registerRecords", records == null ? 0 : records,
                "remainingWithoutRecord", remaining(tenantId),
                "unresolvableNoRegistrationNumber", unresolvableCount(tenantId));
    }

    private Long hpaCouncilId(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM varapi.councils WHERE council_code = 'HPA' AND tenant_id = ? LIMIT 1",
                    Long.class, tenantId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clip(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
