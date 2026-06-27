package zw.gov.mohcc.impilo.ia.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static assurance policy: which permissions are granted at each level, and the upgrade
 * pathways available from a level. identity-assurance-service is the canonical owner of
 * identity-assurance policy (other services carry {@code must-not-own-identity-assurance-policy}).
 */
public final class AssurancePolicy {

    private AssurancePolicy() {
    }

    /** Full universe of assurance-gated permissions, ordered by the level that first grants them. */
    private static final Map<AssuranceLevel, List<String>> GRANTED_AT_LEVEL = Map.of(
            AssuranceLevel.LOA1, List.of(
                    "VIEW_OWN_RECORDS", "WELLNESS_TRACKING", "SEARCH",
                    "EDUCATION_ACCESS", "MARKETPLACE_BROWSE", "COMMUNITY_PARTICIPATION"),
            AssuranceLevel.LOA2, List.of("APPOINTMENT_BOOKING", "MESSAGING"),
            AssuranceLevel.LOA3, List.of("PRESCRIBING", "CLAIMS_SUBMISSION"),
            AssuranceLevel.LOA4, List.of("IDENTITY_VERIFICATION_OTHERS", "PROVIDER_ACTIVATION", "ADMIN_OPERATIONS")
    );

    /** Permissions cumulatively granted up to and including {@code level}. */
    public static List<String> granted(AssuranceLevel level) {
        List<String> out = new ArrayList<>();
        for (AssuranceLevel l : AssuranceLevel.values()) {
            if (l.rank() <= level.rank()) {
                out.addAll(GRANTED_AT_LEVEL.getOrDefault(l, List.of()));
            }
        }
        return out;
    }

    /** Permissions not yet granted at {@code level} (i.e. requiring a higher level). */
    public static List<String> restricted(AssuranceLevel level) {
        List<String> out = new ArrayList<>();
        for (AssuranceLevel l : AssuranceLevel.values()) {
            if (l.rank() > level.rank()) {
                out.addAll(GRANTED_AT_LEVEL.getOrDefault(l, List.of()));
            }
        }
        return out;
    }

    /** Upgrade pathways available from {@code level} (empty at the top level). */
    public static List<Map<String, Object>> pathways(AssuranceLevel level) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (level.rank() < AssuranceLevel.LOA3.rank()) {
            out.add(pathway("LOA3", "IN_PERSON_VERIFICATION",
                    "Visit a registered facility for in-person identity verification"));
            out.add(pathway("LOA3", "SUPERVISED_REMOTE_PROOFING",
                    "Video call with an identity verification officer"));
        }
        if (level.rank() < AssuranceLevel.LOA4.rank()) {
            out.add(pathway("LOA4", "BIOMETRIC_BINDING",
                    "In-person biometric enrolment with physical token issuance"));
        }
        return out;
    }

    private static Map<String, Object> pathway(String targetLevel, String method, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("targetLevel", targetLevel);
        p.put("method", method);
        p.put("description", description);
        return p;
    }
}
