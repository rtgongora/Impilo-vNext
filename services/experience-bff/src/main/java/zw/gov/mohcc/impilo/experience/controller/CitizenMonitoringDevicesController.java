package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Explicit contract handlers for citizen monitoring devices.
 *
 * <p>Forwards to {@code wellness-service} while preserving trust headers for matrix discovery.</p>
 */
@RestController
public class CitizenMonitoringDevicesController {

    private static final List<String> HOP_BY_HOP = List.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host");

    private final RestTemplate restTemplate;
    private final String wellnessBaseUrl;

    public CitizenMonitoringDevicesController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.wellness-base-url:http://localhost:8161}") String wellnessBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        String b = wellnessBaseUrl.trim();
        this.wellnessBaseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    @GetMapping("/internal/v1/mobile/citizen/monitoring/devices")
    public ResponseEntity<byte[]> listDevices(HttpServletRequest request) {
        return forward(request, null);
    }

    @PostMapping("/internal/v1/mobile/citizen/monitoring/devices")
    public ResponseEntity<byte[]> pairDevice(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return forward(request, body);
    }

    @PostMapping("/internal/v1/mobile/citizen/monitoring/devices/{id}/sync")
    public ResponseEntity<byte[]> syncDevice(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody(required = false) byte[] body) {
        return forward(request, body);
    }

    @PostMapping("/internal/v1/mobile/citizen/monitoring/readings")
    public ResponseEntity<byte[]> ingestReadings(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return forward(request, body);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) {
        if (body == null && request.getContentLengthLong() > 0) {
            try {
                body = StreamUtils.copyToByteArray(request.getInputStream());
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(new byte[0]);
            }
        }
        String q = request.getQueryString();
        URI target = URI.create(wellnessBaseUrl + request.getRequestURI()
                + (q != null && !q.isBlank() ? "?" + q : ""));

        HttpHeaders out = copyHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, out);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, entity, byte[].class);
            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.putAll(resp.getHeaders());
            stripHopByHop(respHeaders);
            return ResponseEntity.status(resp.getStatusCode()).headers(respHeaders).body(resp.getBody());
        } catch (HttpStatusCodeException ex) {
            HttpHeaders respHeaders = new HttpHeaders();
            if (ex.getResponseHeaders() != null) {
                respHeaders.putAll(ex.getResponseHeaders());
            }
            stripHopByHop(respHeaders);
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(respHeaders)
                    .body(ex.getResponseBodyAsByteArray());
        }
    }

    private static HttpHeaders copyHeaders(HttpServletRequest request) {
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
        return out;
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
