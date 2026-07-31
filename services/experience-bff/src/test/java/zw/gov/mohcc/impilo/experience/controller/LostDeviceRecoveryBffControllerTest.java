package zw.gov.mohcc.impilo.experience.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.security.RecoveryExecutionService;

class LostDeviceRecoveryBffControllerTest {
    private final TshepoIdentityServiceClient identity = mock(TshepoIdentityServiceClient.class);
    private final RecoveryExecutionService executor = mock(RecoveryExecutionService.class);
    private final LostDeviceRecoveryBffController controller =
            new LostDeviceRecoveryBffController(identity, executor);

    @Test
    void aalTwoCannotCreatePrivilegedRecoveryCase() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create("tenant", jwt("urn:impilo:aal2"), Map.of()));

        assertEquals("STEP_UP_AAL3_REQUIRED", error.getReason());
        verifyNoInteractions(identity, executor);
    }

    @Test
    void aalTwoCannotApproveOrExecuteRecoveryCase() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.approve("tenant", jwt("urn:impilo:aal2"), UUID.randomUUID(),
                        Map.of("approved", true)));

        assertEquals("STEP_UP_AAL3_REQUIRED", error.getReason());
        verifyNoInteractions(identity, executor);
    }

    private static Jwt jwt(String acr) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "none").subject("admin-1")
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("acr", acr).build();
    }
}
