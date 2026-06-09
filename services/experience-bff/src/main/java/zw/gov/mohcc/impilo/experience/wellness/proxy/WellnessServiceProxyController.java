package zw.gov.mohcc.impilo.experience.wellness.proxy;

import jakarta.servlet.http.HttpServletRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Forwards all wellness and citizen My Life traffic to {@code simba-service} (product: Simba).
 */
@RestController
public class WellnessServiceProxyController {

    private static final List<String> HOP_BY_HOP = List.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host");

    private final RestTemplate restTemplate;
    private final String simbaBaseUrl;
    private final Counter legacyCitizenWalletRouteCounter;

    public WellnessServiceProxyController(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${impilo.services.simba-base-url:http://localhost:8125}") String simbaBaseUrl) {
        this.restTemplate = restTemplate;
        this.simbaBaseUrl = trimTrailingSlash(simbaBaseUrl);
        this.legacyCitizenWalletRouteCounter = Counter.builder("impilo.legacy.route.requests")
                .description("Inbound requests still using legacy wallet proxy routes")
                .tag("route_family", "mobile_citizen_wallet")
                .register(meterRegistry);
    }

    @RequestMapping(
            value = {
                    "/internal/v1/wellness/**",
                    "/internal/v1/mobile/citizen/health-id",
                    "/internal/v1/mobile/citizen/wellness/**",
                    "/internal/v1/mobile/citizen/wallet/**",
                    "/internal/v1/mobile/citizen/sos/**",
                    "/internal/v1/mobile/citizen/queue/**",
                    "/internal/v1/mobile/citizen/clubs/**",
                    "/internal/v1/mobile/citizen/providers/**",
                    "/internal/v1/mobile/citizen/crowdfunding/**",
                    "/internal/v1/mobile/citizen/monitoring/**",
                    "/internal/v1/mobile/citizen/services/discover"
            },
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.PATCH,
                    RequestMethod.DELETE
            })
    public ResponseEntity<byte[]> forward(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        if (body == null && request.getContentLengthLong() > 0) {
            try {
                body = StreamUtils.copyToByteArray(request.getInputStream());
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(new byte[0]);
            }
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/internal/v1/mobile/citizen/wallet")) {
            legacyCitizenWalletRouteCounter.increment();
        }
        String q = request.getQueryString();
        URI target = URI.create(simbaBaseUrl + uri + (q != null && !q.isBlank() ? "?" + q : ""));

        HttpHeaders out = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null) {
                continue;
            }
            String ln = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(ln) || "content-length".equals(ln)) {
                continue;
            }
            Enumeration<String> vals = request.getHeaders(name);
            while (vals.hasMoreElements()) {
                String v = vals.nextElement();
                if (v != null) {
                    out.add(name, v);
                }
            }
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, out);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, entity, byte[].class);
            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.putAll(resp.getHeaders());
            stripHopByHop(respHeaders);
            return ResponseEntity.status(resp.getStatusCode())
                    .headers(respHeaders)
                    .body(resp.getBody());
        } catch (HttpStatusCodeException ex) {
            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.putAll(ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders());
            stripHopByHop(respHeaders);
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(respHeaders)
                    .body(ex.getResponseBodyAsByteArray());
        }
    }

    private static String trimTrailingSlash(String base) {
        String b = base.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private static void stripHopByHop(HttpHeaders h) {
        if (h == null || h.isEmpty()) {
            return;
        }
        for (String k : HOP_BY_HOP) {
            h.remove(k);
        }
        h.remove(HttpHeaders.TRANSFER_ENCODING);
    }
}
