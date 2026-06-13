package zw.gov.mohcc.impilo.experience.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Honest degraded-state metadata for BFF registry and intake surfaces. */
public final class BffDegradedMeta {

    private BffDegradedMeta() {
    }

    public static Map<String, Object> base(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    public static Map<String, Object> degraded(
            String requestId,
            String correlationId,
            String upstream,
            String guidance) {
        Map<String, Object> meta = base(requestId, correlationId);
        meta.put("degraded", true);
        meta.put("upstream", upstream);
        meta.put("guidance", guidance);
        return meta;
    }

    public static Map<String, Object> degradedWithStatus(
            String requestId,
            String correlationId,
            String upstream,
            int status,
            String guidance) {
        Map<String, Object> meta = degraded(requestId, correlationId, upstream, guidance);
        meta.put("status", status);
        return meta;
    }

    public static Map<String, Object> withPaging(
            String requestId,
            String correlationId,
            int page,
            int size) {
        Map<String, Object> meta = base(requestId, correlationId);
        meta.put("page", page);
        meta.put("size", size);
        return meta;
    }

    public static Map<String, Object> degradedWithPaging(
            String requestId,
            String correlationId,
            String upstream,
            String guidance,
            int page,
            int size) {
        Map<String, Object> meta = degraded(requestId, correlationId, upstream, guidance);
        meta.put("page", page);
        meta.put("size", size);
        return meta;
    }
}
