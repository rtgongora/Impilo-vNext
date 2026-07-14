package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Composition tests for citizen fundraisers: server-side wallet binding on create,
 * donor-wallet resolution + status gate on donate, and honest failure states.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CitizenCrowdfundingControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private DaidzaiServiceClient daidzai;
    @Mock private MusheWalletServiceClient mushe;

    private CitizenCrowdfundingController controller;

    private static final UUID WALLET = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new CitizenCrowdfundingController(daidzai, mushe);
        ObjectNode wallet = MAPPER.createObjectNode();
        wallet.put("id", WALLET.toString());
        when(mushe.getWalletByOwner(eq("PERSON"), anyString())).thenReturn(wallet);
    }

    private ObjectNode assistance(String status, String shareToken) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", UUID.randomUUID().toString());
        node.put("verificationStatus", status);
        node.put("shareToken", shareToken);
        node.put("attestationPublicToken", "https://verify/tok");
        return node;
    }

    @Test
    void create_binds_beneficiary_wallet_to_caller_and_strips_lifecycle() {
        when(daidzai.createAssistance(anyMap())).thenAnswer(inv -> {
            Map<String, Object> body = inv.getArgument(0);
            assertThat(body.get("beneficiaryWalletId")).isEqualTo(WALLET.toString());
            assertThat(body).doesNotContainKey("verificationStatus");
            return MAPPER.createObjectNode().put("id", "created");
        });

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Help with surgery");
        body.put("targetAmount", "2000");
        body.put("verificationStatus", "ACTIVE"); // hostile client value — must be stripped
        ResponseEntity<Object> response = controller.create("citizen-1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(daidzai).createAssistance(anyMap());
    }

    @Test
    void donate_resolves_donor_wallet_and_forwards_idempotency() {
        when(daidzai.getAssistance("case-1")).thenReturn(assistance("ACTIVE", "tok-1"));
        when(mushe.contributeToBill(eq("tok-1"), anyMap())).thenAnswer(inv -> {
            Map<String, Object> body = inv.getArgument(1);
            assertThat(body.get("contributorWalletId")).isEqualTo(WALLET.toString());
            assertThat(body.get("contributorRef")).isEqualTo("donor-1");
            assertThat(body.get("idempotencyKey")).isEqualTo("idem-1");
            return MAPPER.createObjectNode().put("recorded", true);
        });

        ResponseEntity<Object> response = controller.donate("donor-1", "idem-1", "case-1",
                Map.of("amount", "50"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(mushe).contributeToBill(eq("tok-1"), anyMap());
    }

    @Test
    void donate_to_unverified_case_is_conflict_and_never_reaches_money() {
        when(daidzai.getAssistance("case-2")).thenReturn(assistance("PENDING_VERIFICATION", "tok-2"));

        ResponseEntity<Object> response = controller.donate("donor-1", null, "case-2",
                Map.of("amount", "50"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(mushe, never()).contributeToBill(anyString(), anyMap());
    }

    @Test
    void get_composes_case_money_share_and_verification() {
        when(daidzai.getAssistance("case-3")).thenReturn(assistance("ACTIVE", "tok-3"));
        when(mushe.getBillContribution("tok-3"))
                .thenReturn(MAPPER.createObjectNode().put("raisedAmount", "100"));

        ResponseEntity<Object> response = controller.get("case-3");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> composed = (Map<String, Object>) response.getBody();
        assertThat(composed).containsKeys("case", "money", "shareUrl", "independentVerificationUrl");
        assertThat(composed.get("shareUrl")).isEqualTo("/share/fund/tok-3");
    }

    @Test
    void get_survives_money_view_outage_with_honest_flag() {
        when(daidzai.getAssistance("case-4")).thenReturn(assistance("ACTIVE", "tok-4"));
        when(mushe.getBillContribution("tok-4")).thenThrow(new RuntimeException("mushe down"));

        ResponseEntity<Object> response = controller.get("case-4");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> composed = (Map<String, Object>) response.getBody();
        assertThat(composed.get("moneyUnavailable")).isEqualTo(true);
    }

    @Test
    void create_without_resolvable_wallet_fails_closed() {
        when(mushe.getWalletByOwner(eq("PERSON"), anyString())).thenReturn(null);

        ResponseEntity<Object> response = controller.create("citizen-1", Map.of("title", "t"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        verify(daidzai, never()).createAssistance(any());
    }
}
