package zw.gov.mohcc.impilo.experience.controller;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

@RestController
@RequestMapping("/internal/v1/msika")
public class MsikaGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(MsikaGovernanceController.class);

    private final MsikaServiceClient msikaClient;

    public MsikaGovernanceController(MsikaServiceClient msikaClient) {
        this.msikaClient = msikaClient;
    }

    @GetMapping("/catalogs")
    public ResponseEntity<String> listCatalogs(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list catalogs", requestId, correlationId,
                () -> msikaClient.listCatalogs(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/catalogs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createCatalog(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create catalog", requestId, correlationId, () -> msikaClient.createCatalog(body));
    }

    @PostMapping("/catalogs/{catalogId}/submit-review")
    public ResponseEntity<String> submitCatalogReview(
            @PathVariable String catalogId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("submit catalog review", requestId, correlationId, () -> msikaClient.submitCatalogReview(catalogId));
    }

    @PostMapping("/catalogs/{catalogId}/approve")
    public ResponseEntity<String> approveCatalog(
            @PathVariable String catalogId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("approve catalog", requestId, correlationId, () -> msikaClient.approveCatalog(catalogId));
    }

    @PostMapping("/catalogs/{catalogId}/publish")
    public ResponseEntity<String> publishCatalog(
            @PathVariable String catalogId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("publish catalog", requestId, correlationId, () -> msikaClient.publishCatalog(catalogId));
    }

    @GetMapping("/mappings/pending")
    public ResponseEntity<String> listPendingMappings(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list pending mappings", requestId, correlationId,
                () -> msikaClient.listPendingMappings(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping("/mappings/{mappingId}/approve")
    public ResponseEntity<String> approveMapping(
            @PathVariable String mappingId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("approve mapping", requestId, correlationId, () -> msikaClient.approveMapping(mappingId));
    }

    @PostMapping("/mappings/{mappingId}/reject")
    public ResponseEntity<String> rejectMapping(
            @PathVariable String mappingId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("reject mapping", requestId, correlationId, () -> msikaClient.rejectMapping(mappingId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importCsv(
            @RequestParam String catalogId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return withHeaders(msikaClient.importCsv(
                    catalogId,
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "catalog.csv"), requestId, correlationId);
        } catch (IOException exception) {
            log.warn("MSIKA import file read failed catalogId={}", catalogId, exception);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body("{\"error\":\"IMPORT_FILE_READ_FAILED\"}");
        } catch (HttpStatusCodeException exception) {
            log.warn("MSIKA import failed status={}", exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("MSIKA governance {} failed status={}", action, exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null
                ? upstream.getResponseHeaders().getContentType()
                : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
