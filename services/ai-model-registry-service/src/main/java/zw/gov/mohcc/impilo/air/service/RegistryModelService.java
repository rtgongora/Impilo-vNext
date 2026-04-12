package zw.gov.mohcc.impilo.air.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.ApproveModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.ModelResponse;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.UpdateModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.WithdrawModelRequest;
import zw.gov.mohcc.impilo.air.events.AiRegistryOutboxAppender;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.AiModelRepository;
import zw.gov.mohcc.impilo.air.support.AiRequestSupport;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

@Service
public class RegistryModelService {

    private final AiModelRepository modelRepository;
    private final AiRegistryOutboxAppender outboxAppender;

    public RegistryModelService(AiModelRepository modelRepository, AiRegistryOutboxAppender outboxAppender) {
        this.modelRepository = modelRepository;
        this.outboxAppender = outboxAppender;
    }

    @Transactional
    public ModelResponse create(CreateModelRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        AiModelEntity e = new AiModelEntity();
        e.setTenantId(tenantId);
        e.setName(req.name());
        e.setDescription(req.description());
        e.setModelType(req.modelType());
        e.setOwner(req.owner());
        e.setStatus("DRAFT");
        modelRepository.save(e);

        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_id", e.getModelId().toString());
        payload.put("name", e.getName());
        payload.put("model_type", e.getModelType());
        outboxAppender.append(
                tenantId,
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":model-created",
                "AI_MODEL",
                e.getModelId().toString(),
                "AI_MODEL_CREATED",
                "MODEL",
                e.getModelId().toString(),
                tenantId + ":" + e.getModelId(),
                payload);
        return toResponse(e);
    }

    public List<ModelResponse> list(RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return modelRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ModelResponse get(UUID modelId, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return modelRepository
                .findByTenantIdAndModelId(tenantId, modelId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
    }

    @Transactional
    public ModelResponse update(UUID modelId, UpdateModelRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        AiModelEntity e = loadForTenant(modelId, ctx);
        if (!"DRAFT".equals(e.getStatus()) && !"APPROVED".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model cannot be updated in status " + e.getStatus());
        }
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.modelType() != null) {
            e.setModelType(req.modelType());
        }
        if (req.owner() != null) {
            e.setOwner(req.owner());
        }
        modelRepository.save(e);
        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_id", e.getModelId().toString());
        outboxAppender.append(
                e.getTenantId(),
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":model-updated",
                "AI_MODEL",
                e.getModelId().toString(),
                "AI_MODEL_UPDATED",
                "MODEL",
                e.getModelId().toString(),
                e.getTenantId() + ":" + e.getModelId(),
                payload);
        return toResponse(e);
    }

    @Transactional
    public ModelResponse approve(UUID modelId, ApproveModelRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        AiModelEntity e = loadForTenant(modelId, ctx);
        if (!"DRAFT".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT models can be approved");
        }
        String by = req != null && req.approvedBy() != null && !req.approvedBy().isBlank()
                ? req.approvedBy()
                : "system";
        e.setStatus("APPROVED");
        e.setApprovedBy(by);
        e.setApprovedAt(OffsetDateTime.now());
        modelRepository.save(e);
        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_id", e.getModelId().toString());
        payload.put("approved_by", by);
        outboxAppender.append(
                e.getTenantId(),
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":model-approved",
                "AI_MODEL",
                e.getModelId().toString(),
                "AI_MODEL_APPROVED",
                "MODEL",
                e.getModelId().toString(),
                e.getTenantId() + ":" + e.getModelId(),
                payload);
        return toResponse(e);
    }

    @Transactional
    public ModelResponse withdraw(UUID modelId, WithdrawModelRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        AiModelEntity e = loadForTenant(modelId, ctx);
        if ("WITHDRAWN".equals(e.getStatus())) {
            return toResponse(e);
        }
        e.setStatus("WITHDRAWN");
        e.setWithdrawnAt(OffsetDateTime.now());
        modelRepository.save(e);
        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_id", e.getModelId().toString());
        if (req != null && req.reason() != null) {
            payload.put("reason", req.reason());
        }
        outboxAppender.append(
                e.getTenantId(),
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":model-withdrawn",
                "AI_MODEL",
                e.getModelId().toString(),
                "AI_MODEL_WITHDRAWN",
                "MODEL",
                e.getModelId().toString(),
                e.getTenantId() + ":" + e.getModelId(),
                payload);
        return toResponse(e);
    }

    private AiModelEntity loadForTenant(UUID modelId, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return modelRepository
                .findByTenantIdAndModelId(tenantId, modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
    }

    private ModelResponse toResponse(AiModelEntity e) {
        return new ModelResponse(
                e.getModelId(),
                e.getTenantId(),
                e.getName(),
                e.getDescription(),
                e.getModelType(),
                e.getOwner(),
                e.getStatus(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getWithdrawnAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
