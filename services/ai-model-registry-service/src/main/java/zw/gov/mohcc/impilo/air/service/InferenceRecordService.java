package zw.gov.mohcc.impilo.air.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateInferenceRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.InferenceResponse;
import zw.gov.mohcc.impilo.air.events.AiRegistryOutboxAppender;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelEntity;
import zw.gov.mohcc.impilo.air.persistence.entity.InferenceRecordEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.AiModelRepository;
import zw.gov.mohcc.impilo.air.persistence.repository.InferenceRecordRepository;
import zw.gov.mohcc.impilo.air.support.AiRequestSupport;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

@Service
public class InferenceRecordService {

    private final InferenceRecordRepository recordRepository;
    private final AiModelRepository modelRepository;
    private final AiRegistryOutboxAppender outboxAppender;

    public InferenceRecordService(
            InferenceRecordRepository recordRepository,
            AiModelRepository modelRepository,
            AiRegistryOutboxAppender outboxAppender) {
        this.recordRepository = recordRepository;
        this.modelRepository = modelRepository;
        this.outboxAppender = outboxAppender;
    }

    public List<InferenceResponse> findByWorkflow(String workflowRef, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return recordRepository.findByTenantIdAndWorkflowRefOrderByCreatedAtDesc(tenantId, workflowRef).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<InferenceResponse> findBySubject(String subjectType, String subjectId, RequestContext ctx) {
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        return recordRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(tenantId, subjectType, subjectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InferenceResponse record(CreateInferenceRequest req, RequestContext ctx, String idempotencyKey) {
        FederationAuthority.requireNational();
        UUID tenantId = AiRequestSupport.tenantUuid(ctx);
        AiModelEntity model =
                modelRepository.findByTenantIdAndModelId(tenantId, req.modelId()).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found for tenant"));

        InferenceRecordEntity e = new InferenceRecordEntity();
        e.setTenantId(tenantId);
        e.setModelId(model.getModelId());
        e.setModelVersion(req.modelVersion());
        e.setInferenceClass(req.inferenceClass());
        e.setWorkflowRef(req.workflowRef());
        e.setSubjectType(req.subjectType());
        e.setSubjectId(req.subjectId());
        e.setOutputSummary(req.outputSummary());
        e.setScore(req.score());
        e.setConfidence(req.confidence());
        e.setHumanReviewRequired(req.humanReviewRequired() != null ? req.humanReviewRequired() : Boolean.TRUE);
        e.setHumanAccepted(req.humanAccepted());
        e.setOverrideReason(req.overrideReason());
        recordRepository.save(e);

        String ikey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : ctx.requestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_id", e.getRecordId().toString());
        payload.put("model_id", e.getModelId().toString());
        payload.put("workflow_ref", e.getWorkflowRef());
        outboxAppender.append(
                tenantId,
                ctx.podId(),
                AiRequestSupport.correlationUuid(ctx),
                null,
                ikey + ":inference",
                "AI_INFERENCE",
                e.getRecordId().toString(),
                "AI_INFERENCE_RECORDED",
                "INFERENCE",
                e.getRecordId().toString(),
                tenantId + ":" + e.getRecordId(),
                payload);
        return toResponse(e);
    }

    private InferenceResponse toResponse(InferenceRecordEntity e) {
        return new InferenceResponse(
                e.getRecordId(),
                e.getTenantId(),
                e.getModelId(),
                e.getModelVersion(),
                e.getInferenceClass(),
                e.getWorkflowRef(),
                e.getSubjectType(),
                e.getSubjectId(),
                e.getOutputSummary(),
                e.getScore(),
                e.getConfidence(),
                e.getHumanReviewRequired(),
                e.getHumanAccepted(),
                e.getOverrideReason(),
                e.getCreatedAt());
    }
}
