package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.EmergencyOrderSetService;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetInstanceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP surface for order-set instances (pct V206, W7b). */
@RestController
@RequestMapping("/v1/emergency")
public class EmergencyOrderSetController {

    private final EmergencyOrderSetService orderSetService;

    public EmergencyOrderSetController(EmergencyOrderSetService orderSetService) {
        this.orderSetService = orderSetService;
    }

    @PostMapping("/episodes/{episodeId}/order-sets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invoke(@PathVariable UUID episodeId,
                                                                    @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var instance = orderSetService.invoke(episodeId, ctx.tenantId(), body);
        var items = orderSetService.items(instance.getInstanceId(), ctx.tenantId());
        Map<String, Object> out = instanceRow(instance);
        out.put("items", items.stream().map(this::itemRow).toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @GetMapping("/episodes/{episodeId}/order-sets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forEpisode(@PathVariable UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        var instances = orderSetService.forEpisode(episodeId, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(instances.stream().map(this::instanceRow).toList(),
                ctx.correlationId().toString()));
    }

    @GetMapping("/order-sets/{instanceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID instanceId) {
        TrustContext ctx = TrustContextHolder.require();
        var instance = orderSetService.getInstance(instanceId, ctx.tenantId());
        var items = orderSetService.items(instanceId, ctx.tenantId());
        Map<String, Object> out = instanceRow(instance);
        out.put("items", items.stream().map(this::itemRow).toList());
        return ResponseEntity.ok(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/order-sets/items/{itemId}/order")
    public ResponseEntity<ApiResponse<Map<String, Object>>> orderItem(@PathVariable UUID itemId,
                                                                       @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var item = orderSetService.orderItem(itemId, ctx.tenantId(), body);
        return ResponseEntity.ok(ApiResponse.ok(itemRow(item), ctx.correlationId().toString()));
    }

    @PostMapping("/order-sets/items/{itemId}/decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> declineItem(@PathVariable UUID itemId,
                                                                         @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var item = orderSetService.declineItem(itemId, ctx.tenantId(), body);
        return ResponseEntity.ok(ApiResponse.ok(itemRow(item), ctx.correlationId().toString()));
    }

    private Map<String, Object> instanceRow(EmergencyOrderSetInstanceEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instance_id", i.getInstanceId().toString());
        m.put("episode_id", i.getEpisodeId().toString());
        m.put("order_set_code", i.getOrderSetCode());
        m.put("order_set_name", i.getOrderSetName());
        m.put("status", i.getStatus());
        m.put("invoked_by", i.getInvokedBy());
        m.put("invoked_at", i.getInvokedAt() != null ? i.getInvokedAt().toString() : null);
        return m;
    }

    private Map<String, Object> itemRow(EmergencyOrderSetItemEntity it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", it.getItemId().toString());
        m.put("instance_id", it.getInstanceId().toString());
        m.put("order_code", it.getOrderCode());
        m.put("order_label", it.getOrderLabel());
        m.put("order_type", it.getOrderType());
        m.put("status", it.getStatus());
        m.put("diagnostic_order_id", it.getDiagnosticOrderId() != null ? it.getDiagnosticOrderId().toString() : null);
        m.put("ordered_at", it.getOrderedAt() != null ? it.getOrderedAt().toString() : null);
        m.put("ordered_by", it.getOrderedBy());
        m.put("declined_at", it.getDeclinedAt() != null ? it.getDeclinedAt().toString() : null);
        m.put("declined_by", it.getDeclinedBy());
        m.put("decline_reason", it.getDeclineReason());
        return m;
    }
}
