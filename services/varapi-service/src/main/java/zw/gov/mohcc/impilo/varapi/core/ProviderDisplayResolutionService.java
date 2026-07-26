package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batch display-facts read-model for the experience composition layer.
 *
 * <p>The on-call rota and the swap picker render people vashandi only knows by reference —
 * {@code vsh_workforce_profile} deliberately holds no name and no telephone number. Varapi is the
 * registry that does hold those facts for providers, so the composition layer resolves them here
 * in one call rather than copying names into the rostering service.</p>
 *
 * <p><b>Disclosure-limited by construction.</b> This returns only what a rota needs to render a
 * person and reach them: name parts, profession/cadre, and the registered phone. It is a display
 * read-model, not a privilege check — a suspended provider still has a name, so registry status
 * does not filter the result. Nothing here grants authority.</p>
 *
 * <p><b>A miss is a miss.</b> Every requested id comes back with {@code resolved} true or false;
 * unknown and malformed ids yield the identical unresolved row (no detail, no 404), so the caller
 * renders its own honest fallback — the worker reference — rather than an invented name.</p>
 */
@Service
public class ProviderDisplayResolutionService {

    /** One rota week tops out far below this; the cap keeps the IN-clause bounded. */
    public static final int MAX_BATCH = 200;

    private final ProviderRepository providerRepository;

    public ProviderDisplayResolutionService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolveDisplayFacts(List<String> healthIds) {
        TrustContext ctx = TrustContextHolder.require();
        if (healthIds == null || healthIds.isEmpty()) {
            return List.of();
        }
        if (healthIds.size() > MAX_BATCH) {
            throw new IllegalArgumentException(
                    "at most " + MAX_BATCH + " health ids per resolution call");
        }

        LinkedHashMap<String, UUID> requested = new LinkedHashMap<>();
        for (String raw : new LinkedHashSet<>(healthIds)) {
            requested.put(raw, parseUuid(raw));
        }

        List<UUID> parseable = requested.values().stream().filter(java.util.Objects::nonNull).toList();
        Map<UUID, ProviderEntity> byHealthId = new LinkedHashMap<>();
        if (!parseable.isEmpty()) {
            for (ProviderEntity p : providerRepository.findByTenantIdAndImpiloHealthIdIn(ctx.tenantId(), parseable)) {
                byHealthId.putIfAbsent(p.getImpiloHealthId(), p);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>(requested.size());
        for (Map.Entry<String, UUID> e : requested.entrySet()) {
            ProviderEntity p = e.getValue() == null ? null : byHealthId.get(e.getValue());
            rows.add(p == null ? unresolvedRow(e.getKey()) : resolvedRow(e.getKey(), p));
        }
        return rows;
    }

    private static Map<String, Object> resolvedRow(String healthId, ProviderEntity p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("healthId", healthId);
        row.put("resolved", true);
        row.put("providerPublicId", p.getProviderPublicId());
        row.put("title", blankToNull(p.getTitle()));
        row.put("givenName", blankToNull(p.getGivenName()));
        row.put("familyName", blankToNull(p.getFamilyName()));
        row.put("displayName", displayName(p));
        row.put("profession", blankToNull(p.getProfession()));
        row.put("cadre", blankToNull(p.getCadre()));
        row.put("phone", blankToNull(p.getPhone()));
        return row;
    }

    /** Unknown and malformed ids share this one shape — nothing to learn from the difference. */
    private static Map<String, Object> unresolvedRow(String healthId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("healthId", healthId);
        row.put("resolved", false);
        row.put("providerPublicId", null);
        row.put("title", null);
        row.put("givenName", null);
        row.put("familyName", null);
        row.put("displayName", null);
        row.put("profession", null);
        row.put("cadre", null);
        row.put("phone", null);
        return row;
    }

    /**
     * A registry row with both name parts blank resolves to no display name at all — the caller
     * falls back to its reference. Composing a name out of whitespace would defeat the point.
     */
    private static String displayName(ProviderEntity p) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{p.getTitle(), p.getGivenName(), p.getFamilyName()}) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(part.trim());
            }
        }
        String given = p.getGivenName();
        String family = p.getFamilyName();
        boolean hasName = (given != null && !given.isBlank()) || (family != null && !family.isBlank());
        return hasName ? sb.toString() : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
