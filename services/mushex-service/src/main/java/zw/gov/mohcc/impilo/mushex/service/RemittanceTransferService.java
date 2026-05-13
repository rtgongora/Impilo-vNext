package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceRequestEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RemittanceRequestRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domestic and international remittance request lifecycle (distinct from token-based slip remittance).
 */
@Service
public class RemittanceTransferService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceTransferService.class);

    private final RemittanceRequestRepository remittanceRequestRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RemittanceTransferService(RemittanceRequestRepository remittanceRequestRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.remittanceRequestRepository = remittanceRequestRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<RemittanceRequestEntity> listForSender(String senderRef) {
        TrustContext ctx = TrustContextHolder.require();
        return remittanceRequestRepository.findByTenantIdAndSenderRefOrderByCreatedAtDesc(
                ctx.tenantId(), senderRef);
    }

    public RemittanceRequestEntity getById(String remittanceRequestId) {
        TrustContext ctx = TrustContextHolder.require();
        RemittanceRequestEntity row = remittanceRequestRepository.findById(remittanceRequestId)
                .orElseThrow(() -> new PlatformResourceNotFoundException(
                        "Remittance transfer not found: " + remittanceRequestId));
        if (!ctx.tenantId().equals(row.getTenantId())) {
            throw new PlatformResourceNotFoundException(
                    "Remittance transfer not found: " + remittanceRequestId);
        }
        return row;
    }

    @Transactional
    public RemittanceRequestEntity createRequest(String remittanceCode,
                                                 String senderRef,
                                                 String recipientRef,
                                                 String recipientType,
                                                 String originCountry,
                                                 String destinationCountry,
                                                 String domesticOrInternational,
                                                 String remittancePurpose,
                                                 String healthContextJson,
                                                 BigDecimal amount,
                                                 String currency) {
        TrustContext ctx = TrustContextHolder.require();
        RemittanceRequestEntity r = new RemittanceRequestEntity();
        r.setRemittanceRequestId(UlidGenerator.generate());
        r.setTenantId(ctx.tenantId());
        r.setRemittanceCode(remittanceCode);
        r.setSenderRef(senderRef);
        r.setRecipientRef(recipientRef);
        r.setRecipientType(recipientType);
        r.setOriginCountry(originCountry);
        r.setDestinationCountry(destinationCountry);
        r.setDomesticOrInternational(domesticOrInternational != null ? domesticOrInternational : "DOMESTIC");
        r.setRemittancePurpose(remittancePurpose);
        r.setHealthContext(healthContextJson);
        r.setAmount(amount);
        r.setCurrency(currency != null ? currency : "USD");
        r.setStatus("REQUESTED");
        r = remittanceRequestRepository.save(r);

        publishEvent("REMITTANCE_REQUEST", r.getRemittanceRequestId(), "REMITTANCE_REQUESTED",
                Map.of(
                        "remittanceRequestId", r.getRemittanceRequestId(),
                        "remittanceCode", remittanceCode,
                        "amount", amount,
                        "domesticOrInternational", r.getDomesticOrInternational()
                ),
                ctx.tenantId());
        log.info("Created remittance request {} code={}", r.getRemittanceRequestId(), remittanceCode);
        return r;
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType,
                              Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write MusheX outbox event {}", eventType, e);
        }
    }
}
