package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

/**
 * Proxies COSTA {@code /api/costa/...} through the enterprise plane (finance domain) for Experience UI and tools.
 */
@RestController
@RequestMapping("/internal/v1/finance/costa-intel")
public class CostaIntelBffController {

    private static final Logger log = LoggerFactory.getLogger(CostaIntelBffController.class);

    private static final String PREFIX = "/internal/v1/finance/costa-intel";

    private final CostaServiceClient costaClient;
    private final FinancePlaneAuthorizationService financePlaneAuthorizationService;

    public CostaIntelBffController(CostaServiceClient costaClient,
                                   FinancePlaneAuthorizationService financePlaneAuthorizationService) {
        this.costaClient = costaClient;
        this.financePlaneAuthorizationService = financePlaneAuthorizationService;
    }

    @RequestMapping(
            value = {"/**", ""},
            method = {
                    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE
            })
    public ResponseEntity<String> proxy(HttpServletRequest request,
                                         @RequestBody(required = false) String body,
                                         @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                         @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertCostaIntelAccess(request.getMethod());
        String ctx = request.getContextPath() != null ? request.getContextPath() : "";
        String uri = request.getRequestURI();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        if (!path.startsWith(PREFIX)) {
            log.warn("Unexpected path for costa-intel proxy: {}", path);
        }
        String tail = path.length() > PREFIX.length() ? path.substring(PREFIX.length()) : "";
        if (tail.isEmpty()) {
            tail = "/";
        }
        if (!tail.startsWith("/")) {
            tail = "/" + tail;
        }
        String upstreamPath = "/api/costa" + tail;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            upstreamPath = upstreamPath + "?" + query;
        }
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        try {
            ResponseEntity<String> upstream = costaClient.costaIntelExchange(method, upstreamPath, body);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Costa intel proxy failed status={}", exception.getStatusCode());
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
}
