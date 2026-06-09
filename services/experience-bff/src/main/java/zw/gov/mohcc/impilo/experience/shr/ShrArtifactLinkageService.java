package zw.gov.mohcc.impilo.experience.shr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ShrArtifactLinkageService {

    private static final String KEY_PREFIX = "experience:bff:shr-linkage:";
    private static final long TTL_DAYS = 90;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ShrArtifactLinkageService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public ShrArtifactLinkageRecord recordIntent(
            String tenantId,
            String patientId,
            String artifactType,
            Map<String, Object> payload,
            String correlationId,
            String requestId) {
        ShrArtifactLinkageRecord record = new ShrArtifactLinkageRecord();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId != null ? tenantId : "unknown");
        record.setPatientId(patientId);
        record.setArtifactType(artifactType);
        record.setPayload(payload);
        record.setCorrelationId(correlationId);
        record.setRequestId(requestId);
        record.setStatus("PENDING_SHR_WRITE");
        record.setCreatedAt(OffsetDateTime.now());
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + record.getId(),
                    objectMapper.writeValueAsString(record),
                    TTL_DAYS,
                    TimeUnit.DAYS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to persist SHR linkage intent", ex);
        }
        return record;
    }
}
