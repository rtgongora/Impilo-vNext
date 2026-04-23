package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.integration.CouncilRegulatoryPolicyClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdCycleEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEventEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CpdCycleRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.FundoCpdCandidateRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Governed bridge from Impilo Fundo (Moodle) completions to Varapi CPD events.
 * Imported rows are candidates until council (or configured auto-accept) verifies them.
 */
@Service
public class FundoCpdGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(FundoCpdGovernanceService.class);

    private final FundoCpdCandidateRepository candidateRepository;
    private final ProviderRepository providerRepository;
    private final CouncilRepository councilRepository;
    private final CpdService cpdService;
    private final CpdCycleRepository cycleRepository;
    private final EventOutboxRepository outboxRepository;
    private final VarapiProperties varapiProperties;
    private final CouncilRegulatoryPolicyClient policyClient;

    public FundoCpdGovernanceService(
            FundoCpdCandidateRepository candidateRepository,
            ProviderRepository providerRepository,
            CouncilRepository councilRepository,
            CpdService cpdService,
            CpdCycleRepository cycleRepository,
            EventOutboxRepository outboxRepository,
            VarapiProperties varapiProperties,
            CouncilRegulatoryPolicyClient policyClient) {
        this.candidateRepository = candidateRepository;
        this.providerRepository = providerRepository;
        this.councilRepository = councilRepository;
        this.cpdService = cpdService;
        this.cycleRepository = cycleRepository;
        this.outboxRepository = outboxRepository;
        this.varapiProperties = varapiProperties;
        this.policyClient = policyClient;
    }

    @Transactional(readOnly = true)
    public List<FundoCpdCandidateEntity> listForProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        requireProvider(providerId, ctx.tenantId());
        return candidateRepository.findByTenantIdAndProvider_IdOrderByCreatedAtDesc(ctx.tenantId(), providerId);
    }

    @Transactional
    public FundoCpdCandidateEntity ingestCompletion(
            UUID tenantId,
            String providerPublicId,
            Long councilId,
            String courseId,
            String courseName,
            Instant completedAt,
            int creditsSuggested,
            String externalRef,
            String rawJson) {
        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerPublicId));
        if (!provider.getTenantId().equals(tenantId)) {
            throw new IllegalStateException("Provider tenant mismatch");
        }
        Optional<FundoCpdCandidateEntity> existing = candidateRepository
                .findByTenantIdAndProvider_IdAndExternalRef(tenantId, provider.getId(), externalRef);
        if (existing.isPresent()) {
            return existing.get();
        }

        FundoCpdCandidateEntity c = new FundoCpdCandidateEntity();
        c.setTenantId(tenantId);
        c.setProvider(provider);
        if (councilId != null) {
            CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));
            c.setCouncil(council);
        }
        c.setCourseId(courseId);
        c.setCourseName(courseName);
        c.setCompletedAt(completedAt);
        c.setCreditsSuggested(Math.max(0, creditsSuggested));
        c.setExternalRef(externalRef);
        c.setVerificationState("PENDING");
        c.setRawPayload(rawJson);
        c = candidateRepository.save(c);

        publish(tenantId, "provider_council.fundo.sync_completed",
                "{\"candidateId\":" + c.getId() + ",\"providerPublicId\":\"" + providerPublicId + "\"}");

        if (varapiProperties.getFundo().isAutoAcceptCpdFromFundo()) {
            TrustContext prior = TrustContextHolder.get();
            try {
                TrustContextHolder.set(new TrustContext(
                        tenantId, "FUNDO_SYNC", "SYSTEM", "REGISTRY_ADMIN", "fundo-webhook",
                        UUID.randomUUID(), null, null, null, prior != null ? prior.mode() : AccessMode.INTERNAL));
                acceptCandidate(c.getId());
            } finally {
                if (prior != null) {
                    TrustContextHolder.set(prior);
                } else {
                    TrustContextHolder.clear();
                }
            }
        }
        return c;
    }

    @Transactional
    public FundoCpdCandidateEntity acceptCandidate(Long candidateId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("ACCEPT_FUNDO_CPD", null, null, null)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        FundoCpdCandidateEntity c = candidateRepository.findByIdAndTenantId(candidateId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));
        if (!"PENDING".equals(c.getVerificationState())) {
            return c;
        }
        ProviderEntity provider = c.getProvider();
        CpdCycleEntity cycle = cycleRepository
                .findFirstByProvider_IdAndStatusOrderByIdDesc(provider.getId(), "IN_PROGRESS")
                .orElseThrow(() -> new IllegalStateException("No IN_PROGRESS CPD cycle for provider"));

        LocalDate eventDate = LocalDate.ofInstant(c.getCompletedAt(), ZoneOffset.UTC);
        CpdEventEntity event = cpdService.recordEvent(cycle.getId(), new CpdService.RecordCpdEventRequest(
                "FUNDO",
                c.getCourseName() != null ? c.getCourseName() : c.getCourseId(),
                "Imported from Impilo Fundo — pending council governance",
                Math.max(0, c.getCreditsSuggested()),
                eventDate,
                c.getExternalRef()
        ));
        c.setLinkedCpdEventId(event.getId());
        c.setVerificationState("ACCEPTED");
        c = candidateRepository.save(c);

        publish(ctx.tenantId(), "provider_council.cpd.updated",
                "{\"candidateId\":" + candidateId + ",\"cpdEventId\":" + event.getId() + "}");
        return c;
    }

    @Transactional
    public FundoCpdCandidateEntity rejectCandidate(Long candidateId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("REJECT_FUNDO_CPD", null, null, null)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        FundoCpdCandidateEntity c = candidateRepository.findByIdAndTenantId(candidateId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));
        c.setVerificationState("REJECTED");
        c = candidateRepository.save(c);
        publish(ctx.tenantId(), "provider_council.cpd.reviewed",
                "{\"candidateId\":" + candidateId + ",\"decision\":\"REJECTED\"}");
        return c;
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

    private void publish(UUID tenantId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FUNDO_CPD_CANDIDATE");
        event.setAggregateId(tenantId.toString());
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
