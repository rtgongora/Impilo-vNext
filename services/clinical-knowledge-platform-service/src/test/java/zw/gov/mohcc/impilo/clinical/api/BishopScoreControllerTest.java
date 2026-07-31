package zw.gov.mohcc.impilo.clinical.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.clinical.maternal.BishopScoreService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BishopScoreControllerTest {

    private final BishopScoreController controller = new BishopScoreController(new BishopScoreService());

    @Test
    @DisplayName("assess returns wrapped data with interpretation")
    void assessReturnsWrappedData() throws Exception {
        var response = controller.assess(new BishopScoreController.AssessRequest(
                "DIL_5_PLUS", "EFF_80_PLUS", "STATION_MINUS2_PLUS", "SOFT", "ANTERIOR"));

        Map<String, Object> data = data(response);
        assertThat(data.get("score")).isEqualTo(13);
        assertThat(data.get("interpretation")).isEqualTo("FAVOURABLE");
        assertThat(data.get("content_version")).isEqualTo("ENGINEERING_SEED");
    }

    @Test
    @DisplayName("missing component returns INCOMPLETE with missing list")
    void incompleteWhenBlank() {
        var response = controller.assess(new BishopScoreController.AssessRequest(
                "CLOSED", null, "+2", "FIRM", "POSTERIOR"));

        Map<String, Object> data = data(response);
        assertThat(data.get("score")).isNull();
        assertThat(data.get("interpretation")).isEqualTo("INCOMPLETE");
        assertThat(data.get("missing")).isEqualTo(List.of("effacement"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }
}
