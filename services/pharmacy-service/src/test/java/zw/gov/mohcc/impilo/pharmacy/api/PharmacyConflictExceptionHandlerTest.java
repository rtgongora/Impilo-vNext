package zw.gov.mohcc.impilo.pharmacy.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 F-500 — pharmacy fail-closed refusals must surface as coded 409s in the
 * standard envelope, never bodyless raw 500s (same defect family as the OF-A
 * M2 inventory fix). The refusal semantics themselves are untouched — this is
 * a transport-contract test over the real refusal messages the engine throws.
 */
class PharmacyConflictExceptionHandlerTest {

    private final PharmacyConflictExceptionHandler handler = new PharmacyConflictExceptionHandler();

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', value = {
            "CLARIFICATION_PENDING          | Dispense order 42 holds: a prescriber clarification is pending",
            "INVALID_DISPENSE_TRANSITION    | Invalid dispense transition from DISPENSED to DISPENSED for order 42",
            "PICKUP_PROOF_EXPIRED           | Pickup proof has expired",
            "COLLECTOR_IDENTITY_MISMATCH    | Collector identity does not match the named recipient — collection rejected",
            "SMS_REFERENCE_PATH_REQUIRED    | This pickup requires the SMS-reference collection path with recipient identity check",
            "BIOMETRIC_VERIFICATION_FAILED  | Biometric collector verification failed — collection rejected",
            "STATE_CONFLICT                 | Something else entirely",
    })
    @DisplayName("real engine refusal messages map to their coded 409")
    void refusalsMapToCoded409(String expectedCode, String message) {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleStateConflict(new IllegalStateException(message.trim()));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(expectedCode.trim());
        assertThat(response.getBody().error().message()).isEqualTo(message.trim());
    }

    @Test
    @DisplayName("null message still yields a coded envelope, never an empty body")
    void nullMessageStillCoded() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleStateConflict(new IllegalStateException((String) null));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().code()).isEqualTo("STATE_CONFLICT");
    }
}
