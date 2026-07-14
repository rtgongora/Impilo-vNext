package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugResponse;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ControlledDrugRegisterEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ControlledDrugRegisterRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled-drug witness register.
 *
 * <p>Records a running ledger of controlled-drug movements against a reference
 * (typically a theatre case / episode). ISSUE credits the running balance;
 * ADMINISTER, WASTE and DISCARD debit it and each require a witnessing
 * provider. The balance is carried forward per (tenant, item_code, ref_id).</p>
 */
@Service
public class ControlledDrugRegisterService {

    private static final Logger log = LoggerFactory.getLogger(ControlledDrugRegisterService.class);

    private static final String ISSUE = "ISSUE";
    private static final Set<String> WITNESS_REQUIRED = Set.of("ADMINISTER", "WASTE", "DISCARD");
    private static final Set<String> VALID_ACTIONS = Set.of("ISSUE", "ADMINISTER", "WASTE", "DISCARD");

    private final ControlledDrugRegisterRepository registerRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ControlledDrugRegisterService(ControlledDrugRegisterRepository registerRepository,
                                         EventOutboxRepository outboxRepository,
                                         ObjectMapper objectMapper) {
        this.registerRepository = registerRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecordControlledDrugResponse record(RecordControlledDrugRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenant = ctx.tenantId();

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.itemCode() == null || request.itemCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemCode is required");
        }
        String action = request.action() == null ? null : request.action().trim().toUpperCase();
        if (action == null || !VALID_ACTIONS.contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be one of ISSUE|ADMINISTER|WASTE|DISCARD");
        }
        BigDecimal quantity = request.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }

        boolean witnessBlank = request.witnessProvider() == null || request.witnessProvider().isBlank();
        if (WITNESS_REQUIRED.contains(action) && witnessBlank) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "witness_provider is required to administer/waste a controlled drug");
        }

        // Carry the running balance forward from the latest prior entry for this drug on this reference.
        BigDecimal prior = registerRepository
                .findFirstByTenantIdAndItemCodeAndRefIdOrderByCreatedAtDesc(tenant, request.itemCode(), request.refId())
                .map(ControlledDrugRegisterEntity::getRunningBalance)
                .orElse(BigDecimal.ZERO);
        if (prior == null) {
            prior = BigDecimal.ZERO;
        }
        BigDecimal runningBalance = ISSUE.equals(action) ? prior.add(quantity) : prior.subtract(quantity);

        ControlledDrugRegisterEntity entry = new ControlledDrugRegisterEntity();
        entry.setTenantId(tenant);
        entry.setFacilityId(request.facilityId());
        entry.setStoreId(request.storeId());
        entry.setItemCode(request.itemCode());
        entry.setDrugName(request.drugName());
        entry.setAction(action);
        entry.setQuantity(quantity);
        entry.setUnit(request.unit());
        entry.setRunningBalance(runningBalance);
        entry.setAdministeringProvider(request.administeringProvider());
        entry.setWitnessProvider(request.witnessProvider());
        entry.setRefType(request.refType() != null ? request.refType() : "THEATRE_CASE");
        entry.setRefId(request.refId());
        entry.setChartEntryRef(request.chartEntryRef());
        entry.setNotes(request.notes());
        entry.setCreatedBy(ctx.actorId());

        entry = registerRepository.save(entry);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("entryId", entry.getEntryId());
        event.put("itemCode", entry.getItemCode());
        event.put("drugName", entry.getDrugName());
        event.put("action", entry.getAction());
        event.put("quantity", entry.getQuantity());
        event.put("runningBalance", entry.getRunningBalance());
        event.put("administeringProvider", entry.getAdministeringProvider());
        event.put("witnessProvider", entry.getWitnessProvider());
        event.put("refType", entry.getRefType());
        event.put("refId", entry.getRefId());
        publishOutboxEvent("CONTROLLED_DRUG", entry.getEntryId().toString(),
                "inventory.controlled_drug.recorded", event, tenant);

        log.info("Controlled-drug entry recorded: entry={}, item={}, action={}, qty={}, balance={}",
                entry.getEntryId(), entry.getItemCode(), action, quantity, runningBalance);

        return new RecordControlledDrugResponse(entry.getEntryId(), entry.getAction(),
                entry.getQuantity(), entry.getRunningBalance(), entry.getWitnessProvider());
    }

    @Transactional(readOnly = true)
    public List<ControlledDrugRegisterEntity> list(String refType, String refId) {
        TrustContext ctx = TrustContextHolder.require();
        String type = refType != null ? refType : "THEATRE_CASE";
        return registerRepository.findByTenantIdAndRefTypeAndRefIdOrderByCreatedAtAsc(
                ctx.tenantId(), type, refId);
    }

    /**
     * Current running balance for a drug on a reference (0 if no entries yet).
     */
    @Transactional(readOnly = true)
    public BigDecimal currentBalance(String itemCode, String refId) {
        TrustContext ctx = TrustContextHolder.require();
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        return registerRepository
                .findFirstByTenantIdAndItemCodeAndRefIdOrderByCreatedAtDesc(ctx.tenantId(), itemCode, refId)
                .map(e -> e.getRunningBalance() != null ? e.getRunningBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                    String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }
}
