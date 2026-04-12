package zw.gov.mohcc.impilo.air.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateDriftRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.DriftResponse;
import zw.gov.mohcc.impilo.air.events.AiRegistryOutboxAppender;
import zw.gov.mohcc.impilo.air.persistence.entity.DriftEventEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.DriftEventRepository;
import zw.gov.mohcc.impilo.air.support.AiRequestSupport;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

@Service
public class DriftEventService {

    private final DriftEventRepository driftRepository;
    private final AiRegistryOutboxAppender outboxAppender;

    public DriftEventService(DriftEventRepository driftRepository, AiRegistryOutboxAppender outboxAppender) {
        this.driftRepository = driftRepository;
        this.outboxAppender = outboxAppender;
    }

    public List<DriftResponse> listByModel(UUID modelId, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return driftRepository.findByTenantIdAndModelIdOrderByDetectedAtDesc(tenantId, modelId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DriftResponse record(CreateDriftRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        DriftEventEntity e = new DriftEventEntity();
        e.setTenantId(tenantId);
        e.setModelId(req.modelId());
        e.setModelVersion(req.modelVersion());
        e.setDriftMetric(req.driftMetric());
        e.setDriftValue(req.driftValue());
        e.setThreshold(req.threshold());
        e.setSeverity(req.severity());
        driftRepository.save(e);

        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", e.getEventId().toString());
        payload.put("model_id", e.getModelId().toString());
        payload.put("drift_metric", e.getDriftMetric());
        outboxAppender.append(
                tenantId,
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":drift",
                "AI_DRIFT",
                e.getEventId().toString(),
                "AI_DRIFT_RECORDED",
                "DRIFT",
                e.getEventId().toString(),
                tenantId + ":" + e.getEventId(),
                payload);
        return toResponse(e);
    }

    private DriftResponse toResponse(DriftEventEntity e) {
        return new DriftResponse(
                e.getEventId(),
                e.getTenantId(),
                e.getModelId(),
                e.getModelVersion(),
                e.getDriftMetric(),
                e.getDriftValue(),
                e.getThreshold(),
                e.getSeverity(),
                e.getDetectedAt());
    }
}
