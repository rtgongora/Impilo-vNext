package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes RUNTIME state onto a virtual-hospital payload:
 *
 * <ul>
 *   <li>per-queue computed status — {@code LIVE} only when PCT confirms the
 *       queue is materialised AND active for the pool; everything else stays
 *       {@code AWAITING_BACKEND} (never claimed from stored config);</li>
 *   <li>queue depth / oldest-wait / SLA-breach read-backs from PCT;</li>
 *   <li>pool duty (on-duty providers + next rostered start) from vashandi —
 *       unreachable → {@code duty.status=UNKNOWN}, never an error.</li>
 * </ul>
 */
@Service
public class VirtualHospitalCompositionService {

    private static final Logger log = LoggerFactory.getLogger(VirtualHospitalCompositionService.class);

    private final PctServiceClient pctServiceClient;
    private final VirtualPoolDutyService dutyService;

    public VirtualHospitalCompositionService(PctServiceClient pctServiceClient,
                                             VirtualPoolDutyService dutyService) {
        this.pctServiceClient = pctServiceClient;
        this.dutyService = dutyService;
    }

    /** Enrich one VH payload in place-copy (input map is not mutated). */
    public Map<String, Object> compose(Map<String, Object> vh) {
        Map<String, Object> out = new LinkedHashMap<>(vh);
        String poolId = primaryPoolId(out);
        if (poolId == null) {
            // No active routing seam — nothing routable, nothing to compose.
            return out;
        }

        Map<String, Map<String, Object>> statsByQueueKey = new LinkedHashMap<>();
        boolean statsDegraded = false;
        try {
            JsonNode stats = pctServiceClient.getVirtualPoolStats(poolId);
            if (stats != null && stats.isArray()) {
                for (JsonNode stat : stats) {
                    String queueKey = text(stat, "queueKey");
                    if (queueKey == null) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("active", stat.path("active").asBoolean(false));
                    row.put("materializationStatus", text(stat, "materializationStatus"));
                    row.put("depth", stat.path("depth").asLong(0));
                    row.put("oldestWaitingMinutes",
                            stat.hasNonNull("oldestWaitingMinutes") ? stat.get("oldestWaitingMinutes").asLong() : null);
                    row.put("slaBreaches", stat.path("slaBreaches").asLong(0));
                    row.put("slaMinutes", stat.hasNonNull("slaMinutes") ? stat.get("slaMinutes").asInt() : null);
                    statsByQueueKey.put(queueKey, row);
                }
            } else {
                statsDegraded = true;
            }
        } catch (Exception e) {
            log.warn("PCT virtual-pool stats unavailable for pool {} — queues stay AWAITING_BACKEND: {}",
                    poolId, e.getMessage());
            statsDegraded = true;
        }

        Object queuesRaw = out.get("queues");
        if (queuesRaw instanceof List<?> queues) {
            List<Map<String, Object>> composed = new ArrayList<>();
            for (Object raw : queues) {
                if (!(raw instanceof Map<?, ?> queue)) continue;
                Map<String, Object> q = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : queue.entrySet()) q.put(String.valueOf(e.getKey()), e.getValue());
                String queueKey = q.get("id") == null ? null : q.get("id").toString();
                Map<String, Object> stat = queueKey == null ? null : statsByQueueKey.get(queueKey);
                boolean live = stat != null
                        && Boolean.TRUE.equals(stat.get("active"))
                        && "MATERIALIZED".equals(stat.get("materializationStatus"));
                q.put("status", live ? "LIVE" : "AWAITING_BACKEND");
                if (stat != null) {
                    q.put("depth", stat.get("depth"));
                    q.put("oldestWaitingMinutes", stat.get("oldestWaitingMinutes"));
                    q.put("slaBreaches", stat.get("slaBreaches"));
                    q.put("slaMinutes", stat.get("slaMinutes"));
                }
                composed.add(q);
            }
            out.put("queues", composed);
        }
        out.put("queueStatsDegraded", statsDegraded);
        out.put("primaryPoolId", poolId);
        out.put("duty", dutyService.dutySnapshot(poolId));
        return out;
    }

    /** The pool key: first active routing seam target_ref. */
    @SuppressWarnings("unchecked")
    static String primaryPoolId(Map<String, Object> vh) {
        Object seams = vh.get("currentRoutingSeams");
        if (seams instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> seam) {
                    Object target = ((Map<String, Object>) seam).get("targetRef");
                    if (target != null && !target.toString().isBlank()) {
                        return target.toString();
                    }
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
