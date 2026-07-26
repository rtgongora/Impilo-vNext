package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Derives a capability for an HPA-listed facility ONLY where the licence class <em>is</em> the
 * service (HAR W5).
 *
 * <p><b>The line this service does not cross.</b> 5,509 facilities carry zero capabilities, so
 * every service-filtered find-care search misses them. The tempting fix — infer services from the
 * institution type — is how a citizen ends up travelling to a "clinic" for a delivery it has never
 * performed. So the rule is narrow and it is about evidence, not coverage: HPA registers a pharmacy
 * <em>as a pharmacy</em>. Dispensing medicines is not an inference about that facility; it is the
 * licence. The same holds for a laboratory, a radiology centre, a dental surgery, optical rooms and
 * an ambulance service.</p>
 *
 * <p><b>What is never derived, no matter what the register says:</b> OPD, IPD, MATERNITY and
 * EMERGENCY. HPA's own subtypes include "Maternity Homes" (40) and "Emergency Rooms" (69), and this
 * service still refuses them — {@link #FORBIDDEN_TOKENS} is asserted against every mapping. A
 * register entry saying a place is a maternity home is not confirmation that it is staffed,
 * equipped and open tonight, and those are the four services where being wrong is measured in
 * lives rather than in a wasted trip.</p>
 *
 * <p>Everything written is marked {@code ziboValidated=false} and
 * {@code metadata={derived:true, source:HPA_INSTITUTION_TYPE, confirmed:false}} so the surface can
 * label it as unconfirmed, and so a later ZIBO validation or facility claim overwrites rather than
 * competes.</p>
 */
@Service
public class HpaCapabilityDerivationService {

    private static final Logger log = LoggerFactory.getLogger(HpaCapabilityDerivationService.class);

    /** Tokens this service may never emit, whatever the register calls the place. */
    static final Set<String> FORBIDDEN_TOKENS = Set.of("OPD", "IPD", "MATERNITY", "EMERGENCY");

    static final String DERIVATION_SOURCE = "HPA_INSTITUTION_TYPE";

    /**
     * Subtype → capability. Checked first, because the subtype is the more specific licence and
     * sometimes contradicts its parent type (a "Research Laboratory" is a Laboratory that no
     * citizen may walk into for a test).
     */
    static final Map<String, String[]> SUBTYPE_CAPABILITIES = Map.ofEntries(
            Map.entry("retail pharmacy", new String[]{"PHARMACY", "Pharmacy"}),
            Map.entry("dental surgeries ( general practice)", new String[]{"DENTAL", "Dental services"}),
            Map.entry("dental surgeries - specialist", new String[]{"DENTAL", "Dental services"}),
            Map.entry("optical rooms", new String[]{"OPTOMETRY", "Optometry"}),
            Map.entry("radiology centres", new String[]{"RADIOLOGY", "Radiology and imaging"}),
            Map.entry("x-ray rooms", new String[]{"RADIOLOGY", "Radiology and imaging"}),
            Map.entry("ultra sound scanning rooms", new String[]{"RADIOLOGY", "Radiology and imaging"}),
            Map.entry("multidisciplinary laboratory", new String[]{"LABORATORY", "Laboratory services"}),
            Map.entry("single discipline medical laboratory", new String[]{"LABORATORY", "Laboratory services"}),
            Map.entry("medical laboratory collection point", new String[]{"LABORATORY", "Laboratory sample collection"}),
            Map.entry("ambulance services", new String[]{"AMBULANCE", "Ambulance services"}));

    /** Type → capability, used only when the subtype says nothing. */
    static final Map<String, String[]> TYPE_CAPABILITIES = Map.of(
            "pharmacy", new String[]{"PHARMACY", "Pharmacy"},
            "laboratory", new String[]{"LABORATORY", "Laboratory services"},
            "ambulance", new String[]{"AMBULANCE", "Ambulance services"});

    /**
     * Subtypes that must derive NOTHING even though their parent type is derivable.
     * <ul>
     *   <li>A research laboratory does not accept a citizen's specimen.</li>
     *   <li>A pharmaceutical wholesaler does not dispense to the public — sending someone there for
     *       their medicine is sending them to a warehouse.</li>
     * </ul>
     */
    static final Set<String> SUBTYPE_DENYLIST = Set.of(
            "research laboratory", "pharmaceutical wholesalers");

    /**
     * Types that must derive NOTHING. "Pharmaceutical" is manufacture and wholesale; it is not a
     * place to collect a prescription.
     */
    static final Set<String> TYPE_DENYLIST = Set.of("pharmaceutical");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public HpaCapabilityDerivationService(JdbcTemplate jdbc, TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
    }

    public record DerivationSummary(int facilitiesConsidered, int capabilitiesWritten,
                                    int noDerivableClass, int alreadyPresent, boolean dryRun) {}

    /**
     * Resolve the capability a registered institution's own licence class implies.
     *
     * @return {@code [code, displayName]}, or empty when the class implies no citizen-facing service.
     */
    static Optional<String[]> deriveCapability(String institutionType, String institutionSubtype) {
        String subtype = normalise(institutionSubtype);
        String type = normalise(institutionType);

        if (subtype != null && SUBTYPE_DENYLIST.contains(subtype)) {
            return Optional.empty();
        }
        if (subtype != null && SUBTYPE_CAPABILITIES.containsKey(subtype)) {
            return Optional.of(SUBTYPE_CAPABILITIES.get(subtype));
        }
        if (type != null && TYPE_DENYLIST.contains(type)) {
            return Optional.empty();
        }
        if (type != null && TYPE_CAPABILITIES.containsKey(type)) {
            return Optional.of(TYPE_CAPABILITIES.get(type));
        }
        // Consulting rooms, clinics, hospitals, nursing homes, maternity homes, emergency rooms:
        // the register names a building, not a service that is running. Nothing is derived.
        return Optional.empty();
    }

    public DerivationSummary deriveForImportedFacilities(UUID tenantId, boolean dryRun, int limit) {
        List<Map<String, Object>> candidates = jdbc.queryForList(
                """
                SELECT f.id AS facility_id, f.tenant_id,
                       t.asserted_value AS institution_type,
                       s.asserted_value AS institution_subtype
                  FROM tuso.facility f
                  LEFT JOIN tuso.facility_field_assertion t
                         ON t.facility_id = f.id AND t.field_path = 'regulatory.type' AND t.is_current
                  LEFT JOIN tuso.facility_field_assertion s
                         ON s.facility_id = f.id AND s.field_path = 'regulatory.subtype' AND s.is_current
                 WHERE f.regulatory_status = 'IMPORTED_PENDING_CONFIGURATION'
                   AND (CAST(? AS uuid) IS NULL OR f.tenant_id = CAST(? AS uuid))
                 ORDER BY f.id
                 LIMIT ?
                """,
                tenantId, tenantId, Math.max(1, limit));

        int written = 0, noClass = 0, present = 0;
        for (Map<String, Object> row : candidates) {
            Optional<String[]> derived = deriveCapability(
                    (String) row.get("institution_type"), (String) row.get("institution_subtype"));
            if (derived.isEmpty()) {
                noClass++;
                continue;
            }
            String[] capability = derived.get();
            assertNotForbidden(capability[0]);

            Long facilityId = ((Number) row.get("facility_id")).longValue();
            Integer existing = jdbc.queryForObject(
                    "SELECT count(*) FROM tuso.facility_capability WHERE facility_id = ? AND capability_code = ?",
                    Integer.class, facilityId, capability[0]);
            if (existing != null && existing > 0) {
                present++;
                continue;
            }
            if (!dryRun) {
                UUID rowTenant = (UUID) row.get("tenant_id");
                transactionTemplate.executeWithoutResult(status -> jdbc.update(
                        "INSERT INTO tuso.facility_capability (facility_id, tenant_id, capability_code, "
                                + "capability_type, name, zibo_validated, active, metadata) "
                                + "VALUES (?, ?, ?, 'SERVICE', ?, false, true, CAST(? AS jsonb))",
                        facilityId, rowTenant, capability[0], capability[1], derivedMetadataJson()));
            }
            written++;
        }
        log.info("HPA capability derivation: considered={} written={} noDerivableClass={} alreadyPresent={} dryRun={}",
                candidates.size(), written, noClass, present, dryRun);
        return new DerivationSummary(candidates.size(), written, noClass, present, dryRun);
    }

    /**
     * Defence in depth. The maps above are checked by test, but this runs on every write so that a
     * future edit cannot quietly ship a forbidden token to production.
     */
    static void assertNotForbidden(String capabilityCode) {
        if (capabilityCode != null && FORBIDDEN_TOKENS.contains(capabilityCode.toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Refusing to derive " + capabilityCode + " from a regulatory listing — "
                            + "OPD, IPD, MATERNITY and EMERGENCY require confirmation, never inference");
        }
    }

    /** {@code confirmed:false} is the whole point — the surface must be able to say "unconfirmed". */
    static String derivedMetadataJson() {
        return "{\"derived\":true,\"source\":\"" + DERIVATION_SOURCE + "\",\"confirmed\":false}";
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
