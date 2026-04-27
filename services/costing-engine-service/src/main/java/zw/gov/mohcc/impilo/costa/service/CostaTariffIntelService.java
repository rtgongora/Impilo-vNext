package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.api.dto.finance.TariffApplicationResult;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ClaimPackStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.CostaInvoiceType;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.costa.engine.TariffCostEngine;
import zw.gov.mohcc.impilo.costa.exemptions.BillSplitResult;
import zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine;
import zw.gov.mohcc.impilo.costa.integration.MushexPaymentIntentClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CostaTariffIntelService {

    public static final String DEFAULT_DEMO_TARIFF_EXTERNAL_CODE = "ZW-PLACEHOLDER-POC-V0.1";

    private final TariffLibraryRepository tariffLibraryRepository;
    private final CostaTariffListRepository costaTariffListRepository;
    private final CostaTariffListItemRepository costaTariffListItemRepository;
    private final ChargeSheetRepository chargeSheetRepository;
    private final ChargeSheetItemRepository chargeSheetItemRepository;
    private final CostaCostEstimateRepository costaCostEstimateRepository;
    private final CostaPaymentHandoffRepository costaPaymentHandoffRepository;
    private final ExemptionEngine exemptionEngine;
    private final BillService billService;
    private final PaymentIntegrationService paymentIntegrationService;
    private final ClaimPackRepository claimPackRepository;
    private final InvoiceRepository invoiceRepository;
    private final MushexPaymentIntentClient mushexPaymentIntentClient;
    private final BillingDecisionRepository billingDecisionRepository;
    private final ObjectMapper objectMapper;

    public CostaTariffIntelService(TariffLibraryRepository tariffLibraryRepository,
                                   CostaTariffListRepository costaTariffListRepository,
                                   CostaTariffListItemRepository costaTariffListItemRepository,
                                   ChargeSheetRepository chargeSheetRepository,
                                   ChargeSheetItemRepository chargeSheetItemRepository,
                                   CostaCostEstimateRepository costaCostEstimateRepository,
                                   CostaPaymentHandoffRepository costaPaymentHandoffRepository,
                                   ExemptionEngine exemptionEngine,
                                   BillService billService,
                                   PaymentIntegrationService paymentIntegrationService,
                                   ClaimPackRepository claimPackRepository,
                                   InvoiceRepository invoiceRepository,
                                   MushexPaymentIntentClient mushexPaymentIntentClient,
                                   BillingDecisionRepository billingDecisionRepository,
                                   ObjectMapper objectMapper) {
        this.tariffLibraryRepository = tariffLibraryRepository;
        this.costaTariffListRepository = costaTariffListRepository;
        this.costaTariffListItemRepository = costaTariffListItemRepository;
        this.chargeSheetRepository = chargeSheetRepository;
        this.chargeSheetItemRepository = chargeSheetItemRepository;
        this.costaCostEstimateRepository = costaCostEstimateRepository;
        this.costaPaymentHandoffRepository = costaPaymentHandoffRepository;
        this.exemptionEngine = exemptionEngine;
        this.billService = billService;
        this.paymentIntegrationService = paymentIntegrationService;
        this.claimPackRepository = claimPackRepository;
        this.invoiceRepository = invoiceRepository;
        this.mushexPaymentIntentClient = mushexPaymentIntentClient;
        this.billingDecisionRepository = billingDecisionRepository;
        this.objectMapper = objectMapper;
    }

    public List<TariffLibraryEntity> listLibraries() {
        return tariffLibraryRepository.findAll();
    }

    public List<CostaTariffListEntity> listTariffLists(UUID tenantId) {
        return costaTariffListRepository.findVisibleForTenant(tenantId);
    }

    public CostaTariffListEntity requireTariffList(Long id, UUID tenantId) {
        return costaTariffListRepository.findByIdForTenant(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tariff list not found or not visible: " + id));
    }

    public List<CostaTariffListItemEntity> listTariffItems(Long tariffListId) {
        return costaTariffListItemRepository.findByTariffListIdOrderByItemCodeAsc(tariffListId);
    }

    public Long resolveTariffListId(JsonNode body, UUID tenantId) {
        long selected = body.path("selected_tariff_list_id").asLong(0);
        if (selected > 0) {
            return selected;
        }
        if (body.path("auto_select").asBoolean(false)) {
            return costaTariffListRepository.findFirstByExternalCodeAndTenantIdIsNull(DEFAULT_DEMO_TARIFF_EXTERNAL_CODE)
                    .map(CostaTariffListEntity::getId)
                    .orElseThrow(() -> new IllegalStateException("Default demo tariff list is not seeded"));
        }
        throw new IllegalArgumentException("Provide selected_tariff_list_id or auto_select=true");
    }

    public Map<String, Object> computeCostEstimate(JsonNode body) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        UUID facilityId = ctx.facilityId();

        Long tariffListId = resolveTariffListId(body, tenantId);
        CostaTariffListEntity list = requireTariffList(tariffListId, tenantId);
        BillingTimingMode billingTimingMode = parseBillingTimingMode(body);

        List<String> warnings = new ArrayList<>();
        if (list.isReferenceOnly()) {
            warnings.add("REFERENCE_ONLY_TARIFF: suitable for mapping, comparison, or demonstration — not an approved operational billing tariff.");
        }

        Map<String, Object> lineContext = new LinkedHashMap<>();
        lineContext.put(TariffCostEngine.CTX_COSTA_TARIFF_LIST_ID, tariffListId);
        JsonNode payer = body.path("payer_context");
        if (payer.hasNonNull("patient_category")) {
            lineContext.put("patient_category", payer.get("patient_category").asText());
        }
        JsonNode fac = body.path("facility_context");
        if (fac.hasNonNull("facility_category")) {
            lineContext.put("facility_category", fac.get("facility_category").asText());
        }

        BigDecimal totalTariff = BigDecimal.ZERO;
        List<Map<String, Object>> appliedItems = new ArrayList<>();
        Set<String> serviceCodes = new LinkedHashSet<>();

        JsonNode services = body.path("services_rendered");
        if (services.isArray()) {
            for (JsonNode s : services) {
                String code = text(s, "item_code");
                if (code == null) {
                    continue;
                }
                serviceCodes.add(code);
                BigDecimal qty = s.has("quantity") ? new BigDecimal(s.get("quantity").asText()) : BigDecimal.ONE;
                CostaTariffListItemEntity row = costaTariffListItemRepository
                        .findByTariffListIdAndItemCode(tariffListId, code)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown tariff item for list: " + code));
                BigDecimal lineTotal = row.getBasePrice().multiply(qty).setScale(2, RoundingMode.HALF_UP);
                totalTariff = totalTariff.add(lineTotal);
                appliedItems.add(Map.of(
                        "item_code", code,
                        "item_name", row.getItemName(),
                        "quantity", qty.toPlainString(),
                        "tariff_unit_price", row.getBasePrice().toPlainString(),
                        "line_tariff_total", lineTotal.toPlainString()
                ));
            }
        }

        JsonNode sheetItems = body.path("charge_sheet_items");
        List<Map<String, Object>> duplicateWarnings = new ArrayList<>();
        if (sheetItems.isArray()) {
            for (JsonNode it : sheetItems) {
                String code = text(it, "item_code");
                BigDecimal qty = it.has("quantity") ? new BigDecimal(it.get("quantity").asText()) : BigDecimal.ONE;
                if (code != null && serviceCodes.contains(code)
                        && !"duplicate_exclude_from_costing".equals(text(it, "duplicate_resolution"))) {
                    duplicateWarnings.add(Map.of(
                            "item_code", code,
                            "message", "Possible double count: item appears in services_rendered and charge_sheet_items"));
                }
                if (code == null) {
                    continue;
                }
                CostaTariffListItemEntity row = costaTariffListItemRepository
                        .findByTariffListIdAndItemCode(tariffListId, code)
                        .orElse(null);
                if (row == null) {
                    warnings.add("CHARGE_SHEET_ITEM_NOT_IN_TARIFF:" + code);
                    continue;
                }
                BigDecimal lineTotal = row.getBasePrice().multiply(qty).setScale(2, RoundingMode.HALF_UP);
                totalTariff = totalTariff.add(lineTotal);
                appliedItems.add(Map.of(
                        "item_code", code,
                        "item_name", row.getItemName(),
                        "quantity", qty.toPlainString(),
                        "source", "charge_sheet",
                        "line_tariff_total", lineTotal.toPlainString()
                ));
            }
        }

        Map<String, Object> splitCtx = new LinkedHashMap<>();
        Iterator<String> fn = body.fieldNames();
        while (fn.hasNext()) {
            String k = fn.next();
            splitCtx.put(k, objectMapper.convertValue(body.get(k), Object.class));
        }
        String exemptionCategory = body.path("patient_context").path("exemption_category").asText(null);
        Long insurancePlanId = body.path("payer_context").path("insurance_plan_id").asLong(0);
        if (insurancePlanId == 0) {
            insurancePlanId = null;
        }

        BillSplitResult split = exemptionEngine.split(tenantId, totalTariff, exemptionCategory, insurancePlanId, splitCtx);

        BigDecimal economic = totalTariff.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("direct_service_cost", totalTariff);
        breakdown.put("total_economic_cost", economic);
        breakdown.put("tariffed_price", totalTariff);
        breakdown.put("billable_amount", totalTariff);
        breakdown.put("exempted_amount", split.writeOff());
        breakdown.put("subsidised_amount", split.subsidyPayable());
        breakdown.put("payer_liability", split.insurerPayable());
        breakdown.put("patient_liability", split.patientPayable());
        breakdown.put("unrecovered_cost", economic.subtract(totalTariff).max(BigDecimal.ZERO));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total_economic_cost", economic.toPlainString());
        response.put("cost_breakdown", breakdown);
        response.put("tariffed_price", totalTariff.toPlainString());
        response.put("billable_amount", totalTariff.toPlainString());
        response.put("exempted_amount", split.writeOff().toPlainString());
        response.put("subsidised_amount", split.subsidyPayable().toPlainString());
        response.put("payer_liability", split.insurerPayable().toPlainString());
        response.put("patient_liability", split.patientPayable().toPlainString());
        response.put("provider_receivable", totalTariff.toPlainString());
        response.put("unrecovered_cost", economic.subtract(totalTariff).max(BigDecimal.ZERO).toPlainString());
        response.put("applied_tariff_items", appliedItems);
        response.put("tariff_version_used", Map.of("tariff_list_id", tariffListId, "external_code", list.getExternalCode()));
        response.put("methodology_used", List.of("TARIFF_LIBRARY_UNIT_PRICES", "EXEMPTION_ENGINE_SPLIT"));
        response.put("warnings", warnings);
        response.put("duplicate_charge_sheet_warnings", duplicateWarnings);
        response.put("billing_rules_applied", split.trace());
        TariffApplicationResult tariffApplicationResult = new TariffApplicationResult(
                tariffListId,
                list.getExternalCode(),
                billingTimingMode,
                totalTariff.toPlainString(),
                split.patientPayable().toPlainString(),
                split.insurerPayable().toPlainString(),
                split.writeOff().toPlainString(),
                split.subsidyPayable().toPlainString()
        );
        response.put("tariff_application_result", tariffApplicationResult.toMap());

        String auditRef = "CE-" + UlidGenerator.generate().substring(0, 12);
        response.put("audit_reference", auditRef);

        CostaCostEstimateEntity row = new CostaCostEstimateEntity();
        row.setTenantId(tenantId);
        row.setTariffListId(tariffListId);
        try {
            row.setRequestPayload(objectMapper.writeValueAsString(objectMapper.convertValue(body, Map.class)));
            row.setResponsePayload(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist cost estimate", e);
        }
        row.setAuditReference(auditRef);
        row.setBillingTimingMode(billingTimingMode);
        row = costaCostEstimateRepository.save(row);
        response.put("cost_estimate_id", row.getCostEstimateId().toString());

        BillingDecisionEntity decision = new BillingDecisionEntity();
        decision.setTenantId(tenantId);
        decision.setCostEstimateId(row.getCostEstimateId());
        decision.setGrossTariffedAmount(totalTariff);
        decision.setPatientLiability(split.patientPayable());
        decision.setPayerLiability(split.insurerPayable());
        decision.setExemptedAmount(split.writeOff());
        decision.setWaivedAmount(BigDecimal.ZERO);
        decision.setSubsidisedAmount(split.subsidyPayable());
        decision.setUnrecoveredAmount(economic.subtract(totalTariff).max(BigDecimal.ZERO));
        decision.setApprovalRequired(list.isReferenceOnly() || !list.isApprovedForBilling());
        decision.setAuditReference(auditRef);
        decision.setBillingTimingMode(billingTimingMode);
        try {
            ArrayNode linesJson = objectMapper.createArrayNode();
            for (Map<String, Object> line : appliedItems) {
                linesJson.add(objectMapper.valueToTree(line));
            }
            decision.setLines(linesJson.toString());
            decision.setRulesApplied(objectMapper.writeValueAsString(split.trace()));
            ArrayNode reasons = objectMapper.createArrayNode();
            for (String w : warnings) {
                reasons.add(w);
            }
            decision.setReasonCodes(reasons.toString());
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("tariff_list_id", tariffListId);
            meta.put("tariff_external_code", list.getExternalCode());
            decision.setMetadata(meta.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist billing decision snapshot", e);
        }
        decision = billingDecisionRepository.save(decision);
        response.put("billing_decision_id", decision.getBillingDecisionId().toString());

        return response;
    }

    public BillingDecisionEntity requireBillingDecision(UUID id, UUID tenantId) {
        return billingDecisionRepository.findByBillingDecisionIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Billing decision not found: " + id));
    }

    public ChargeSheetEntity createChargeSheet(JsonNode body) {
        var ctx = TrustContextHolder.require();
        ChargeSheetEntity e = new ChargeSheetEntity();
        e.setChargeSheetId(UlidGenerator.generate());
        e.setTenantId(ctx.tenantId());
        e.setPatientCpid(requiredText(body, "patient_cpid"));
        e.setEncounterId(text(body, "encounter_id"));
        if (body.hasNonNull("facility_id")) {
            e.setFacilityId(UUID.fromString(body.get("facility_id").asText()));
        } else if (ctx.facilityId() != null) {
            e.setFacilityId(ctx.facilityId());
        }
        if (body.hasNonNull("workspace_id")) {
            e.setWorkspaceId(UUID.fromString(body.get("workspace_id").asText()));
        } else {
            e.setWorkspaceId(ctx.workspaceId());
        }
        e.setCreatedBy(ctx.actorId());
        e.setNotes(text(body, "notes"));
        e = chargeSheetRepository.save(e);

        JsonNode items = body.path("items");
        if (items.isArray()) {
            for (JsonNode it : items) {
                ChargeSheetItemEntity li = new ChargeSheetItemEntity();
                li.setChargeSheetItemId(UlidGenerator.generate());
                li.setChargeSheetId(e.getChargeSheetId());
                li.setItemCategory(requiredText(it, "item_category"));
                li.setItemName(requiredText(it, "item_name"));
                li.setItemCode(text(it, "item_code"));
                li.setQuantity(it.has("quantity") ? new BigDecimal(it.get("quantity").asText()) : BigDecimal.ONE);
                li.setUnitOfMeasure(text(it, "unit_of_measure"));
                if (it.has("chargeable")) {
                    li.setChargeable(it.get("chargeable").asBoolean());
                }
                li.setClinicalNote(text(it, "clinical_note"));
                chargeSheetItemRepository.save(li);
            }
        }
        return e;
    }

    public Map<String, Object> getChargeSheet(String id) {
        var ctx = TrustContextHolder.require();
        ChargeSheetEntity sheet = chargeSheetRepository.findByChargeSheetIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Charge sheet not found"));
        List<ChargeSheetItemEntity> items = chargeSheetItemRepository.findByChargeSheetIdOrderByCreatedAtAsc(id);
        return Map.of("charge_sheet", sheet, "items", items);
    }

    public ChargeSheetEntity submitChargeSheet(String id) {
        var ctx = TrustContextHolder.require();
        ChargeSheetEntity sheet = chargeSheetRepository.findByChargeSheetIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Charge sheet not found"));
        if (!"DRAFT".equalsIgnoreCase(sheet.getStatus())) {
            throw new IllegalStateException("Charge sheet not in DRAFT");
        }
        sheet.setStatus("SUBMITTED");
        sheet.setSubmittedAt(OffsetDateTime.now());
        sheet.setSubmittedBy(ctx.actorId());
        return chargeSheetRepository.save(sheet);
    }

    public ChargeSheetEntity verifyChargeSheet(String id) {
        var ctx = TrustContextHolder.require();
        ChargeSheetEntity sheet = chargeSheetRepository.findByChargeSheetIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Charge sheet not found"));
        if (!"SUBMITTED".equalsIgnoreCase(sheet.getStatus())) {
            throw new IllegalStateException("Charge sheet must be SUBMITTED before verify");
        }
        sheet.setStatus("VERIFIED");
        sheet.setVerifiedAt(OffsetDateTime.now());
        sheet.setVerifiedBy(ctx.actorId());
        sheet = chargeSheetRepository.save(sheet);

        List<ChargeSheetItemEntity> items = chargeSheetItemRepository.findByChargeSheetIdOrderByCreatedAtAsc(id);
        for (ChargeSheetItemEntity li : items) {
            try {
                JsonNode meta = objectMapper.readTree(li.getMetadata() != null ? li.getMetadata() : "{}");
                ObjectNode n = meta.isObject() ? (ObjectNode) meta : objectMapper.createObjectNode();
                n.put("cost_event_id", UlidGenerator.generate());
                li.setMetadata(objectMapper.writeValueAsString(n));
                chargeSheetItemRepository.save(li);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to stamp cost_event_id on item " + li.getChargeSheetItemId(), e);
            }
        }
        return sheet;
    }

    public Map<String, Object> createInvoiceFromCostEstimate(JsonNode body) {
        var ctx = TrustContextHolder.require();
        UUID estimateId = UUID.fromString(body.path("cost_estimate_id").asText());
        CostaCostEstimateEntity est = costaCostEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("Cost estimate not found"));
        if (!est.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Cost estimate not visible");
        }
        CostaTariffListEntity list = requireTariffList(est.getTariffListId(), ctx.tenantId());
        assertCanBill(list, body);

        Map<String, Object> responseMap;
        try {
            responseMap = objectMapper.readValue(est.getResponsePayload(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored estimate unreadable", e);
        }

        JsonNode req;
        try {
            req = objectMapper.readTree(est.getRequestPayload());
        } catch (Exception e) {
            throw new IllegalStateException("Stored estimate request unreadable", e);
        }
        String encounterId = req.path("encounter_context").path("encounter_id").asText(null);

        Long tariffListId = est.getTariffListId();
        Map<String, Object> lineCtx = new LinkedHashMap<>();
        lineCtx.put(TariffCostEngine.CTX_COSTA_TARIFF_LIST_ID, tariffListId);

        BillHeaderEntity bill = billService.createDraft(encounterId, null, zw.gov.mohcc.impilo.costa.domain.enums.BillType.ENCOUNTER);

        JsonNode services = req.path("services_rendered");
        if (services.isArray()) {
            for (JsonNode s : services) {
                String code = text(s, "item_code");
                if (code == null) {
                    continue;
                }
                BigDecimal qty = s.has("quantity") ? new BigDecimal(s.get("quantity").asText()) : BigDecimal.ONE;
                String desc = costaTariffListItemRepository.findByTariffListIdAndItemCode(tariffListId, code)
                        .map(CostaTariffListItemEntity::getItemName)
                        .orElse(code);
                var line = billService.postLine(bill.getBillId(), code, desc, BillLineKind.SERVICE, qty,
                        CostMethodType.TARIFF, "COSTA_INTEL", est.getCostEstimateId().toString(), lineCtx);
                if (line == null) {
                    throw new IllegalStateException("Bill line excluded or not posted for code " + code);
                }
            }
        }
        JsonNode sheetItems = req.path("charge_sheet_items");
        if (sheetItems.isArray()) {
            for (JsonNode s : sheetItems) {
                String code = text(s, "item_code");
                if (code == null) {
                    continue;
                }
                BigDecimal qty = s.has("quantity") ? new BigDecimal(s.get("quantity").asText()) : BigDecimal.ONE;
                String desc = costaTariffListItemRepository.findByTariffListIdAndItemCode(tariffListId, code)
                        .map(CostaTariffListItemEntity::getItemName)
                        .orElse(code);
                var line = billService.postLine(bill.getBillId(), code, desc, BillLineKind.SERVICE, qty,
                        CostMethodType.TARIFF, "COSTA_INTEL_CHARGE_SHEET", est.getCostEstimateId().toString(), lineCtx);
                if (line == null) {
                    throw new IllegalStateException("Bill line excluded or not posted for code " + code);
                }
            }
        }

        billService.submitForApproval(bill.getBillId());
        billService.approve(bill.getBillId(), ctx.actorId(), "COSTA tariff intel auto-approve");
        billService.finalize(bill.getBillId());
        InvoiceEntity inv = paymentIntegrationService.issueInvoice(bill.getBillId());
        inv.setBillingTimingMode(parseBillingTimingMode(body));
        inv.setInvoiceType(parseInvoiceType(body));
        invoiceRepository.save(inv);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoice_id", inv.getInvoiceId());
        out.put("bill_id", bill.getBillId());
        out.put("cost_estimate_id", est.getCostEstimateId().toString());
        out.put("tariff_list_id", list.getId());
        return out;
    }

    public Map<String, Object> createClaimFromInvoice(JsonNode body) {
        var ctx = TrustContextHolder.require();
        String invoiceId = body.path("invoice_id").asText();
        InvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (!inv.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Invoice not visible");
        }
        long tlid = body.path("tariff_list_id").asLong(0);
        if (tlid <= 0) {
            throw new IllegalArgumentException("tariff_list_id is required for claim generation");
        }
        CostaTariffListEntity list = requireTariffList(tlid, ctx.tenantId());
        assertCanBill(list, body);

        ClaimPackEntity c = new ClaimPackEntity();
        c.setBillId(inv.getBillId());
        c.setBillingTimingMode(parseBillingTimingMode(body));
        c.setInsurerRef(body.path("payer_context").path("insurer_ref").asText(null));
        c.setClaimType(body.path("claim_type").asText("INITIAL"));
        c.setStatus(ClaimPackStatus.PENDING);
        try {
            c.setPayload(objectMapper.writeValueAsString(Map.of(
                    "invoice_id", inv.getInvoiceId(),
                    "invoice_number", inv.getInvoiceNumber(),
                    "total", inv.getTotal() != null ? inv.getTotal().toPlainString() : "0"
            )));
        } catch (Exception e) {
            throw new IllegalStateException("Claim payload", e);
        }
        c = claimPackRepository.save(c);
        return Map.of("claim_id", c.getId().toString(), "invoice_id", invoiceId, "bill_id", inv.getBillId());
    }

    public CostaTariffListEntity approveTariffList(Long id, JsonNode body) {
        var ctx = TrustContextHolder.require();
        CostaTariffListEntity list = requireTariffList(id, ctx.tenantId());
        list.setApprovedForBilling(true);
        if (body.path("convert_to_operational").asBoolean(false)) {
            list.setReferenceOnly(false);
        }
        return costaTariffListRepository.save(list);
    }

    public CostaTariffListEntity retireTariffList(Long id) {
        var ctx = TrustContextHolder.require();
        CostaTariffListEntity list = requireTariffList(id, ctx.tenantId());
        list.setValidationStatus("retired");
        return costaTariffListRepository.save(list);
    }

    public Map<String, Object> createPaymentHandoff(JsonNode body, HttpServletRequest request) {
        var ctx = TrustContextHolder.require();
        String invoiceId = text(body, "invoice_id");
        String claimId = text(body, "claim_id");
        Long tariffListId = body.path("tariff_list_id").asLong(0);
        CostaTariffListEntity list = tariffListId > 0
                ? requireTariffList(tariffListId, ctx.tenantId())
                : null;
        if (list != null) {
            assertCanBill(list, body);
        }

        BigDecimal amount = new BigDecimal(body.path("amount_requested").asText());
        String currency = body.path("currency").asText("USD");
        String idem = body.path("idempotency_key").asText(UlidGenerator.generate());
        String facility = body.path("facility_id").asText(ctx.facilityId() != null ? ctx.facilityId().toString() : null);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("impilo_simulation", body.path("impilo_simulation").asBoolean(true));
        meta.put("simulation_outcome", body.path("simulation_outcome").asText("authorised"));
        meta.put("provider_id", body.path("provider_id").asText(ctx.facilityId() != null ? ctx.facilityId().toString() : ""));
        if (body.hasNonNull("intent_type")) {
            meta.put("intent_type", body.get("intent_type").asText());
        }
        if (invoiceId != null) {
            meta.put("invoice_id", invoiceId);
        }
        if (claimId != null) {
            meta.put("claim_id", claimId);
        }
        String metadataJson = meta.toString();

        String sourceId = invoiceId != null ? invoiceId : (claimId != null ? claimId : "ADHOC");
        MushexPaymentIntentClient.MushexIntentCreated created = mushexPaymentIntentClient.createPaymentIntent(
                request,
                "COSTA_BILL",
                sourceId,
                amount.toPlainString(),
                currency,
                facility,
                idem,
                metadataJson
        );

        CostaPaymentHandoffEntity h = new CostaPaymentHandoffEntity();
        h.setTenantId(ctx.tenantId());
        h.setBillingTimingMode(parseBillingTimingMode(body));
        h.setInvoiceId(invoiceId);
        h.setClaimId(claimId);
        h.setMushexIntentId(created.intentId());
        h.setAmountRequested(amount);
        h.setCurrency(currency);
        h.setStatus(created.status() != null ? created.status() : "CREATED");
        try {
            h.setMetadata(objectMapper.writeValueAsString(Map.of(
                    "payer_type", body.path("payer_type").asText("patient"),
                    "payment_channel", body.path("payment_channel").asText("simulated"),
                    "settlement_mode", body.path("settlement_mode").asText("immediate_payment")
            )));
        } catch (Exception e) {
            throw new IllegalStateException("Handoff metadata", e);
        }
        h = costaPaymentHandoffRepository.save(h);
        return Map.of(
                "payment_handoff_id", h.getPaymentHandoffId().toString(),
                "mushex_intent_id", created.intentId(),
                "status", h.getStatus()
        );
    }

    private BillingTimingMode parseBillingTimingMode(JsonNode body) {
        if (body == null || !body.hasNonNull("billing_timing_mode")) {
            return null;
        }
        try {
            return BillingTimingMode.valueOf(body.get("billing_timing_mode").asText().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CostaInvoiceType parseInvoiceType(JsonNode body) {
        if (body == null || !body.hasNonNull("invoice_type")) {
            return null;
        }
        try {
            return CostaInvoiceType.valueOf(body.get("invoice_type").asText().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void assertCanBill(CostaTariffListEntity list, JsonNode body) {
        if (body.path("force_operational_billing").asBoolean(false)
                && "TARIFF_APPROVER".equalsIgnoreCase(TrustContextHolder.require().actorType())) {
            return;
        }
        if (list.isReferenceOnly() || !list.isApprovedForBilling()) {
            throw new IllegalStateException(
                    "Operational billing blocked: tariff list is reference-only or not approved for billing.");
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field)) {
            return null;
        }
        String v = n.get(field).asText();
        return v.isBlank() ? null : v;
    }

    private static String requiredText(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return v;
    }
}
