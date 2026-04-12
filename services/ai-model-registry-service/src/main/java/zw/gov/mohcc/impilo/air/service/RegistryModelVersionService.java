package zw.gov.mohcc.impilo.air.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateVersionRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.VersionResponse;
import zw.gov.mohcc.impilo.air.events.AiRegistryOutboxAppender;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelEntity;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelVersionEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.AiModelRepository;
import zw.gov.mohcc.impilo.air.persistence.repository.AiModelVersionRepository;
import zw.gov.mohcc.impilo.air.support.AiRequestSupport;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

@Service
public class RegistryModelVersionService {

    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;
    private final AiRegistryOutboxAppender outboxAppender;

    public RegistryModelVersionService(
            AiModelRepository modelRepository,
            AiModelVersionRepository versionRepository,
            AiRegistryOutboxAppender outboxAppender) {
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.outboxAppender = outboxAppender;
    }

    public List<VersionResponse> list(UUID modelId, RequestContext ctx) {
        AiModelEntity model = loadModel(modelId, ctx);
        return versionRepository.findByModelIdOrderByCreatedAtDesc(model.getModelId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VersionResponse create(UUID modelId, CreateVersionRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        AiModelEntity model = loadModel(modelId, ctx);
        AiModelVersionEntity v = new AiModelVersionEntity();
        v.setModelId(model.getModelId());
        v.setVersion(req.version());
        v.setArtifactRef(req.artifactRef());
        v.setConfigJson(req.configJson() != null ? req.configJson() : "{}");
        v.setApprovedUseCases(req.approvedUseCases() != null ? req.approvedUseCases() : "[]");
        v.setProhibitedUseCases(req.prohibitedUseCases() != null ? req.prohibitedUseCases() : "[]");
        v.setDriftThreshold(req.driftThreshold());
        if (req.status() != null && !req.status().isBlank()) {
            v.setStatus(req.status());
        }
        versionRepository.save(v);

        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version_id", v.getVersionId().toString());
        payload.put("model_id", model.getModelId().toString());
        payload.put("version", v.getVersion());
        outboxAppender.append(
                model.getTenantId(),
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":version-created",
                "AI_MODEL_VERSION",
                v.getVersionId().toString(),
                "AI_MODEL_VERSION_CREATED",
                "MODEL_VERSION",
                v.getVersionId().toString(),
                model.getTenantId() + ":" + v.getVersionId(),
                payload);
        return toResponse(v);
    }

    private AiModelEntity loadModel(UUID modelId, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return modelRepository
                .findByTenantIdAndModelId(tenantId, modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
    }

    private VersionResponse toResponse(AiModelVersionEntity v) {
        return new VersionResponse(
                v.getVersionId(),
                v.getModelId(),
                v.getVersion(),
                v.getArtifactRef(),
                v.getConfigJson(),
                v.getApprovedUseCases(),
                v.getProhibitedUseCases(),
                v.getDriftThreshold(),
                v.getStatus(),
                v.getCreatedAt());
    }
}
