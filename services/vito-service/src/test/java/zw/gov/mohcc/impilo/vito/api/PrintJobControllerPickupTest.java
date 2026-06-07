package zw.gov.mohcc.impilo.vito.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.core.issuance.IssuanceStateMachineService;
import zw.gov.mohcc.impilo.vito.core.pickup.DelegatedPickupService;
import zw.gov.mohcc.impilo.vito.persistence.entity.DelegatedPickupEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.IssuanceRequestEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.IssuanceRequestRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PrintJobControllerPickupTest {

    @Mock private SmartCardRepository cardRepo;
    @Mock private EventOutboxRepository outboxRepo;
    @Mock private DelegatedPickupService pickupService;
    @Mock private IssuanceRequestRepository issuanceRepo;
    @Mock private IssuanceStateMachineService issuanceService;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrintJobController controller = new PrintJobController(
                cardRepo, outboxRepo, pickupService, issuanceRepo, issuanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "staff-1",
                "STAFF",
                "FACILITY_OPERATIONS",
                null,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void verifyPickupReturnsDelegateDetails() throws Exception {
        UUID facilityId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        when(pickupService.verifyPickup("token-1", "123456"))
                .thenReturn(new DelegatedPickupService.PickupVerification(
                        "Jane Delegate", facilityId, "Harare Central Hospital", 42L, expiresAt));

        IssuanceRequestEntity issuance = new IssuanceRequestEntity();
        issuance.setId(42L);
        issuance.setTenantId(tenantId);
        issuance.setCardId(99L);
        when(issuanceRepo.findById(42L)).thenReturn(Optional.of(issuance));

        mockMvc.perform(post("/v1/print/card/verify-pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupToken":"token-1","delegateOtp":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.delegateName").value("Jane Delegate"))
                .andExpect(jsonPath("$.facilityName").value("Harare Central Hospital"))
                .andExpect(jsonPath("$.cardId").value("99"));
    }

    @Test
    void confirmHandoverRedeemsAndDelivers() throws Exception {
        DelegatedPickupEntity pickup = new DelegatedPickupEntity();
        pickup.setIssuanceRequestId(42L);
        when(pickupService.redeem(eq("token-1"), eq("123456"), eq("staff-1"))).thenReturn(pickup);

        IssuanceRequestEntity issuance = new IssuanceRequestEntity();
        issuance.setId(42L);
        issuance.setTenantId(tenantId);
        issuance.setCardId(99L);
        when(issuanceRepo.findById(42L)).thenReturn(Optional.of(issuance));
        when(issuanceService.deliver(eq(tenantId), eq(42L))).thenReturn(issuance);

        mockMvc.perform(post("/v1/print/card/confirm-handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupToken":"token-1","delegateOtp":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.cardId").value("99"));
    }
}
