package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tuso.integration.MinistryAuthorityClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Who may act on a facility with Ministry authority, and over which facilities (FCV-W2).
 *
 * <p>Authority comes from an ACTIVE appointment at the Ministry organisation in org-registry,
 * resolved service-to-service. Its {@code jurisdiction_code} is then matched against the facility's
 * own province and district. The policy engine deliberately does not do this second half: it can
 * only compare a duty jurisdiction against a fixed list in a rule, so it cannot express "matches
 * <em>this</em> facility's district" — enumerating ten provinces and sixty-odd districts in every
 * rule would mean each new district silently denies until someone edits a policy seed. So the PDP
 * gates the lane coarsely and the facility-level match lives here, in one place.</p>
 *
 * <p><b>Jurisdiction grammar</b> (carried on the appointment, seeded in org-registry V012):
 * {@code NATIONAL}, {@code PROVINCE:<NAME>}, {@code DISTRICT:<NAME>}. A national jurisdiction
 * satisfies any facility; a province satisfies facilities in that province; a district satisfies
 * only that district. Narrower never satisfies wider — a district officer cannot decide for the
 * province.</p>
 *
 * <p>Comparison is case- and whitespace-insensitive because {@code facility.province} and
 * {@code facility.district} are free text with no catalogue behind them. That is a real weakness:
 * a facility whose district reads "Buhera District" will not match {@code DISTRICT:BUHERA} and the
 * verifier is refused. Refusing is the safe direction, and normalising these onto a geography
 * catalogue is tracked as its own piece of work — but it means this gate is honest about what it
 * knows rather than guessing.</p>
 */
@Service
public class MinistryAuthorityService {

    private static final Logger log = LoggerFactory.getLogger(MinistryAuthorityService.class);

    /** The Ministry organisation seeded by org-registry V012. */
    public static final String MOHCC_ORGANISATION_ID = "a6000000-0000-4000-8000-000000000001";

    /** Roles that may issue a Ministry legitimacy verdict. Registry administration is NOT one. */
    public static final Set<String> VERIFIER_ROLES = Set.of(
            "MOHCC_NATIONAL_VERIFIER", "MOHCC_PROVINCIAL_VERIFIER", "MOHCC_DISTRICT_VERIFIER");

    /** Roles that may administer the registry (review claims, run governed batches). */
    public static final Set<String> REGISTRY_ADMIN_ROLES = Set.of("MOHCC_REGISTRY_ADMINISTRATOR");

    private static final String NATIONAL = "NATIONAL";
    private static final String PROVINCE_PREFIX = "PROVINCE:";
    private static final String DISTRICT_PREFIX = "DISTRICT:";

    private final MinistryAuthorityClient client;

    public MinistryAuthorityService(MinistryAuthorityClient client) {
        this.client = client;
    }

    /** A resolved authority: the role the person holds and the jurisdiction it is bounded to. */
    public record Authority(String roleCode, String jurisdictionCode) {}

    /**
     * The person's Ministry authority over this facility, or empty when they have none.
     *
     * @param roles which role codes count — {@link #VERIFIER_ROLES} to decide legitimacy,
     *              {@link #REGISTRY_ADMIN_ROLES} to administer the registry
     */
    public java.util.Optional<Authority> authorityOver(String personHealthId, FacilityEntity facility,
                                                       Set<String> roles) {
        if (facility == null) {
            return java.util.Optional.empty();
        }
        for (Map<String, Object> appointment : client.appointmentsForPerson(personHealthId)) {
            String status = str(appointment.get("status"));
            String org = str(appointment.get("organizationId"));
            String role = str(appointment.get("roleCode"));
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                continue;
            }
            if (!MOHCC_ORGANISATION_ID.equalsIgnoreCase(org)) {
                continue; // authority at a professional council is not authority over facilities
            }
            if (role == null || !roles.contains(role.toUpperCase(Locale.ROOT))) {
                continue;
            }
            String jurisdiction = str(appointment.get("jurisdictionCode"));
            if (covers(jurisdiction, facility)) {
                return java.util.Optional.of(new Authority(role.toUpperCase(Locale.ROOT),
                        jurisdiction == null ? NATIONAL : jurisdiction));
            }
            log.debug("Ministry appointment {} does not cover facility {} ({}/{})",
                    role, facility.getFacilityUuid(), facility.getProvince(), facility.getDistrict());
        }
        return java.util.Optional.empty();
    }

    /** Does this jurisdiction code cover this facility? */
    static boolean covers(String jurisdictionCode, FacilityEntity facility) {
        String j = normalise(jurisdictionCode);
        if (j.isEmpty() || NATIONAL.equals(j)) {
            // An appointment with no jurisdiction recorded defaults to NATIONAL in org-registry.
            return true;
        }
        if (j.startsWith(PROVINCE_PREFIX)) {
            return normalise(facility.getProvince()).equals(j.substring(PROVINCE_PREFIX.length()).trim());
        }
        if (j.startsWith(DISTRICT_PREFIX)) {
            return normalise(facility.getDistrict()).equals(j.substring(DISTRICT_PREFIX.length()).trim());
        }
        // An unrecognised grammar is not a licence to act everywhere.
        return false;
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
