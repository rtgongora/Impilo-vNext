package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.CouncilRegulatoryConfigService;
import zw.gov.mohcc.impilo.varapi.core.FundoCpdGovernanceService;
import zw.gov.mohcc.impilo.varapi.core.ProviderApplicationService;
import zw.gov.mohcc.impilo.varapi.core.ProviderCouncilRegulationService;
import zw.gov.mohcc.impilo.varapi.core.ProviderPaymentObligationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilRegulatoryConfigEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoLearningLinkEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilReviewEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPaymentObligationEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Internal APIs for council-regulated provider lifecycle (profiles, fees, Fundo CPD candidates, queues).
 */
@RestController
@RequestMapping("/v1/internal/provider-council")
public class ProviderCouncilInternalController {

    private final ProviderCouncilRegulationService councilRegulationService;
    private final CouncilRegulatoryConfigService councilRegulatoryConfigService;
    private final ProviderPaymentObligationService obligationService;
    private final ProviderApplicationService applicationService;
    private final FundoCpdGovernanceService fundoCpdGovernanceService;

    public ProviderCouncilInternalController(
            ProviderCouncilRegulationService councilRegulationService,
            CouncilRegulatoryConfigService councilRegulatoryConfigService,
            ProviderPaymentObligationService obligationService,
            ProviderApplicationService applicationService,
            FundoCpdGovernanceService fundoCpdGovernanceService) {
        this.councilRegulationService = councilRegulationService;
        this.councilRegulatoryConfigService = councilRegulatoryConfigService;
        this.obligationService = obligationService;
        this.applicationService = applicationService;
        this.fundoCpdGovernanceService = fundoCpdGovernanceService;
    }

    @GetMapping("/council-regulatory-config")
    public ResponseEntity<CouncilRegulatoryConfigEntity> getCouncilRegulatoryConfig(@RequestParam Long councilId) {
        Optional<CouncilRegulatoryConfigEntity> row = councilRegulatoryConfigService.getForCouncil(councilId);
        return row.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/council-regulatory-config")
    public ResponseEntity<CouncilRegulatoryConfigEntity> putCouncilRegulatoryConfig(@RequestBody Map<String, Object> body) {
        Long councilId = Long.valueOf(body.get("councilId").toString());
        Integer configVersion = body.get("configVersion") != null ? Integer.valueOf(body.get("configVersion").toString()) : null;
        CouncilRegulatoryConfigEntity saved = councilRegulatoryConfigService.upsertForCouncil(
                councilId,
                configVersion,
                body.get("feesJson") != null ? body.get("feesJson").toString() : null,
                body.get("cpdRulesJson") != null ? body.get("cpdRulesJson").toString() : null,
                body.get("workflowHintsJson") != null ? body.get("workflowHintsJson").toString() : null,
                body.get("documentRequirementsJson") != null ? body.get("documentRequirementsJson").toString() : null,
                body.get("workflowTemplateCode") != null ? body.get("workflowTemplateCode").toString() : null,
                body.get("notes") != null ? body.get("notes").toString() : null);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<ProviderCouncilProfileEntity>> listProfiles(@RequestParam Long providerId) {
        return ResponseEntity.ok(councilRegulationService.listProfiles(providerId));
    }

    @PostMapping("/profiles")
    public ResponseEntity<GenericResponse> upsertProfile(@RequestBody Map<String, Object> body) {
        Long providerId = Long.valueOf(body.get("providerId").toString());
        Long councilId = Long.valueOf(body.get("councilId").toString());
        ProviderCouncilProfileEntity saved = councilRegulationService.upsertProfile(
                providerId,
                councilId,
                (String) body.get("registrationCategory"),
                (String) body.get("professionalClass"),
                body.get("status") != null ? body.get("status").toString() : "ACTIVE",
                body.get("primaryCouncil") != null && Boolean.parseBoolean(body.get("primaryCouncil").toString()),
                body.get("startDate") != null ? LocalDate.parse(body.get("startDate").toString()) : null,
                body.get("endDate") != null ? LocalDate.parse(body.get("endDate").toString()) : null,
                (String) body.get("metadata"));
        return ResponseEntity.ok(GenericResponse.success("Profile saved", Map.of("id", saved.getId())));
    }

    @GetMapping("/obligations")
    public ResponseEntity<List<ProviderPaymentObligationEntity>> listObligations(@RequestParam Long providerId) {
        return ResponseEntity.ok(obligationService.listForProvider(providerId));
    }

    @PostMapping("/obligations")
    public ResponseEntity<GenericResponse> createObligation(@RequestBody Map<String, Object> body) {
        Long providerId = Long.valueOf(body.get("providerId").toString());
        Long applicationId = body.get("applicationId") != null ? Long.valueOf(body.get("applicationId").toString()) : null;
        Long councilId = body.get("councilId") != null ? Long.valueOf(body.get("councilId").toString()) : null;
        String idempotencyKey = body.get("idempotencyKey").toString();
        ProviderPaymentObligationEntity row = obligationService.createObligation(
                providerId,
                applicationId,
                councilId,
                body.get("obligationType").toString(),
                new BigDecimal(body.get("amount").toString()),
                body.get("currency") != null ? body.get("currency").toString() : "USD",
                body.get("dueDate") != null ? LocalDate.parse(body.get("dueDate").toString()) : null,
                idempotencyKey);
        return ResponseEntity.ok(GenericResponse.success("Obligation created", Map.of("obligationId", row.getId())));
    }

    @PostMapping("/obligations/{obligationId}/mushex-intent")
    public ResponseEntity<GenericResponse> createMusheXIntent(@PathVariable Long obligationId) {
        ProviderPaymentObligationEntity row = obligationService.createMusheXIntentForObligation(obligationId);
        return ResponseEntity.ok(GenericResponse.success("Intent requested",
                Map.of("obligationId", row.getId(), "mushexIntentId", row.getMushexIntentId() != null ? row.getMushexIntentId() : "")));
    }

    @PostMapping("/obligations/{obligationId}/sync-payment")
    public ResponseEntity<ProviderPaymentObligationEntity> syncPayment(@PathVariable Long obligationId) {
        return ResponseEntity.ok(obligationService.syncFromMusheX(obligationId));
    }

    @GetMapping("/applications/open")
    public ResponseEntity<List<ProviderApplicationEntity>> councilQueue(
            @RequestParam Long councilId,
            @RequestParam(required = false) String workflowStates) {
        List<String> states = workflowStates == null || workflowStates.isBlank()
                ? List.of()
                : Arrays.stream(workflowStates.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(applicationService.getApplicationsForCouncil(councilId, states));
    }

    @PostMapping("/applications/{applicationId}/under-admin-review")
    public ResponseEntity<ProviderApplicationEntity> toAdminReview(@PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationService.advanceToUnderAdminReview(applicationId));
    }

    @PostMapping("/applications/{applicationId}/awaiting-payment")
    public ResponseEntity<ProviderApplicationEntity> toAwaitingPayment(@PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationService.advanceToAwaitingPayment(applicationId));
    }

    @PostMapping("/applications/{applicationId}/fee-paid")
    public ResponseEntity<ProviderApplicationEntity> feePaid(@PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationService.advanceAfterFeePayment(applicationId));
    }

