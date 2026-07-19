package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A5: fetches a captured proofing artifact (selfie / document portrait / liveness
 * capture) by its document-service object reference so a proofing engine can score
 * the real pixels. Behind an interface so the facial-comparison engine is unit-testable
 * without HTTP.
 */
public interface ProofingObjectFetcher {

    /** @return the object bytes, or {@code null} if it can't be fetched (caller fails closed). */
    byte[] fetch(String objectRef);

    @Component
    class RestClientFetcher implements ProofingObjectFetcher {

        private static final Logger log = LoggerFactory.getLogger(RestClientFetcher.class);

        private final RestClient restClient;
        private final String documentBaseUrl;

        public RestClientFetcher(
                @Value("${DOCUMENT_SERVICE_URL:${vito.proofing.document-service.base-url:http://localhost:8093}}")
                String documentBaseUrl) {
            this.documentBaseUrl = trimSlash(documentBaseUrl);
            this.restClient = RestClient.create();
        }

        @Override
        public byte[] fetch(String objectRef) {
            if (objectRef == null || objectRef.isBlank()) {
                return null;
            }
            try {
                return restClient.get()
                        .uri(documentBaseUrl + "/v1/internal/objects/" + objectRef + "/download")
                        .retrieve()
                        .body(byte[].class);
            } catch (RuntimeException e) {
                log.warn("Proofing object fetch failed for ref (fail-closed): {}", e.getMessage());
                return null;
            }
        }

        private static String trimSlash(String s) {
            return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
        }
    }
}
