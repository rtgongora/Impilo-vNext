package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * PACS/DICOM proxy controller — proxies DICOMweb requests from the frontend
 * to the Orthanc PACS server. This keeps Orthanc unexposed to the public
 * network while allowing authenticated clinical staff to view imaging studies.
 *
 * <p>Supported DICOMweb operations:
 * <ul>
 *   <li>QIDO-RS: Query for studies, series, instances</li>
 *   <li>WADO-RS: Retrieve study/series/instance metadata and rendered frames</li>
 *   <li>STOW-RS: Store DICOM objects (multipart/related) via DICOMweb</li>
 *   <li>C-STORE style ingest: POST raw {@code application/dicom} to Orthanc {@code /instances}</li>
 *   <li>Study list from Orthanc REST API</li>
 * </ul>
 *
 * <p>All requests require clinical role access via SecurityConfig.</p>
 */
@RestController
@RequestMapping("/internal/v1/pacs")
public class PacsController {

    private static final Logger log = LoggerFactory.getLogger(PacsController.class);

    private final RestTemplate restTemplate;
    private final String orthancBaseUrl;
    private final String dicomWebUrl;

    public PacsController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.orthanc-base-url:http://localhost:8042}") String orthancBaseUrl,
            @Value("${impilo.services.orthanc-dicomweb-url:http://localhost:8042/dicom-web}") String dicomWebUrl) {
        this.restTemplate = serviceRestTemplate;
        this.orthancBaseUrl = orthancBaseUrl;
        this.dicomWebUrl = dicomWebUrl;
    }

    // ── Orthanc REST API proxies ────────────────────────────────────

    /**
     * List all studies in Orthanc.
     */
    @GetMapping("/studies")
    public ResponseEntity<String> listStudies(@RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGet(orthancBaseUrl + "/studies");
    }

    /**
     * Get a specific study by Orthanc ID.
     */
    @GetMapping("/studies/{id}")
    public ResponseEntity<String> getStudy(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGet(orthancBaseUrl + "/studies/" + id);
    }

    /**
     * Get series within a study.
     */
    @GetMapping("/studies/{id}/series")
    public ResponseEntity<String> getStudySeries(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGet(orthancBaseUrl + "/studies/" + id + "/series");
    }

    /**
     * Get instances within a series.
     */
    @GetMapping("/series/{id}/instances")
    public ResponseEntity<String> getSeriesInstances(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGet(orthancBaseUrl + "/series/" + id + "/instances");
    }

    /**
     * Get a rendered preview of an instance (PNG).
     */
    @GetMapping("/instances/{id}/preview")
    public ResponseEntity<byte[]> getInstancePreview(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    orthancBaseUrl + "/instances/" + id + "/preview",
                    HttpMethod.GET,
                    buildOrthancRequest(),
                    byte[].class);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get instance preview: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    /**
     * Get a DICOM instance file.
     */
    /**
     * C-STORE style single-instance ingest — forwards {@code application/dicom} to Orthanc
     * {@code POST /instances} (Orthanc REST, not DIMSE).
     */
    @PostMapping(value = "/instances/dicom", consumes = "application/dicom")
    public ResponseEntity<String> postInstanceDicom(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId) throws IOException {
        return proxyPostStringBody(orthancBaseUrl + "/instances", request, MediaType.APPLICATION_JSON);
    }

    @GetMapping("/instances/{id}/file")
    public ResponseEntity<byte[]> getInstanceFile(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    orthancBaseUrl + "/instances/" + id + "/file",
                    HttpMethod.GET,
                    buildOrthancRequest(),
                    byte[].class);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get DICOM file: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    // ── DICOMweb (WADO-RS / QIDO-RS) proxies ───────────────────────

    /**
     * QIDO-RS: Search for studies.
     * Example: GET /internal/v1/pacs/dicomweb/studies?PatientID=CPID-123
     */
    @GetMapping("/dicomweb/studies")
    public ResponseEntity<String> qidoSearchStudies(
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        StringBuilder url = new StringBuilder(dicomWebUrl + "/studies");
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
        }
        return proxyGetDicomWeb(url.toString());
    }

    /**
     * WADO-RS: Retrieve study metadata.
     */
    @GetMapping("/dicomweb/studies/{studyUid}/metadata")
    public ResponseEntity<String> wadoStudyMetadata(
            @PathVariable String studyUid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGetDicomWeb(dicomWebUrl + "/studies/" + studyUid + "/metadata");
    }

    /**
     * QIDO-RS: List series for a study UID.
     */
    @GetMapping("/dicomweb/studies/{studyUid}/series")
    public ResponseEntity<String> qidoSeriesForStudy(
            @PathVariable String studyUid,
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        StringBuilder url = new StringBuilder(dicomWebUrl + "/studies/" + studyUid + "/series");
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
        }
        return proxyGetDicomWeb(url.toString());
    }

    /**
     * QIDO-RS: List instances in a series.
     */
    @GetMapping("/dicomweb/studies/{studyUid}/series/{seriesUid}/instances")
    public ResponseEntity<String> qidoInstancesForSeries(
            @PathVariable String studyUid,
            @PathVariable String seriesUid,
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        StringBuilder url = new StringBuilder(dicomWebUrl + "/studies/" + studyUid
                + "/series/" + seriesUid + "/instances");
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
        }
        return proxyGetDicomWeb(url.toString());
    }

    /**
     * WADO-RS: Retrieve series metadata.
     */
    @GetMapping("/dicomweb/studies/{studyUid}/series/{seriesUid}/metadata")
    public ResponseEntity<String> wadoSeriesMetadata(
            @PathVariable String studyUid,
            @PathVariable String seriesUid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return proxyGetDicomWeb(
                dicomWebUrl + "/studies/" + studyUid + "/series/" + seriesUid + "/metadata");
    }

    /**
     * WADO-RS: Retrieve a rendered frame (for Cornerstone.js).
     */
    @GetMapping("/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}/frames/{frame}/rendered")
    public ResponseEntity<byte[]> wadoRenderedFrame(
            @PathVariable String studyUid,
            @PathVariable String seriesUid,
            @PathVariable String instanceUid,
            @PathVariable int frame,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            String url = dicomWebUrl + "/studies/" + studyUid
                    + "/series/" + seriesUid
                    + "/instances/" + instanceUid
                    + "/frames/" + frame + "/rendered";

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, buildOrthancRequest(), byte[].class);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Failed to retrieve rendered frame: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    /**
     * WADO-RS: Retrieve a DICOM instance via DICOMweb.
     */
    /**
     * STOW-RS: store to an existing study (DICOMweb).
     */
    @PostMapping("/dicomweb/studies/{studyUid}")
    public ResponseEntity<byte[]> stowToStudy(
            HttpServletRequest request,
            @PathVariable String studyUid,
            @RequestHeader("X-Tenant-ID") String tenantId) throws IOException {
        String url = dicomWebUrl + "/studies/" + studyUid;
        return proxyPostBinaryPreserveStatus(url, request);
    }

    /**
     * STOW-RS: store instances; multipart/related or single-part body is forwarded unchanged.
     */
    @PostMapping("/dicomweb/studies")
    public ResponseEntity<byte[]> stowStudies(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId) throws IOException {
        return proxyPostBinaryPreserveStatus(dicomWebUrl + "/studies", request);
    }

    @GetMapping("/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}")
    public ResponseEntity<byte[]> wadoRetrieveInstance(
            @PathVariable String studyUid,
            @PathVariable String seriesUid,
            @PathVariable String instanceUid,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            String url = dicomWebUrl + "/studies/" + studyUid
                    + "/series/" + seriesUid
                    + "/instances/" + instanceUid;

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, buildOrthancRequest(), byte[].class);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Failed to retrieve DICOM instance: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private ResponseEntity<String> proxyGet(String url) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(jsonAcceptHeaders()),
                    String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("PACS proxy error for {}: {}", url, e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\":\"PACS service unavailable\"}");
        }
    }

    private ResponseEntity<String> proxyGetDicomWeb(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("DICOMweb proxy error for {}: {}", url, e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\":\"DICOMweb service unavailable\"}");
        }
    }

    private HttpHeaders jsonAcceptHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpEntity<Void> buildOrthancRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        return new HttpEntity<>(headers);
    }

    /**
     * Forwards POST body and selected headers to Orthanc/DICOMweb, returning upstream status and bytes
     * (e.g. {@code application/dicom+json} STOW response).
     */
    private ResponseEntity<byte[]> proxyPostBinaryPreserveStatus(String targetUrl, HttpServletRequest request)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        HttpHeaders headers = buildForwardHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(body.length == 0 ? null : body, headers);
        try {
            ResponseEntity<byte[]> response =
                    restTemplate.exchange(targetUrl, HttpMethod.POST, entity, byte[].class);
            return copyBinaryResponse(response);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(copySafeResponseHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsByteArray());
        } catch (Exception e) {
            log.error("PACS STOW proxy error for {}: {}", targetUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private ResponseEntity<String> proxyPostStringBody(
            String targetUrl, HttpServletRequest request, MediaType acceptResponse) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        HttpHeaders headers = buildForwardHeaders(request);
        headers.setAccept(List.of(acceptResponse));
        HttpEntity<byte[]> entity = new HttpEntity<>(body.length == 0 ? null : body, headers);
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(targetUrl, HttpMethod.POST, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(copySafeResponseHeaders(response.getHeaders()))
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(copySafeResponseHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("PACS C-STORE proxy error for {}: {}", targetUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"Orthanc ingest failed\"}");
        }
    }

    private static HttpHeaders buildForwardHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String ct = request.getContentType();
        if (ct != null && !ct.isBlank()) {
            headers.setContentType(MediaType.parseMediaType(ct));
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && !accept.isBlank()) {
            headers.set(HttpHeaders.ACCEPT, accept);
        }
        String transferEncoding = request.getHeader(HttpHeaders.TRANSFER_ENCODING);
        if (transferEncoding != null && !transferEncoding.isBlank()) {
            headers.set(HttpHeaders.TRANSFER_ENCODING, transferEncoding);
        }
        return headers;
    }

    private static ResponseEntity<byte[]> copyBinaryResponse(ResponseEntity<byte[]> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(copySafeResponseHeaders(response.getHeaders()))
                .body(response.getBody());
    }

    private static HttpHeaders copySafeResponseHeaders(HttpHeaders source) {
        if (source == null || source.isEmpty()) {
            return new HttpHeaders();
        }
        HttpHeaders t = new HttpHeaders();
        MediaType ct = source.getContentType();
        if (ct != null) {
            t.setContentType(ct);
        }
        long len = source.getContentLength();
        if (len > 0) {
            t.setContentLength(len);
        }
        // Preserve DICOMweb STOW correlation / warning headers when present
        for (String h : List.of("Warning", "Content-Location")) {
            List<String> vals = source.get(h);
            if (vals != null) {
                t.addAll(h, vals);
            }
        }
        String cd = source.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (cd != null) {
            t.set(HttpHeaders.CONTENT_DISPOSITION, cd);
        }
        return t;
    }
}
