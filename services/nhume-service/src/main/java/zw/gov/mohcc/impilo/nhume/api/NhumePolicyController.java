package zw.gov.mohcc.impilo.nhume.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryIntegrationProviderEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPolicyEntity;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryIntegrationProviderRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryPolicyRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class NhumePolicyController {

    private final DeliveryPolicyRepository policyRepo;
    private final DeliveryIntegrationProviderRepository integrationRepo;
    private final TrustLayerGuard trust;

    public NhumePolicyController(DeliveryPolicyRepository policyRepo,
                                  DeliveryIntegrationProviderRepository integrationRepo,
                                  TrustLayerGuard trust) {
        this.policyRepo = policyRepo;
        this.integrationRepo = integrationRepo;
        this.trust = trust;
    }

    @GetMapping({"/api/v1/nhume/policies", "/internal/v1/nhume/policies"})
    public ResponseEntity<?> list() {
        RequestContext ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", policyRepo.findByTenantIdAndActiveTrue(UUID.fromString(ctx.tenantId()))
                .stream().map(this::toPolicy).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping({"/api/v1/nhume/policies", "/internal/v1/nhume/policies"})
    public ResponseEntity<?> create(@RequestBody PolicyPayload p, HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "policy.create");
        trust.requirePurposeOfUse(actor, "policy.create");
        DeliveryPolicyEntity e = new DeliveryPolicyEntity();
        e.setTenantId(UUID.fromString(ctx.tenantId()));
        applyPolicy(e, p);
        policyRepo.save(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPolicy(e));
    }

    @PutMapping({"/api/v1/nhume/policies/{policyId}",
            "/internal/v1/nhume/policies/{policyId}"})
    public ResponseEntity<?> update(@PathVariable String policyId,
                                     @RequestBody PolicyPayload p,
                                     HttpServletRequest http) {
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "policy.update");
        return policyRepo.findById(policyId).<ResponseEntity<?>>map(e -> {
            applyPolicy(e, p);
            e.setUpdatedAt(OffsetDateTime.now());
            policyRepo.save(e);
            return ResponseEntity.ok(toPolicy(e));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping({"/api/v1/nhume/integrations", "/internal/v1/nhume/integrations"})
    public ResponseEntity<?> integrations() {
        RequestContext ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", integrationRepo.findByTenantIdAndActiveTrue(UUID.fromString(ctx.tenantId()))
                .stream().map(this::toIntegration).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping({"/api/v1/nhume/integrations", "/internal/v1/nhume/integrations"})
    public ResponseEntity<?> createIntegration(@RequestBody IntegrationPayload p,
                                                HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        TrustLayerGuard.ActorContext actor = trust.capture(http);
        trust.requireActor(actor, "integration.create");
        trust.requirePurposeOfUse(actor, "integration.create");
        DeliveryIntegrationProviderEntity e = new DeliveryIntegrationProviderEntity();
        e.setProviderCode(p.provider_code);
        e.setTenantId(UUID.fromString(ctx.tenantId()));
        e.setDisplayName(p.display_name);
        e.setProviderKind(p.provider_kind);
        e.setBaseUrl(p.base_url);
        if (p.auth_kind != null) e.setAuthKind(p.auth_kind);
        e.setAuthRef(p.auth_ref);
        e.setWebhookSecret(p.webhook_secret);
        if (p.simulation_mode != null) e.setSimulationMode(p.simulation_mode);
        integrationRepo.save(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(toIntegration(e));
    }

    private void applyPolicy(DeliveryPolicyEntity e, PolicyPayload p) {
        if (p.policy_id != null) e.setPolicyId(p.policy_id);
        if (p.delivery_type != null) e.setDeliveryType(p.delivery_type);
        if (p.display_name != null) e.setDisplayName(p.display_name);
        if (p.description != null) e.setDescription(p.description);
        if (p.requires_approval != null) e.setRequiresApproval(p.requires_approval);
        if (p.requires_payment != null) e.setRequiresPayment(p.requires_payment);
        if (p.requires_stock_check != null) e.setRequiresStockCheck(p.requires_stock_check);
        if (p.requires_identity_verify != null) e.setRequiresIdentityVerify(p.requires_identity_verify);
        if (p.requires_consent != null) e.setRequiresConsent(p.requires_consent);
        if (p.requires_chain_of_custody != null) e.setRequiresChainOfCustody(p.requires_chain_of_custody);
        if (p.cold_chain_required != null) e.setColdChainRequired(p.cold_chain_required);
        if (p.notification_set != null) e.setNotificationSet(p.notification_set);
        if (p.sla_target_minutes != null) e.setSlaTargetMinutes(p.sla_target_minutes);
        if (p.escalation_rule != null) e.setEscalationRule(p.escalation_rule);
        if (p.active != null) e.setActive(p.active);
    }

    private Map<String, Object> toPolicy(DeliveryPolicyEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policy_id", p.getPolicyId());
        m.put("delivery_type", p.getDeliveryType());
        m.put("display_name", p.getDisplayName());
        m.put("description", p.getDescription());
        m.put("requires_approval", p.isRequiresApproval());
        m.put("requires_payment", p.isRequiresPayment());
        m.put("requires_stock_check", p.isRequiresStockCheck());
        m.put("requires_identity_verify", p.getRequiresIdentityVerify());
        m.put("requires_consent", p.isRequiresConsent());
        m.put("requires_chain_of_custody", p.isRequiresChainOfCustody());
        m.put("cold_chain_required", p.isColdChainRequired());
        m.put("proof_methods_required", p.getProofMethodsRequired());
        m.put("allowed_modes", p.getAllowedModes());
        m.put("allowed_courier_kinds", p.getAllowedCourierKinds());
        m.put("notification_set", p.getNotificationSet());
        m.put("sla_target_minutes", p.getSlaTargetMinutes());
        m.put("escalation_rule", p.getEscalationRule());
        m.put("active", p.isActive());
        m.put("is_demo", p.isDemo());
        return m;
    }

    private Map<String, Object> toIntegration(DeliveryIntegrationProviderEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider_code", i.getProviderCode());
        m.put("display_name", i.getDisplayName());
        m.put("provider_kind", i.getProviderKind());
        m.put("base_url", i.getBaseUrl());
        m.put("auth_kind", i.getAuthKind());
        m.put("simulation_mode", i.isSimulationMode());
        m.put("active", i.isActive());
        return m;
    }

    public static class PolicyPayload {
        public String policy_id;
        public String delivery_type;
        public String display_name;
        public String description;
        public Boolean requires_approval;
        public Boolean requires_payment;
        public Boolean requires_stock_check;
        public String requires_identity_verify;
        public Boolean requires_consent;
        public Boolean requires_chain_of_custody;
        public Boolean cold_chain_required;
        public String notification_set;
        public Integer sla_target_minutes;
        public String escalation_rule;
        public Boolean active;
    }

    public static class IntegrationPayload {
        public String provider_code;
        public String display_name;
        public String provider_kind;
        public String base_url;
        public String auth_kind;
        public String auth_ref;
        public String webhook_secret;
        public Boolean simulation_mode;
    }
}
