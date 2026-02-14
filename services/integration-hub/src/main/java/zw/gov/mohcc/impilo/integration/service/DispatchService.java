package zw.gov.mohcc.impilo.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchRequest;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchResponse;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DispatchService(OutboxEventRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchResponse dispatch(DispatchRequest request, RequestContext ctx) {
        String dispatchId = UUID.randomUUID().toString();
        String idempotencyKey = "dispatch-" + dispatchId;

        log.info("Dispatching event: id={}, source={}, type={}, target={}",
                dispatchId, request.sourceService(), request.eventType(), request.targetService());

        try {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventType("impilo.integration.dispatch.requested.v1")
                    .schemaVersion(1)
                    .correlationId(ctx.correlationId())
                    .causationId(ctx.requestId())
                    .idempotencyKey(idempotencyKey)
                    .producer("integration-hub")
                    .tenantId(ctx.tenantId())
                    .podId(ctx.podId())
                    .occurredAt(OffsetDateTime.now())
                    .subjectType("Dispatch")
                    .subjectId(dispatchId)
                    .payload(Map.of(
                            "dispatchId", dispatchId,
                            "sourceService", request.sourceService(),
                            "eventType", request.eventType(),
                            "targetService", request.targetService(),
                            "payloadJson", request.payloadJson()
                    ))
                    .build();

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setTenantId(ctx.tenantId());
            outbox.setPodId(ctx.podId());
            outbox.setCorrelationId(ctx.correlationId());
            outbox.setIdempotencyKey(idempotencyKey);
            outbox.setEventType("impilo.integration.dispatch.requested.v1");
            outbox.setSchemaVersion(1);
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(objectMapper.writeValueAsString(envelope));

            outboxRepository.save(outbox);

            return new DispatchResponse(dispatchId, "ACCEPTED");
        } catch (Exception e) {
            log.error("Failed to record dispatch event: {}", dispatchId, e);
            return new DispatchResponse(dispatchId, "FAILED");
        }
    }
}
