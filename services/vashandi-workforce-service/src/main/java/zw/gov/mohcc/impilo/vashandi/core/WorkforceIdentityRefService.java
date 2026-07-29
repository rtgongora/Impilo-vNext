package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profile → person-anchor mapping, in batch.
 *
 * <p>Vashandi knows which profile is rostered; vito and varapi know who that is. This hands the
 * composition layer the join key (Health ID) and the operational worker reference, and stops
 * there — adding a name here would make vashandi a second identity store, which is precisely what
 * {@link StaffingReadService} refuses to be.</p>
 */
@Service
public class WorkforceIdentityRefService {

    /** A rota week's worth of distinct people is far below this; the cap bounds the IN-clause. */
    public static final int MAX_BATCH = 200;

    private final WorkforceProfileRepository profileRepository;

    public WorkforceIdentityRefService(WorkforceProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> identityRefs(UUID tenantId, String idsCsv) {
        if (idsCsv == null || idsCsv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String raw : idsCsv.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // A malformed id resolves to nothing, exactly like an unknown one.
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH) {
            throw new IllegalArgumentException("at most " + MAX_BATCH + " profile ids per lookup");
        }

        Map<UUID, WorkforceProfileEntity> found = new LinkedHashMap<>();
        for (WorkforceProfileEntity p : profileRepository.findByTenantIdAndIdIn(tenantId, ids)) {
            found.putIfAbsent(p.getId(), p);
        }

        List<Map<String, Object>> rows = new ArrayList<>(found.size());
        for (UUID id : ids) {
            WorkforceProfileEntity p = found.get(id);
            if (p == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workforce_profile_id", id.toString());
            row.put("health_id", p.getHealthId());
            row.put("provider_worker_id", p.getProviderWorkerId());
            rows.add(row);
        }
        return rows;
    }
}
