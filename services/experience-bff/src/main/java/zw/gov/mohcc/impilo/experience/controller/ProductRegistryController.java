package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

/**
 * Canonical Experience entry points for the MSIKA product and service registry.
 */
@RestController
@RequestMapping("/internal/v1/product-registry")
public class ProductRegistryController {

    private static final Logger log = LoggerFactory.getLogger(ProductRegistryController.class);

    private final MsikaServiceClient msikaClient;

    public ProductRegistryController(MsikaServiceClient msikaClient) {
        this.msikaClient = msikaClient;
    }

    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = msikaClient.search(copy(queryParams));
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Product registry search failed status={}", exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<String> getItem(
            @PathVariable String itemId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = msikaClient.getItem(itemId);
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Product registry item fetch failed itemId={} status={}", itemId, exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    @GetMapping("/catalogs/{catalogId}/items")
    public ResponseEntity<String> listCatalogItems(
            @PathVariable String catalogId,
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = msikaClient.listCatalogItems(catalogId, copy(queryParams));
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Catalog item listing failed catalogId={} status={}", catalogId, exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    private MultiValueMap<String, String> copy(MultiValueMap<String, String> source) {
        return new LinkedMultiValueMap<>(source);
    }

    private ResponseEntity<String> forward(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> forward(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null
                ? upstream.getResponseHeaders().getContentType()
                : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }
}
