package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicalEpisodesHonestyTest {

    @Test
    void failedUpstreamIsBadGatewayNotEmptyList() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.listMedicalEpisodes(anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("pct down"));
        MedicalEpisodesController controller = new MedicalEpisodesController(pct);

        ResponseEntity<Map<String, Object>> response =
                controller.list("req-1", "corr-1", "CPID-1", false);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("medical_episodes_unavailable", response.getBody().get("error"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("Do not treat"));
    }
}
