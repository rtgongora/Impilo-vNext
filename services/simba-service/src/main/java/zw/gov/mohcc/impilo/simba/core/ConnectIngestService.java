package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConnectIngestLogEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ConnectIngestLogRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConnectIngestService {

    private final ConnectIngestLogRepository ingestLogRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public ConnectIngestService(ConnectIngestLogRepository ingestLogRepository,
                                EventOutboxRepository eventOutboxRepository,
                                ObjectMapper objectMapper) {
        this.ingestLogRepository = ingestLogRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a deduplicated Health-Connect-style ingest row and appends an outbox event.
     *
     * @return {@code true} if this was a new ingest; {@code false} if duplicate source_id.
     */
    @Transactional
    public boolean ingest(UUID tenantId,
                          String personCpid,
                          String dataType,
                          String sourceId,
                          Map<String, Object> payloadExtension) throws JsonProcessingException {

        if (ingestLogRepository.existsByTenantIdAndPersonCpidAndDataTypeAndSourceId(
                tenantId, personCpid, dataType, sourceId)) {
            return false;
        }

        ConnectIngestLogEntity log = new ConnectIngestLogEntity();
        log.setTenantId(tenantId);
        log.setPersonCpid(personCpid);
        log.setDataType(dataType);
        log.setSourceId(sourceId);
        ingestLogRepository.save(log);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", tenantId.toString());
        body.put("person_cpid", personCpid);
        body.put("data_type", dataType);
        body.put("source_id", sourceId);
        body.put("ingested_at", OffsetDateTime.now().toString());
        if (payloadExtension != null && !payloadExtension.isEmpty()) {
            body.put("extension", payloadExtension);
        }

        EventOutboxEntity out = new EventOutboxEntity();
        out.setAggregateType("WELLNESS_CONNECT");
        out.setAggregateId(sourceId);
        out.setEventType("impilo.simba.wellness.connect.ingested.v1");
        out.setSchemaVersion(1);
        out.setIdempotencyKey("simba:connect:" + tenantId + ":" + personCpid + ":" + dataType + ":" + sourceId);
        out.setTenantId(tenantId);
        out.setSubjectType("PERSON");
        out.setSubjectId(personCpid);
        out.setPartitionKey(personCpid);
        out.setOccurredAt(OffsetDateTime.now());
        out.setPayloadJson(objectMapper.writeValueAsString(body));

        var ctx = RequestContextHolder.get();
        if (ctx != null) {
            try {
                out.setPodId(ctx.podId());
                out.setCorrelationId(UUID.fromString(ctx.correlationId()));
            } catch (Exception ignored) {
                // correlation may be non-UUID in some callers; leave null
            }
        } else {
            out.setPodId("national-spine");
        }

        eventOutboxRepository.save(out);
        return true;
    }
}