    @PostMapping("/reviews")
    public ResponseEntity<ProviderCouncilReviewEntity> recordReview(@RequestBody Map<String, Object> body) {
        Long applicationId = body.get("applicationId") != null ? Long.valueOf(body.get("applicationId").toString()) : null;
        Long councilId = body.get("councilId") != null ? Long.valueOf(body.get("councilId").toString()) : null;
        return ResponseEntity.ok(councilRegulationService.recordReview(
                applicationId,
                councilId,
                body.get("reviewType").toString(),
                body.get("decision") != null ? body.get("decision").toString() : null,
                (String) body.get("notes"),
                (String) body.get("resolutionReference")));
    }

    @GetMapping("/reviews")
    public ResponseEntity<List<ProviderCouncilReviewEntity>> listReviews(@RequestParam Long applicationId) {
        return ResponseEntity.ok(councilRegulationService.listReviews(applicationId));
    }

    @PostMapping("/fundo-links")
    public ResponseEntity<FundoLearningLinkEntity> upsertFundoLink(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(councilRegulationService.upsertFundoLink(
                Long.valueOf(body.get("providerId").toString()),
                body.get("fundoUserRef").toString(),
                body.get("linkStatus") != null ? body.get("linkStatus").toString() : "LINKED"));
    }

    @GetMapping("/fundo-cpd-candidates")
    public ResponseEntity<List<FundoCpdCandidateEntity>> listFundoCandidates(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String providerPublicId) {
        if (providerId != null) {
            return ResponseEntity.ok(fundoCpdGovernanceService.listForProvider(providerId));
        }
        if (providerPublicId != null && !providerPublicId.isBlank()) {
            return ResponseEntity.ok(fundoCpdGovernanceService.listForProviderPublicId(providerPublicId));
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/fundo-cpd-candidates/{candidateId}/accept")
    public ResponseEntity<FundoCpdCandidateEntity> acceptFundo(@PathVariable Long candidateId) {
        return ResponseEntity.ok(fundoCpdGovernanceService.acceptCandidate(candidateId));
    }

    @PostMapping("/fundo-cpd-candidates/{candidateId}/reject")
    public ResponseEntity<FundoCpdCandidateEntity> rejectFundo(
            @PathVariable Long candidateId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(fundoCpdGovernanceService.rejectCandidate(candidateId, reason));
    }
}
