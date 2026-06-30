package zw.gov.mohcc.impilo.guidance.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.guidance.core.ContextGuidanceService.GuidanceContext;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceDismissalEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceItemEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GuidanceDismissalRepository;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GuidanceItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextGuidanceServiceTest {

    @Mock private GuidanceItemRepository itemRepo;
    @Mock private GuidanceDismissalRepository dismissalRepo;

    private ContextGuidanceService service() {
        return new ContextGuidanceService(itemRepo, dismissalRepo);
    }

    private GuidanceItemEntity item(String key, String type, String userType, String route,
                                    Integer minLoa, String trigger, int priority, boolean auditReq) {
        GuidanceItemEntity i = new GuidanceItemEntity();
        i.setGuidanceKey(key);
        i.setDomain("wallet");
        i.setGuidanceType(type);
        i.setAppliesUserType(userType);
        i.setAppliesRoute(route);
        i.setMinTrustLoa(minLoa);
        i.setTriggerCondition(trigger);
        i.setTitle("Title " + key);
        i.setBody("Body " + key);
        i.setPriority(priority);
        i.setAuditRequired(auditReq);
        return i;
    }

    private GuidanceContext ctx(String userType, int loa, String route, Set<String> signals) {
        return new GuidanceContext("t1", "CPID-1", userType, null, loa, route, "SELF", signals);
    }

    @Test
    void only_items_whose_trigger_signal_fired_are_returned() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(
                item("UPGRADE_TRUST", "LOCKED_STATE", "CITIZEN", "/citizen/wallet", null, "trust.not_verified", 10, true),
                item("SETTLE_BILL", "NEXT_BEST_ACTION", "CITIZEN", "/citizen/wallet", null, "payments.outstanding", 30, false)));
        when(dismissalRepo.findByTenantIdAndActorId("t1", "CPID-1")).thenReturn(List.of());

        // Only the trust signal fired — the bill card must NOT show (no fabricated actions).
        List<Map<String, Object>> out = service().resolve(
                ctx("CITIZEN", 1, "/citizen/wallet", Set.of("trust.not_verified")));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("key")).isEqualTo("UPGRADE_TRUST");
        assertThat(out.get(0).get("auditRequired")).isEqualTo(true);
    }

    @Test
    void provider_guidance_is_not_shown_to_a_citizen() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(
                item("PROVIDER_CHECK_IN", "LOCKED_STATE", "PROVIDER", "/work", null, "provider.not_checked_in", 10, true)));
        when(dismissalRepo.findByTenantIdAndActorId(anyString(), anyString())).thenReturn(List.of());

        List<Map<String, Object>> out = service().resolve(
                ctx("CITIZEN", 3, "/work", Set.of("provider.not_checked_in")));

        assertThat(out).isEmpty();
    }

    @Test
    void route_prefix_gates_applicability() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(
                item("COMPLETE_PROFILE", "NEXT_BEST_ACTION", "CITIZEN", "/citizen/wallet", null, "profile.incomplete", 40, false)));
        when(dismissalRepo.findByTenantIdAndActorId(anyString(), anyString())).thenReturn(List.of());

        // On an unrelated route, the wallet card does not apply even though the signal is present.
        assertThat(service().resolve(ctx("CITIZEN", 2, "/marketplace", Set.of("profile.incomplete")))).isEmpty();
        // On the wallet route, it applies.
        assertThat(service().resolve(ctx("CITIZEN", 2, "/citizen/wallet", Set.of("profile.incomplete")))).hasSize(1);
    }

    @Test
    void low_trust_user_gets_safe_limited_set_high_trust_gets_more() {
        GuidanceItemEntity detail = item("DETAIL", "NEXT_BEST_ACTION", "CITIZEN", "/citizen/wallet", 3, "always", 50, false);
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(detail));
        when(dismissalRepo.findByTenantIdAndActorId(anyString(), anyString())).thenReturn(List.of());

        // Below the minimum LoA: the higher-trust item is withheld.
        assertThat(service().resolve(ctx("CITIZEN", 1, "/citizen/wallet", Set.of()))).isEmpty();
        // At/above the minimum LoA: it is offered.
        assertThat(service().resolve(ctx("CITIZEN", 3, "/citizen/wallet", Set.of()))).hasSize(1);
    }

    @Test
    void dismissed_guidance_is_filtered_out() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(
                item("WALLET_WALKTHROUGH", "ORIENTATION", "CITIZEN", "/citizen/wallet", null, "always", 60, false)));
        GuidanceDismissalEntity d = new GuidanceDismissalEntity();
        d.setGuidanceKey("WALLET_WALKTHROUGH");
        when(dismissalRepo.findByTenantIdAndActorId("t1", "CPID-1")).thenReturn(List.of(d));

        assertThat(service().resolve(ctx("CITIZEN", 2, "/citizen/wallet", Set.of()))).isEmpty();
    }

    @Test
    void explain_locked_maps_trust_reason_to_catalogue_item_without_leaking_data() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of(
                item("UPGRADE_TRUST", "LOCKED_STATE", "CITIZEN", "/citizen/wallet", null, "trust.not_verified", 10, true)));

        Map<String, Object> out = service().explainLocked("t1", "TRUST_TOO_LOW",
                ctx("CITIZEN", 1, "/citizen/wallet", Set.of()));

        assertThat(out.get("title")).isEqualTo("Title UPGRADE_TRUST");
        assertThat(out.get("ctaRoute")).isEqualTo(null);  // unset in this fixture, but mapping matched
        assertThat(out.get("guidanceKey")).isEqualTo("UPGRADE_TRUST");
        assertThat(String.valueOf(out.get("safetyBoundary"))).contains("does not reveal");
    }

    @Test
    void explain_locked_sensitive_reason_defaults_safely_and_flags_audit() {
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("t1", "ACTIVE")).thenReturn(List.of());
        when(itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("national-spine", "ACTIVE")).thenReturn(List.of());

        Map<String, Object> out = service().explainLocked("t1", "REQUIRES_CONSENT",
                ctx("GUARDIAN", 3, "/citizen/wallet", Set.of()));

        // Safe default explanation, audit required, and NO hidden data echoed.
        assertThat(out.get("auditRequired")).isEqualTo(true);
        assertThat(String.valueOf(out.get("explanation"))).doesNotContain("CPID");
        assertThat(String.valueOf(out.get("explanation"))).contains("consent");
    }

    @Test
    void dismiss_is_idempotent() {
        when(dismissalRepo.existsByTenantIdAndActorIdAndGuidanceKey("t1", "CPID-1", "K")).thenReturn(true);
        service().dismiss("t1", "CPID-1", "K");
        verify(dismissalRepo, never()).save(any());

        when(dismissalRepo.existsByTenantIdAndActorIdAndGuidanceKey("t1", "CPID-1", "K2")).thenReturn(false);
        service().dismiss("t1", "CPID-1", "K2");
        verify(dismissalRepo).save(any());
    }
}
