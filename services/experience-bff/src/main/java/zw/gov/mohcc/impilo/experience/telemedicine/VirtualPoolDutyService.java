package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Virtual-pool duty composition (BFF): asks vashandi who is on duty for a pool
 * (the TUSO routing seam target_ref). Vashandi unreachable → duty UNKNOWN —
 * duty is advisory and must NEVER block a care action.
 */
@Service
public class VirtualPoolDutyService {

    public static final String ON_DUTY_TRUE = "true";
    public static final String ON_DUTY_FALSE = "false";
    public static final String ON_DUTY_UNKNOWN = "UNKNOWN";

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolDutyService.class);

    private final VashandiServiceClient vashandiServiceClient;

    public VirtualPoolDutyService(VashandiServiceClient vashandiServiceClient) {
        this.vashandiServiceClient = vashandiServiceClient;
    }

    /**
     * Duty snapshot for one pool:
     * {@code {status: true|false|UNKNOWN, onDutyCount, onDuty[], nextShiftStart}}.
     * UNKNOWN (with count/list absent) when vashandi is unreachable.
     */
    public Map<String, Object> dutySnapshot(String poolId) {
        Map<String, Object> duty = new LinkedHashMap<>();
        duty.put("poolId", poolId);
        try {
            JsonNode snapshot = vashandiServiceClient.getVirtualPoolOnDuty(poolId);
            if (snapshot == null || !snapshot.has("onDutyCount")) {
                duty.put("status", ON_DUTY_UNKNOWN);
                return duty;
            }
            int count = snapshot.path("onDutyCount").asInt(0);
            duty.put("status", count > 0 ? ON_DUTY_TRUE : ON_DUTY_FALSE);
            duty.put("onDutyCount", count);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode row : iterable(snapshot.get("onDuty"))) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("workforceProfileId", text(row, "workforceProfileId"));
                mapped.put("shiftType", text(row, "shiftType"));
                mapped.put("status", text(row, "status"));
                mapped.put("startTime", text(row, "startTime"));
                mapped.put("endTime", text(row, "endTime"));
                rows.add(mapped);
            }
            duty.put("onDuty", rows);
            duty.put("nextShiftStart", text(snapshot, "nextShiftStart"));
            return duty;
        } catch (Exception e) {
            log.warn("Vashandi duty lookup failed for pool {} — duty UNKNOWN: {}", poolId, e.getMessage());
            duty.put("status", ON_DUTY_UNKNOWN);
            return duty;
        }
    }

    /** Compact flag for audit metadata: "true" | "false" | "UNKNOWN". */
    public String onDutyFlag(String poolId) {
        Object status = dutySnapshot(poolId).get("status");
        return status == null ? ON_DUTY_UNKNOWN : status.toString();
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
