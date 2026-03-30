package zw.gov.mohcc.impilo.fhirgateway.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.fhirgateway.config.FhirGatewayProperties;
import zw.gov.mohcc.impilo.fhirgateway.interceptor.PiiPreventionInterceptor;

import java.util.Enumeration;
import java.util.Set;

@RestController
@RequestMapping("/fhir")
public class FhirGatewayController {

    private static final Logger log = LoggerFactory.getLogger(FhirGatewayController.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "te",
            "upgrade", "proxy-authenticate", "proxy-authorization", "trailers"
    );

    private final RestTemplate restTemplate;
    private final PiiPreventionInterceptor piiInterceptor;
    private final FhirGatewayProperties properties;

    public FhirGatewayController(RestTemplate restTemplate,
                                  PiiPreventionInterceptor piiInterceptor,
                                  FhirGatewayProperties properties) {
        this.restTemplate = restTemplate;
        this.piiInterceptor = piiInterceptor;
        this.properties = properties;
    }

    @RequestMapping("/**")
    public ResponseEntity<String> proxyFhirRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            piiInterceptor.validatePayload(body);
        }

        String fhirSubPath = requestUri.replaceFirst("/fhir", "");
        String backendUrl = properties.getBackendUrl() + "/fhir"
                + (fhirSubPath.isEmpty() ? "" : fhirSubPath)
                + (queryString != null ? "?" + queryString : "");

        log.debug("Proxying {} {} -> {}", method, requestUri, backendUrl);

        HttpHeaders headers = buildForwardHeaders(request);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(backendUrl, HttpMethod.valueOf(method), entity, String.class);
        } catch (HttpStatusCodeException ex) {
            log.warn("Backend returned {} for {} {}", ex.getStatusCode(), method, backendUrl);
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders())
                    .body(ex.getResponseBodyAsString());
        }
    }

    private HttpHeaders buildForwardHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    headers.set(name, request.getHeader(name));
                }
            }
        }
        return headers;
    }
}
