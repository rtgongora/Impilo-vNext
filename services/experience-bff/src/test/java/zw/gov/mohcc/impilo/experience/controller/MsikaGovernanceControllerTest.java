package zw.gov.mohcc.impilo.experience.controller;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MsikaGovernanceControllerTest {

    @Test
    void listCatalogsForwardsCatalogPayload() {
        MsikaGovernanceController controller = new MsikaGovernanceController(new StubMsikaClient());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("status", "DRAFT");

        ResponseEntity<String> response = controller.listCatalogs(queryParams, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"items\":[{\"catalogId\":\"cat-1\",\"status\":\"DRAFT\"}]}", response.getBody());
    }

    @Test
    void importCsvForwardsMultipartPayload() throws IOException {
        MsikaGovernanceController controller = new MsikaGovernanceController(new StubMsikaClient());
        MockMultipartFile file = new MockMultipartFile("file", "catalog.csv", "text/csv", "code,name".getBytes());

        ResponseEntity<String> response = controller.importCsv("cat-1", file, "req-2", "corr-2");

        assertEquals(202, response.getStatusCode().value());
        assertEquals("corr-2", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"jobId\":\"import-1\",\"catalogId\":\"cat-1\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubMsikaClient extends MsikaServiceClient {
        private StubMsikaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listCatalogs(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"catalogId\":\"cat-1\",\"status\":\"" + queryParams.getFirst("status") + "\"}]}");
        }

        @Override
        public ResponseEntity<String> importCsv(String catalogId, byte[] fileBytes, String filename) {
            return ResponseEntity.accepted()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"jobId\":\"import-1\",\"catalogId\":\"" + catalogId + "\"}");
        }
    }
}
