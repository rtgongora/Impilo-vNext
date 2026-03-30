package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.CreateTariffRequest;
import zw.gov.mohcc.impilo.msika.api.dto.TariffView;
import zw.gov.mohcc.impilo.msika.api.dto.UpdateTariffRequest;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.TariffEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.TariffRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;

@Service
public class TariffService {

    private static final Logger log = LoggerFactory.getLogger(TariffService.class);

    private final TariffRepository tariffRepository;
    private final EventOutboxRepository outboxRepository;
    private final ChangeLogService changeLogService;
    private final ObjectMapper objectMapper;

    public TariffService(TariffRepository tariffRepository,
                         EventOutboxRepository outboxRepository,
                         ChangeLogService changeLogService,
                         ObjectMapper objectMapper) {
        this.tariffRepository = tariffRepository;
        this.outboxRepository = outboxRepository;
        this.changeLogService = changeLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TariffEntity createTariff(CreateTariffRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        tariffRepository.findByTariffCode(request.tariffCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("Tariff with code " + request.tariffCode() + " already exists");
        });

        TariffEntity tariff = new TariffEntity();
        tariff.setTariffId(UlidGenerator.generate());
        tariff.setTariffCode(request.tariffCode());
        tariff.setName(request.name());
        tariff.setDescription(request.description());
        tariff.setCategory(request.category());
        tariff.setUnitPrice(request.unitPrice());
        tariff.setCurrency(request.currency() != null ? request.currency() : "USD");
        tariff.setBillingModel(request.billingModel() != null ? request.billingModel() : "FEE_FOR_SERVICE");
        tariff.setEffectiveFrom(request.effectiveFrom());
        tariff.setEffectiveTo(request.effectiveTo());
        tariff.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        if (ctx.tenantId() != null) {
            tariff.setTenantId(ctx.tenantId());
        }

        try {
            if (request.pricingRules() != null) {
                tariff.setPricingRules(objectMapper.writeValueAsString(request.pricingRules()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize pricingRules: {}", e.getMessage());
        }

        tariff = tariffRepository.save(tariff);
        emitOutboxEvent("TARIFF", tariff.getTariffId(), "TARIFF_CREATED", tariff, ctx);
        changeLogService.record("CREATE", "TARIFF", tariff.getTariffId(), request);
        log.info("Created tariff {} (code={})", tariff.getTariffId(), tariff.getTariffCode());
        return tariff;
    }

    @Transactional
    public TariffEntity updateTariff(String tariffId, UpdateTariffRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        TariffEntity tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new IllegalArgumentException("Tariff not found: " + tariffId));

        if (request.name() != null) tariff.setName(request.name());
        if (request.description() != null) tariff.setDescription(request.description());
        if (request.category() != null) tariff.setCategory(request.category());
        if (request.unitPrice() != null) tariff.setUnitPrice(request.unitPrice());
        if (request.currency() != null) tariff.setCurrency(request.currency());
        if (request.billingModel() != null) tariff.setBillingModel(request.billingModel());
        if (request.effectiveFrom() != null) tariff.setEffectiveFrom(request.effectiveFrom());
        if (request.effectiveTo() != null) tariff.setEffectiveTo(request.effectiveTo());
        if (request.status() != null) tariff.setStatus(request.status());
        tariff.setUpdatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        try {
            if (request.pricingRules() != null) {
                tariff.setPricingRules(objectMapper.writeValueAsString(request.pricingRules()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize pricingRules: {}", e.getMessage());
        }

        tariff = tariffRepository.save(tariff);
        emitOutboxEvent("TARIFF", tariff.getTariffId(), "TARIFF_UPDATED", tariff, ctx);
        changeLogService.record("UPDATE", "TARIFF", tariff.getTariffId(), request);
        log.info("Updated tariff {} (code={}, unitPrice={})", tariff.getTariffId(), tariff.getTariffCode(), tariff.getUnitPrice());
        return tariff;
    }

    public TariffView getTariff(String tariffId) {
        TariffEntity tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new IllegalArgumentException("Tariff not found: " + tariffId));
        return toView(tariff);
    }

    public Page<TariffView> listTariffs(Pageable pageable) {
        return tariffRepository.findAll(pageable).map(this::toView);
    }

    public TariffView toView(TariffEntity t) {
        return new TariffView(
                t.getTariffId(), t.getTariffCode(), t.getName(), t.getDescription(),
                t.getCategory(), t.getUnitPrice(), t.getCurrency(), t.getBillingModel(),
                t.getEffectiveFrom(), t.getEffectiveTo(), t.getTenantId(),
                t.getStatus(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private void emitOutboxEvent(String aggregateType, String aggregateId, String eventType,
                                  Object payload, TrustContext ctx) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTenantId(ctx.tenantId());
        event.setPodId(ctx.podId());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setOccurredAt(OffsetDateTime.now());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }
}
