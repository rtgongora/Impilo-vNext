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
import zw.gov.mohcc.impilo.experience.client.CredentialServiceClient;

@RestController
@RequestMapping("/internal/v1/credentials")
public class CredentialVaultController {

    private static final Logger log = LoggerFactory.getLogger(CredentialVaultController.class);

    private final CredentialServiceClient credentialClient;

    public CredentialVaultController(CredentialServiceClient credentialClient) {
        this.credentialClient = credentialClient;
    }

    @GetMapping
    public ResponseEntity<String> listMyCredentials(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String credentialType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("subjectType", "CLIENT");
        queryParams.add("subjectId", actorId);
        if (status != null && !status.isBlank()) queryParams.add("status", status);
        if (credentialType != null && !credentialType.isBlank()) queryParams.add("credentialType", credentialType);
        if (page != null) queryParams.add("page", String.valueOf(page));
        if (size != null) queryParams.add("size", String.valueOf(size));

        return forward("list credentials", requestId, correlationId, () -> credentialClient.searchCredentials(queryParams));
    }

    @GetMapping("/{credentialId}/pdf")
    public ResponseEntity<byte[]> downloadCredentialPdf(
            @PathVariable String credentialId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return withBinaryHeaders(credentialClient.getCredentialPdf(credentialId), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Credential PDF download failed credentialId={} status={}", credentialId, exception.getStatusCode());
            return ResponseEntity.status(exception.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .build();
        }
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Credential vault {} failed status={}", action, exception.getStatusCode());
            return ResponseEntity.status(exception.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(exception.getResponseBodyAsString());
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

    private ResponseEntity<byte[]> withBinaryHeaders(ResponseEntity<byte[]> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .headers(upstream.getHeaders())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_PDF)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
