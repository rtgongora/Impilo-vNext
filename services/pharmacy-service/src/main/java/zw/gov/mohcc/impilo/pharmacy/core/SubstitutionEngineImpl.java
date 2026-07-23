package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.FormularyEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.SubstitutionRuleEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.FormularyRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.SubstitutionRuleRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

/**
 * Evaluates and applies drug substitution rules during dispensing.
 *
 * <p>Checks substitution rules at two levels: facility-specific rules first,
 * then tenant-wide rules. Returns available alternatives sorted by rule
 * priority and indicates whether step-up authorization is required.</p>
 *
 * <p>Substitution types include generic (brand-to-generic), therapeutic
 * (same therapeutic class), formulary (formulary-driven), and dose-form
 * (e.g. tablet to suspension).</p>
 */
@Service
public class SubstitutionEngineImpl implements SubstitutionEngine {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionEngineImpl.class);

    private final SubstitutionRuleRepository ruleRepository;
    private final DispenseItemRepository itemRepository;
    private final FormularyRepository formularyRepository;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository clarificationRepository;

    public SubstitutionEngineImpl(SubstitutionRuleRepository ruleRepository,
                                  DispenseItemRepository itemRepository,
                                  FormularyRepository formularyRepository,
                                  ObjectMapper objectMapper,
                                  zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository clarificationRepository) {
        this.ruleRepository = ruleRepository;
        this.itemRepository = itemRepository;
        this.formularyRepository = formularyRepository;
        this.objectMapper = objectMapper;
        this.clarificationRepository = clarificationRepository;
    }

    @Override
    public SubstitutionResult evaluateSubstitution(UUID itemId, String targetDrugCode) {
        TrustContext ctx = TrustContextHolder.require();

        DispenseItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        String sourceDrugCode = item.getDrugCode();

        // Find applicable rules: facility-specific first, then tenant-wide
        List<SubstitutionRuleEntity> rules = findApplicableRules(
                ctx.tenantId(), ctx.facilityId(), sourceDrugCode);

        // Check if the target code matches any enabled rule
        Optional<SubstitutionRuleEntity> matchingRule = rules.stream()
                .filter(r -> r.getTargetCode().equals(targetDrugCode))
                .findFirst();

        if (matchingRule.isEmpty()) {
            return new SubstitutionResult(false, false,
                    "No substitution rule found for " + sourceDrugCode + " -> " + targetDrugCode);
        }

        SubstitutionRuleEntity rule = matchingRule.get();
        return new SubstitutionResult(
                true,
                rule.isRequiresStepUp(),
                "Substitution allowed via " + rule.getRuleType() + " rule");
    }

    @Override
    public List<SubstitutionOption> getSubstitutionOptions(UUID itemId) {
        TrustContext ctx = TrustContextHolder.require();

        DispenseItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        List<SubstitutionRuleEntity> rules = findApplicableRules(
                ctx.tenantId(), ctx.facilityId(), item.getDrugCode());

        List<SubstitutionOption> options = new ArrayList<>();
        for (SubstitutionRuleEntity rule : rules) {
            // Look up the target drug display name from formulary
            String targetDisplay = resolveDisplay(ctx.tenantId(), ctx.facilityId(), rule.getTargetCode());

            options.add(new SubstitutionOption(
                    rule.getTargetCode(),
                    targetDisplay,
                    rule.getRuleType().name(),
                    "Rule: " + rule.getRuleType() + " substitution",
                    rule.isRequiresStepUp()));
        }

        log.debug("Substitution options for item {}: {} options found", itemId, options.size());
        return options;
    }

    @Override
    @Transactional
    public DispenseItemEntity applySubstitution(UUID itemId, String targetDrugCode, String reason) {
        TrustContext ctx = TrustContextHolder.require();

        DispenseItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        // Validate substitution is allowed
        SubstitutionResult result = evaluateSubstitution(itemId, targetDrugCode);
        if (!result.allowed()) {
            throw new IllegalStateException("Substitution not allowed: " + result.reason());
        }

        // OF-B15 fail-closed approval gate (§8.10.1): a substitution requiring
        // step-up may only apply once a prescriber clarification has been
        // APPROVED for exactly this item+target — never unilaterally. The
        // approval is consumed exactly once (applied_at).
        zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity approval = null;
        if (result.requiresStepUp()) {
            approval = clarificationRepository
                    .findFirstByDispenseItemIdAndTargetDrugCodeAndStatusAndAppliedAtIsNull(
                            itemId, targetDrugCode,
                            zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus.APPROVED)
                    .orElseThrow(() -> new IllegalStateException(
                            "Substitution requires prescriber clarification: no approved clarification "
                                    + "exists for item " + itemId + " -> " + targetDrugCode));
        }

        // Build substitution JSON record
        String substitutionJson;
        try {
            Map<String, Object> subData = new LinkedHashMap<>();
            subData.put("originalCode", item.getDrugCode());
            subData.put("originalDisplay", item.getDrugDisplay());
            subData.put("substituteCode", targetDrugCode);
            subData.put("substituteDisplay",
                    resolveDisplay(ctx.tenantId(), ctx.facilityId(), targetDrugCode));
            subData.put("reason", reason);
            subData.put("appliedBy", ctx.actorId());
            if (approval != null) {
                subData.put("clarificationId", approval.getClarificationId().toString());
                subData.put("approvedBy", approval.getResolvedBy());
                subData.put("patientConsent", approval.isPatientConsent());
            }
            substitutionJson = objectMapper.writeValueAsString(subData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize substitution data", e);
        }

        if (approval != null) {
            approval.setAppliedAt(java.time.OffsetDateTime.now());
            clarificationRepository.save(approval);
        }

        item.setDrugCode(targetDrugCode);
        item.setDrugDisplay(resolveDisplay(ctx.tenantId(), ctx.facilityId(), targetDrugCode));
        item.setSubstitution(substitutionJson);
        item.setStatus(DispenseItemStatus.SUBSTITUTED);
        item = itemRepository.save(item);

        log.info("Substitution applied: itemId={}, {} -> {}, reason={}",
                itemId, item.getDrugCode(), targetDrugCode, reason);
        return item;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Find applicable substitution rules: facility-specific first, then tenant-wide.
     */
    private List<SubstitutionRuleEntity> findApplicableRules(UUID tenantId, UUID facilityId,
                                                              String sourceCode) {
        // Facility-specific rules take precedence
        List<SubstitutionRuleEntity> facilityRules =
                ruleRepository.findByTenantIdAndFacilityIdAndSourceCodeAndEnabledTrue(
                        tenantId, facilityId, sourceCode);
        if (!facilityRules.isEmpty()) {
            return facilityRules;
        }

        // Fall back to tenant-wide rules
        return ruleRepository.findByTenantIdAndSourceCodeAndEnabledTrue(tenantId, sourceCode);
    }

    /**
     * Resolve a drug display name from the formulary. Returns the code if not found.
     */
    private String resolveDisplay(UUID tenantId, UUID facilityId, String drugCode) {
        // Try facility-level first
        Optional<FormularyEntity> entry =
                formularyRepository.findByTenantIdAndFacilityIdAndDrugCode(tenantId, facilityId, drugCode);
        if (entry.isPresent() && entry.get().getDrugDisplay() != null) {
            return entry.get().getDrugDisplay();
        }

        // Try tenant-level
        entry = formularyRepository.findByTenantIdAndFacilityIdIsNullAndDrugCode(tenantId, drugCode);
        if (entry.isPresent() && entry.get().getDrugDisplay() != null) {
            return entry.get().getDrugDisplay();
        }

        return drugCode;
    }
}
