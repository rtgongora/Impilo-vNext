package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEcLookupRepository;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EC-number employment matching for Channel B (public workforce) onboarding
 * (IATG Wave-1, WS-D).
 *
 * <p>Governance (wgv_hsc_employment) is the system of record for public-sector
 * employment; this service answers "does this EC number correspond to a single
 * employment record, and is it linked to the supplied Health ID?" so the BFF
 * can compose an EMPLOYMENT_MATCHED trust assertion against the provider
 * registry. It never mutates trust — evidence composition happens upstream.</p>
 *
 * <p>Match statuses:</p>
 * <ul>
 *   <li>{@code MATCHED_BOTH} — exactly one row for the EC number and its
 *       {@code linkedHealthId} equals the supplied Health ID;</li>
 *   <li>{@code MATCHED_EMPLOYMENT_ONLY} — exactly one row, but no Health ID
 *       linkage could be confirmed (row unlinked, or no Health ID supplied);</li>
 *   <li>{@code CONFLICT} — multiple rows carry the same EC number (honest
 *       duplicate signal; never auto-resolved), or the single row is linked to
 *       a different Health ID than the one supplied;</li>
 *   <li>{@code NOT_FOUND} — no row carries the EC number (also returned for a
 *       malformed EC number: an invalid key can never match).</li>
 * </ul>
 */
@Service
public class EmploymentMatchService {

    public static final String MATCHED_BOTH = "MATCHED_BOTH";
    public static final String MATCHED_EMPLOYMENT_ONLY = "MATCHED_EMPLOYMENT_ONLY";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";

    private final HscEmploymentEcLookupRepository ecLookupRepository;

    public EmploymentMatchService(HscEmploymentEcLookupRepository ecLookupRepository) {
        this.ecLookupRepository = ecLookupRepository;
    }

    /**
     * Tenant-scoped EC-number match. {@code nationalId} and {@code surname} are
     * accepted for forward compatibility and audit context but are not matching
     * discriminators in Wave 1 — wgv_hsc_employment carries neither field, and
     * fabricating agreement on absent data would be dishonest.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> match(UUID tenantId, String ecNumber, String healthId,
                                     String nationalId, String surname) {
        String canonicalEc = EcNumbers.canonicalise(ecNumber);
        if (canonicalEc == null || !EcNumbers.isValid(canonicalEc)) {
            return result(NOT_FOUND, null);
        }

        List<HscEmploymentEntity> rows =
                ecLookupRepository.findByTenantIdAndEcNumberOrderByUpdatedAtDesc(tenantId, canonicalEc);
        if (rows.isEmpty()) {
            return result(NOT_FOUND, null);
        }
        if (rows.size() > 1) {
            // Duplicate EC numbers are an honest CONFLICT signal — never collapse them.
            Map<String, Object> conflict = result(CONFLICT, null);
            conflict.put("conflictingRecordCount", rows.size());
            return conflict;
        }

        HscEmploymentEntity row = rows.get(0);
        String suppliedHealthId = blankToNull(healthId);
        String linkedHealthId = blankToNull(row.getLinkedHealthId());

        String status;
        if (suppliedHealthId != null && linkedHealthId != null) {
            // A row already linked to a *different* person must never yield employment
            // evidence for the supplied Health ID — that is a conflict, not a partial match.
            status = suppliedHealthId.equals(linkedHealthId) ? MATCHED_BOTH : CONFLICT;
        } else {
            status = MATCHED_EMPLOYMENT_ONLY;
        }

        if (CONFLICT.equals(status)) {
            return result(CONFLICT, null);
        }
        return result(status, row);
    }

    private static Map<String, Object> result(String matchStatus, HscEmploymentEntity row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchStatus", matchStatus);
        if (row != null) {
            out.put("employmentId", row.getId());
            if (row.getLinkedHealthId() != null) {
                out.put("linkedHealthId", row.getLinkedHealthId());
            }
            out.put("postings", List.of(posting(row)));
            putIfPresent(out, "cadre", row.getCadre());
            putIfPresent(out, "grade", row.getGrade());
            putIfPresent(out, "disciplinaryEmploymentStatus", row.getDisciplinaryEmploymentStatus());
            putIfPresent(out, "verificationStatus", row.getVerificationStatus());
            putIfPresent(out, "employmentStatus", row.getEmploymentStatus());
        }
        return out;
    }

    private static Map<String, Object> posting(HscEmploymentEntity row) {
        Map<String, Object> posting = new LinkedHashMap<>();
        putIfPresent(posting, "postId", row.getPostId());
        putIfPresent(posting, "postTitle", row.getPostTitle());
        putIfPresent(posting, "province", row.getCurrentPostingProvince());
        putIfPresent(posting, "district", row.getCurrentPostingDistrict());
        putIfPresent(posting, "facility", row.getCurrentPostingFacility());
        putIfPresent(posting, "department", row.getCurrentPostingDepartment());
        return posting;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
