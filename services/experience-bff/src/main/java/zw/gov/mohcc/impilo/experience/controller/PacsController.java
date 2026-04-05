package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

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
            @Value("${impilo.services.orthanc-base-url:http://localhost:8042}") String orthancBaseUrl,
            @Value("${impilo.services.orthanc-dicomweb-url:http://localhost:8042/dicom-web}") String dicomWebUrl) {
        this.restTemplate = new RestTemplate();
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
}
