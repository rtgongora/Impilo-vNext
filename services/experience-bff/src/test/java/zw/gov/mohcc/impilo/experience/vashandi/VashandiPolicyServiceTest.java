package zw.gov.mohcc.impilo.experience.vashandi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VashandiPolicyServiceTest {

    private final VashandiPolicyService policyService = new VashandiPolicyService();

    @Test
    void deniesAssignmentCreateWithoutVisibleWorkspace() {
        Map<String, Object> contract = Map.of(
                "visibleVashandiWorkspaces", List.of("vashandi.my_roster"),
                "blockedVashandiWorkspaces", List.of("vashandi.assignments")
        );
        VashandiDtos.PrecheckRequest request = new VashandiDtos.PrecheckRequest(
                "ASSIGNMENT_CREATE",
                "/work/vashandi/assignments",
                null,
                null,
                "FAC-1",
                "vashandi_facility_workforce_manager",
                null,
                Map.of()
        );
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        assertFalse(decision.allowed());
        assertEquals("impilo.vashandi", decision.policyPackage());
    }

    @Test
    void allowsFacilityManagerAssignmentCreate() {
        Map<String, Object> contract = Map.of(
                "visibleVashandiWorkspaces", List.of(
                        "vashandi.dashboard",
                        "vashandi.assignments",
                        "vashandi.facility_staff"
                )
        );
        VashandiDtos.PrecheckRequest request = new VashandiDtos.PrecheckRequest(
                "ASSIGNMENT_CREATE",
                "/work/vashandi/assignments",
                null,
                null,
                "FAC-1",
                "vashandi_facility_workforce_manager",
                null,
                Map.of()
        );
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        assertTrue(decision.allowed());
        assertNotNull(decision.decisionId());
    }

    @Test
    void allowsVirtualPoolReadWithRosterAuthority() {
        Map<String, Object> contract = Map.of(
                "visibleVashandiWorkspaces", List.of("vashandi.rosters", "vashandi.facility_staff")
        );
        VashandiDtos.PrecheckRequest request = new VashandiDtos.PrecheckRequest(
                "VIRTUAL_POOL_READ",
                "/work/vashandi/on-call",
                null,
                "trauma-oncall:FAC-1",
                "FAC-1",
                "vashandi_facility_workforce_manager",
                null,
                Map.of()
        );
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        assertTrue(decision.allowed());
    }

    @Test
    void deniesVirtualPoolReadWithoutRosterAuthority() {
        Map<String, Object> contract = Map.of(
                "visibleVashandiWorkspaces", List.of("vashandi.my_attendance")
        );
        VashandiDtos.PrecheckRequest request = new VashandiDtos.PrecheckRequest(
                "VIRTUAL_POOL_READ",
                "/work/vashandi/on-call",
                null,
                "trauma-oncall:FAC-1",
                null,
                null,
                null,
                Map.of()
        );
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        assertFalse(decision.allowed());
    }

    @Test
    void deniesCheckInWhenProviderSuspended() {
        Map<String, Object> contract = Map.of(
                "visibleVashandiWorkspaces", List.of("vashandi.my_attendance"),
                "providerWorkerStatus", "suspended"
        );
        VashandiDtos.PrecheckRequest request = new VashandiDtos.PrecheckRequest(
                "ATTENDANCE_CHECK_IN",
                "/work/vashandi/attendance",
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        assertFalse(decision.allowed());
    }
}
