package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Honest birth-destination routing.
 *
 * <p>The tests are the three ways facility routing lies: an unknown status defaulting to safe, "we
 * don't know" collapsing into "no", and a read failure rendered as a capability verdict. Each is a
 * separate property, and each has a real estate case behind it — most facilities are unconfirmed,
 * and almost every maternity facility is unassessed for EmONC.
 */
@ExtendWith(MockitoExtension.class)
class BirthDestinationServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TusoServiceClient tuso;

    @InjectMocks
    private BirthDestinationService service;

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void stub(String statusSummary, String emonc) {
        lenient().when(tuso.getFacilityStatusSummary(1L)).thenReturn(json(statusSummary));
        lenient().when(tuso.getFacilityEmoncReadiness(1L)).thenReturn(json(emonc));
    }

    // ---------------------------------------------------------------- the operational gate fails safe

    @Test
    @DisplayName("operational=false is not a confirmed destination, whatever the EmONC verdict")
    void notOperationalIsNotADestination() {
        stub("{\"operational\": false, \"operationalVerdict\": \"NOT_OPERATIONAL\"}",
                "{\"verdict\": \"CEMONC\"}");

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        // Even a CEmONC-capable facility is not a destination if it is not operating.
        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.NOT_OPERATIONAL);
        assertThat(v.operationalGatePassed()).isFalse();
    }

    @Test
    @DisplayName("a missing operational field fails safe to NOT_OPERATIONAL, never defaults to confirmed")
    void missingOperationalFieldFailsSafe() {
        // status=ACTIVE but no operational boolean — the disclosure-badge polarity would pass this;
        // the birth-destination polarity must not.
        stub("{\"status\": \"ACTIVE\"}", "{\"verdict\": \"CEMONC\"}");

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.NOT_OPERATIONAL);
        assertThat(v.operationalGatePassed()).isFalse();
    }

    // ---------------------------------------------------------------- we-don't-know is never no

    @Test
    @DisplayName("EmONC INSUFFICIENT_EVIDENCE is CAPABILITY_UNKNOWN with call-ahead, never below-level")
    void insufficientEvidenceIsUnknownNotNo() {
        stub("{\"operational\": true}", "{\"verdict\": \"INSUFFICIENT_EVIDENCE\"}");

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.CAPABILITY_UNKNOWN);
        assertThat(v.status()).isNotEqualTo(BirthDestinationService.DestinationStatus.BELOW_REQUIRED_LEVEL);
        assertThat(v.message()).contains("Nothing is known");
        assertThat(v.guidance()).contains("not a statement that it has none");
        assertThat(v.callAhead()).isTrue();
    }

    @Test
    @DisplayName("a missing EmONC verdict is treated as unknown, not as incapable")
    void missingEmoncVerdictIsUnknown() {
        stub("{\"operational\": true}", "{}");

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.CAPABILITY_UNKNOWN);
    }

    @Test
    @DisplayName("NEITHER (assessed, cannot) IS below-level — and is distinct from unknown")
    void neitherIsBelowLevelDistinctFromUnknown() {
        stub("{\"operational\": true}", "{\"verdict\": \"NEITHER\"}");

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        // The distinction that matters: NEITHER means it was assessed and cannot; INSUFFICIENT means
        // nobody looked. They must not land in the same status.
        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.BELOW_REQUIRED_LEVEL);
    }

    // ---------------------------------------------------------------- matching

    @Test
    @DisplayName("a CEmONC facility meets a CEmONC need")
    void cemoncMeetsCemonc() {
        stub("{\"operational\": true}", "{\"verdict\": \"CEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC).status())
                .isEqualTo(BirthDestinationService.DestinationStatus.MEETS_REQUIRED_LEVEL);
    }

    @Test
    @DisplayName("a BEmONC facility is below a CEmONC need but meets a BEmONC need")
    void bemoncMeetsBemoncNotCemonc() {
        stub("{\"operational\": true}", "{\"verdict\": \"BEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC).status())
                .isEqualTo(BirthDestinationService.DestinationStatus.BELOW_REQUIRED_LEVEL);

        stub("{\"operational\": true}", "{\"verdict\": \"BEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.BEMONC).status())
                .isEqualTo(BirthDestinationService.DestinationStatus.MEETS_REQUIRED_LEVEL);
    }

    @Test
    @DisplayName("a CEmONC facility also satisfies a BEmONC need")
    void cemoncSatisfiesBemonc() {
        stub("{\"operational\": true}", "{\"verdict\": \"CEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.BEMONC).status())
                .isEqualTo(BirthDestinationService.DestinationStatus.MEETS_REQUIRED_LEVEL);
    }

    @Test
    @DisplayName("an unestablished required level is not matched against a facility")
    void unknownRequiredLevelIsNotMatched() {
        stub("{\"operational\": true}", "{\"verdict\": \"CEMONC\"}");
        var v = service.assess(1L, BirthDestinationService.RequiredLevel.UNKNOWN);
        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.REQUIRED_LEVEL_UNKNOWN);
    }

    // ---------------------------------------------------------------- read failure fails loud

    @Test
    @DisplayName("a tuso read failure is UNAVAILABLE — neither safe nor unsafe")
    void readFailureFailsLoud() {
        when(tuso.getFacilityStatusSummary(1L)).thenThrow(new RuntimeException("tuso down"));

        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);

        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.UNAVAILABLE);
        assertThat(v.message()).contains("could not be retrieved");
        assertThat(v.guidance()).contains("either a safe or an unsafe");
        assertThat(v.guidance().toLowerCase()).contains("unknown");
    }

    @Test
    @DisplayName("reads tuso's real field name — EmONC is under 'classification', not 'verdict'")
    void readsTheRealClassificationField() {
        // The live tuso EmoncReadinessService serialises the verdict as "classification"; this
        // pins that the parser reads the real field, not an assumed "verdict".
        stub("{\"operational\": true}", "{\"classification\": \"CEMONC\", \"facilityId\": 1}");
        var v = service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC);
        assertThat(v.emoncVerdict()).isEqualTo("CEMONC");
        assertThat(v.status()).isEqualTo(BirthDestinationService.DestinationStatus.MEETS_REQUIRED_LEVEL);
    }

    @Test
    @DisplayName("call-ahead is always advised, on every verdict")
    void callAheadIsAlwaysAdvised() {
        stub("{\"operational\": true}", "{\"verdict\": \"CEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC).callAhead()).isTrue();
        stub("{\"operational\": true}", "{\"verdict\": \"INSUFFICIENT_EVIDENCE\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC).callAhead()).isTrue();
        stub("{\"operational\": false}", "{\"verdict\": \"CEMONC\"}");
        assertThat(service.assess(1L, BirthDestinationService.RequiredLevel.CEMONC).callAhead()).isTrue();
    }
}
