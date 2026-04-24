package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.integration.CouncilRegulatoryPolicyClient;
import zw.gov.mohcc.impilo.varapi.integration.MusheXClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPaymentObligationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderPaymentObligationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderPaymentObligationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderPaymentObligationService.class);

    private final ProviderPaymentObligationRepository obligationRepository;
    private final ProviderRepository providerRepository;
    private final CouncilRepository councilRepository;
    private final ProviderApplicationRepository applicationRepository;
    private final MusheXClient musheXClient;
    private final EventOutboxRepository outboxRepository;
    private final CouncilRegulatoryPolicyClient policyClient;
    private final ProviderApplicationService applicationService;

    public ProviderPaymentObligationService(
            ProviderPaymentObligationRepository obligationRepository,
            ProviderRepository providerRepository,
            CouncilRepository councilRepository,
            ProviderApplicationRepository applicationRepository,
            MusheXClient musheXClient,
            EventOutboxRepository outboxRepository,
            CouncilRegulatoryPolicyClient policyClient,
            ProviderApplicationService applicationService) {
        this.obligationRepository = obligationRepository;
        this.providerRepository = providerRepository;
        this.councilRepository = councilRepository;
        this.applicationRepository = applicationRepository;
        this.musheXClient = musheXClient;
        this.outboxRepository = outboxRepository;
        this.policyClient = policyClient;
        this.applicationService = applicationService;
    }

    @Transactional(readOnly = true)
    public List<ProviderPaymentObligationEntity> listForProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        requireProvider(providerId, ctx.tenantId());
        return obligationRepository.findByTenantIdAndProvider_Id(ctx.tenantId(), providerId);
    }

    @Transactional
    public ProviderPaymentObligationEntity createObligation(
            Long providerId,
            Long applicationId,
            Long councilId,
            String obligationType,
            BigDecimal amount,
            String currency,
            LocalDate dueDate,
            String idempotencyKey) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("CREATE_OBLIGATION", councilId, applicationId, providerId)) {
            throw new IllegalStateException("POLICY_DENIED");
        }

        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());
        ProviderPaymentObligationEntity row = new ProviderPaymentObligationEntity();
        row.setTenantId(ctx.tenantId());
        row.setProvider(provider);
        row.setObligationType(obligationType);
        row.setAmount(amount);
        row.setCurrency(currency != null ? currency : "USD");
        row.setDueDate(dueDate);
        row.setStatus("OPEN");
        row.setIdempotencyKey(idempotencyKey);

        if (applicationId != null) {
            ProviderApplicationEntity app = applicationRepository.findByIdAndTenantId(applicationId, ctx.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
            row.setApplication(app);
        }
        if (councilId != null) {
            CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));
            row.setCouncil(council);
        }

        row = obligationRepository.save(row);
        publish(ctx, "provider_council.payment.obligation_created",
                "{\"obligationId\":" + row.getId() + ",\"providerId\":" + providerId + "}");
        return row;
    }

    /**
     * Persists MusheX intent id on the obligation (intent must already exist in MusheX).
     */
    @Transactional
    public ProviderPaymentObligationEntity attachMusheXIntent(Long obligationId, String intentId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        ProviderPaymentObligationEntity row = obligationRepository.findByIdAndTenantId(obligationId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Obligation not found: " + obligationId));
        row.setMushexIntentId(intentId);
        return obligationRepository.save(row);
    }

    /**
     * Creates a MusheX payment intent for an existing obligation and stores the intent id.
     */
    @Transactional
    public ProviderPaymentObligationEntity createMusheXIntentForObligation(Long obligationId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        ProviderPaymentObligationEntity row = obligationRepository.findByIdAndTenantId(obligationId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Obligation not found: " + obligationId));
        ProviderEntity provider = row.getProvider();
        if (!policyClient.isAllowed("INITIATE_MUSHEX_INTENT",
                row.getCouncil() != null ? row.getCouncil().getId() : null,
                row.getApplication() != null ? row.getApplication().getId() : null,
                provider.getId())) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        if (row.getMushexIntentId() != null && !row.getMushexIntentId().isBlank()) {
            return row;
        }

        String metadata = musheXClient.buildMetadataJson(
                row.getId(),
                row.getApplication() != null ? row.getApplication().getId() : null,
                provider.getProviderPublicId());

        String intentId = musheXClient.createPaymentIntent(
                "PROVIDER_COUNCIL_FEE",
                "OBL-" + row.getId(),
                row.getAmount(),
                row.getCurrency(),
                ctx.facilityId(),
                row.getIdempotencyKey(),
                metadata);

        if (intentId != null) {
            row.setMushexIntentId(intentId);
            row = obligationRepository.save(row);
        }
        return row;
    }

    /**
     * Reads MusheX intent status and updates local obligation + may advance linked application after PAID.
     */
    @Transactional
    public ProviderPaymentObligationEntity syncFromMusheX(Long obligationId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        ProviderPaymentObligationEntity row = obligationRepository.findByIdAndTenantId(obligationId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Obligation not found: " + obligationId));
        if (row.getMushexIntentId() == null || row.getMushexIntentId().isBlank()) {
            return row;
        }
        JsonNode intent = musheXClient.getPaymentIntent(row.getMushexIntentId());
        if (intent == null || intent.isMissingNode()) {
            return row;
        }
        String status = intent.path("status").asText("");
        return applyIntentStatus(ctx, row, status);
    }

    /**
     * Idempotent settlement driven by MusheX Kafka status events (no HTTP round-trip to MusheX).
     */
    @Transactional
    public void reconcileObligationFromMusheXKafka(String mushexIntentId, String toStatus) {
        if (mushexIntentId == null || mushexIntentId.isBlank() || toStatus == null || toStatus.isBlank()) {
            return;
        }
        ProviderPaymentObligationEntity row = obligationRepository
                .findTopByMushexIntentIdOrderByIdAsc(mushexIntentId)
                .orElse(null);
        if (row == null) {
            return;
        }
        TrustContext ctx = systemContextForKafkaReconciliation(row);
        TrustContextHolder.set(ctx);
        try {
            ProviderPaymentObligationEntity fresh = obligationRepository
                    .findByIdAndTenantId(row.getId(), row.getTenantId())
                    .orElseThrow();
            applyIntentStatus(ctx, fresh, toStatus);
        } finally {
            TrustContextHolder.clear();
        }
    }

    private ProviderPaymentObligationEntity applyIntentStatus(TrustContext ctx, ProviderPaymentObligationEntity row, String status) {
        switch (status) {
            case "PAID" -> {
                if ("SETTLED".equals(row.getStatus())) {
                    return row;
                }
                row.setStatus("SETTLED");
                obligationRepository.save(row);
                publish(ctx, "provider_council.payment.settled",
                        "{\"obligationId\":" + row.getId() + ",\"intentId\":\"" + row.getMushexIntentId() + "\"}");
                if (row.getApplication() != null) {
                    try {
                        applicationService.advanceAfterFeePayment(row.getApplication().getId());
                    } catch (Exception e) {
                        log.debug("Application not advanced after payment (state may not allow): {}", e.getMessage());
                    }
                }
            }
            case "REFUNDED" -> {
                if ("REFUNDED".equals(row.getStatus())) {
                    return row;
                }
                row.setStatus("REFUNDED");
                obligationRepository.save(row);
            }
            case "CANCELLED", "FAILED" -> {
                if (status.equals(row.getStatus())) {
                    return row;
                }
                row.setStatus(status);
                obligationRepository.save(row);
            }
            default -> {
                row.setStatus("PENDING_PAYMENT");
                obligationRepository.save(row);
            }
        }
        return row;
    }

    private static TrustContext systemContextForKafkaReconciliation(ProviderPaymentObligationEntity row) {
        return new TrustContext(
                row.getTenantId(),
                "SYSTEM:MUSHEX-KAFKA",
                "SERVICE",
                "PAYMENT_RECONCILIATION",
                "kafka-consumer",
                null,
                null,
                null,
                null,
                AccessMode.INTERNAL);
    }

    private ProviderEntity requireProvider(Long providerId, UUID tenantId) {
        return providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
    }

    private static void requireTenant(TrustContext ctx) {
        if (ctx.tenantId() == null) {
            throw new IllegalStateException("Tenant is required");
        }
    }

    private void publish(TrustContext ctx, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PROVIDER_PAYMENT_OBLIGATION");
        event.setAggregateId(ctx.tenantId().toString());
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
