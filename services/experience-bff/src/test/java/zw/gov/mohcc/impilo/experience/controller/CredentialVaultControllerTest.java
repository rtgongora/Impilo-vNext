package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CredentialServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CredentialVaultControllerTest {

    @Test
    void listCredentialsScopesToCurrentActorAsClient() {
        CredentialVaultController controller = new CredentialVaultController(new StubCredentialClient());

        ResponseEntity<String> response = controller.listMyCredentials(
                "MY",
                "ACTIVE",
                null,
                0,
                20,
                "person-123",
                "req-1",
                "corr-1"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"data\":{\"items\":[{\"credentialId\":\"cred-1\"}]}}", response.getBody());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
    }

    @Test
    void downloadPdfForwardsBinaryPayload() {
        CredentialVaultController controller = new CredentialVaultController(new StubCredentialClient());

        ResponseEntity<byte[]> response = controller.downloadCredentialPdf("cred-1", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertArrayEquals("pdf".getBytes(), response.getBody());
        assertEquals("corr-2", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    private static final class StubCredentialClient extends CredentialServiceClient {
        private StubCredentialClient() {
            super(
                    new RestTemplate(),
                    new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null
        ));
        }

        @Override
        public ResponseEntity<String> searchCredentials(MultiValueMap<String, String> queryParams) {
            assertEquals("CLIENT", queryParams.getFirst("subjectType"));
            assertEquals("person-123", queryParams.getFirst("subjectId"));
            assertEquals("ACTIVE", queryParams.getFirst("status"));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"items\":[{\"credentialId\":\"cred-1\"}]}}");
        }

        @Override
        public ResponseEntity<byte[]> getCredentialPdf(String credentialId) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body("pdf".getBytes());
        }
    }
}
