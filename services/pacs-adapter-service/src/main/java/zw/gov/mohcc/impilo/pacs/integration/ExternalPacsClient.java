package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.pacs.config.DicomWebClientConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * External PACS / VNA connector over <strong>DICOMweb</strong> (QIDO-RS search + WADO-RS metadata
 * retrieve). Activated when {@code impilo.pacs.backend.provider=EXTERNAL}.
 *
 * <p>DICOMweb is UID-addressed (no opaque resource ids like Orthanc), so this connector adapts
 * DICOMweb responses into the same Orthanc-shaped JSON that {@code ImagingHierarchySyncService}
 * consumes — encoding the backend ids as UID composites ({@code studyUid|seriesUid|sopUid}) so the
 * sync flow can drill down without changing. Mapping:
 * <ul>
 *   <li>{@code getStudy} → QIDO {@code GET /studies/{study}/series} → {@code {"Series":[study|series,…]}}</li>
 *   <li>{@code getSeries} → QIDO series + {@code /instances} → {@code {"MainDicomTags":{…},"Instances":[…]}}</li>
 *   <li>{@code getInstanceSimplifiedTags} → WADO-RS {@code …/instances/{sop}/metadata} → DICOM JSON tags</li>
 * </ul>
 *
 * <p>Fail-closed: if no {@code external-base-url} is configured (or DICOMweb disabled), queries return
 * empty and retrievals throw rather than fabricate — there is no live remote PACS to invent.
 */
@Component
@ConditionalOnProperty(name = "impilo.pacs.backend.provider", havingValue = "EXTERNAL")
public class ExternalPacsClient implements PacsBackendClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalPacsClient.class);

    /** Separator for UID-composite backend ids (UIDs are digits+dots, never '|'). */
    static final String SEP = "|";

    // DICOM JSON tag keys (8 hex, no comma).
    private static final String TAG_SERIES_UID = "0020000E";
    private static final String TAG_MODALITY = "00080060";
    private static final String TAG_SERIES_NUMBER = "00200011";
    private static final String TAG_SERIES_DESCRIPTION = "0008103E";
    private static final String TAG_BODY_PART = "00180015";
    private static final String TAG_SOP_UID = "00080018";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String backendMode;
    private final String baseUrl;
    private final boolean dicomwebEnabled;
    private final boolean dimseEnabled;

    public ExternalPacsClient(
            @Qualifier("dicomwebRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.pacs.backend.mode:EMBEDDED}") String backendMode,
            @Value("${impilo.pacs.backend.external-base-url:}") String externalBaseUrl,
            @Value("${impilo.pacs.backend.external-dicomweb-enabled:true}") boolean dicomwebEnabled,
            @Value("${impilo.pacs.backend.external-dimse-enabled:false}") boolean dimseEnabled) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.backendMode = backendMode;
        String trimmed = externalBaseUrl == null ? "" : externalBaseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.baseUrl = trimmed;
        this.dicomwebEnabled = dicomwebEnabled;
        this.dimseEnabled = dimseEnabled;
    }

    @Override
    public String backendProvider() {
        return "EXTERNAL";
    }

    @Override
    public String backendMode() {
        return backendMode;
    }

    /** Configured = a DICOMweb base URL is set and the DICOMweb transport is enabled. */
    private boolean configured() {
        return dicomwebEnabled && !baseUrl.isBlank();
    }

    @Override
    public boolean supportsHierarchySync() {
        return configured();
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of(
                "provider", backendProvider(),
                "mode", backendMode(),
                "studyQuery", configured(),
                "studyRetrieve", configured(),
                "dicomweb", dicomwebEnabled,
                "dimse", dimseEnabled,
                "hierarchySync", configured());
    }

    @Override
    public Map<String, Object> systemStatus() {
        if (!configured()) {
            return Map.of(
                    "reachable", false,
                    "provider", backendProvider(),
                    "mode", backendMode(),
                    "baseUrl", baseUrl,
                    "status", "CONFIGURED_EXTERNAL_PROVIDER",
                    "note", "External PACS selected but no external-base-url / DICOMweb disabled — fail closed.");
        }
        try {
            // QIDO probe: a bounded studies query is the lightest standard reachability check.
            dicomwebGet("/studies?limit=1");
            return Map.of(
                    "reachable", true,
                    "provider", backendProvider(),
                    "mode", backendMode(),
                    "baseUrl", baseUrl,
                    "capabilities", capabilities());
        } catch (RestClientException e) {
            return Map.of(
                    "reachable", false,
                    "provider", backendProvider(),
                    "mode", backendMode(),
                    "baseUrl", baseUrl,
                    "capabilities", capabilities(),
                    "error", e.getMessage() != null ? e.getMessage() : "DICOMweb connectivity failed");
        }
    }

    @Override
    public List<String> findStudyIdsByStudyInstanceUid(String studyInstanceUid) {
        if (!configured() || studyInstanceUid == null || studyInstanceUid.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String path = UriComponentsBuilder.fromPath("/studies")
                    .queryParam("StudyInstanceUID", studyInstanceUid)
                    .queryParam("limit", 1)
                    .toUriString();
            JsonNode result = dicomwebGet(path);
            // DICOMweb is UID-addressed: the backend study id IS the StudyInstanceUID.
            if (result != null && result.isArray() && !result.isEmpty()) {
                return List.of(studyInstanceUid);
            }
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("DICOMweb study search failed for StudyInstanceUID {}: {}", studyInstanceUid, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public JsonNode getStudy(String studyInstanceUid) {
        requireConfigured();
        // QIDO: series of the study → Orthanc-shaped {"Series": [study|series, …]}.
        JsonNode series = dicomwebGet("/studies/" + enc(studyInstanceUid) + "/series");
        ArrayNode seriesIds = objectMapper.createArrayNode();
        if (series != null && series.isArray()) {
            for (JsonNode s : series) {
                String seriesUid = djFirst(s, TAG_SERIES_UID);
                if (seriesUid != null && !seriesUid.isBlank()) {
                    seriesIds.add(studyInstanceUid + SEP + seriesUid);
                }
            }
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.set("Series", seriesIds);
        return out;
    }

    @Override
    public JsonNode getSeries(String compositeSeriesId) {
        requireConfigured();
        String[] parts = split(compositeSeriesId, 2);
        String studyUid = parts[0];
        String seriesUid = parts[1];

        // Series-level metadata (QIDO filtered by SeriesInstanceUID).
        JsonNode seriesList = dicomwebGet(UriComponentsBuilder
                .fromPath("/studies/" + enc(studyUid) + "/series")
                .queryParam("SeriesInstanceUID", seriesUid)
                .toUriString());
        JsonNode seriesNode = (seriesList != null && seriesList.isArray() && !seriesList.isEmpty())
                ? seriesList.get(0) : objectMapper.createObjectNode();

        ObjectNode mainDicomTags = objectMapper.createObjectNode();
        putIfPresent(mainDicomTags, "SeriesInstanceUID", djFirst(seriesNode, TAG_SERIES_UID));
        putIfPresent(mainDicomTags, "Modality", djFirst(seriesNode, TAG_MODALITY));
        putIfPresent(mainDicomTags, "SeriesNumber", djFirst(seriesNode, TAG_SERIES_NUMBER));
        putIfPresent(mainDicomTags, "SeriesDescription", djFirst(seriesNode, TAG_SERIES_DESCRIPTION));
        putIfPresent(mainDicomTags, "BodyPartExamined", djFirst(seriesNode, TAG_BODY_PART));

        // Instance list for the series.
        ArrayNode instanceIds = objectMapper.createArrayNode();
        JsonNode instances = dicomwebGet(
                "/studies/" + enc(studyUid) + "/series/" + enc(seriesUid) + "/instances");
        if (instances != null && instances.isArray()) {
            for (JsonNode inst : instances) {
                String sop = djFirst(inst, TAG_SOP_UID);
                if (sop != null && !sop.isBlank()) {
                    instanceIds.add(studyUid + SEP + seriesUid + SEP + sop);
                }
            }
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.set("MainDicomTags", mainDicomTags);
        out.set("Instances", instanceIds);
        return out;
    }

    @Override
    public JsonNode getInstanceSimplifiedTags(String compositeInstanceId) {
        requireConfigured();
        String[] parts = split(compositeInstanceId, 3);
        String studyUid = parts[0];
        String seriesUid = parts[1];
        String sopUid = parts[2];

        // WADO-RS metadata returns full DICOM JSON tags (hex keys + Value) — the exact shape
        // OrthancTagParser already reads.
        JsonNode metadata = dicomwebGet("/studies/" + enc(studyUid)
                + "/series/" + enc(seriesUid)
                + "/instances/" + enc(sopUid) + "/metadata");
        if (metadata != null && metadata.isArray() && !metadata.isEmpty()) {
            return metadata.get(0);
        }
        if (metadata != null && metadata.isObject()) {
            return metadata;
        }
        return objectMapper.createObjectNode();
    }

    // ── DICOMweb helpers ─────────────────────────────────────────────────────

    private JsonNode dicomwebGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(DicomWebClientConfig.DICOM_JSON, MediaType.APPLICATION_JSON));
        var response = restTemplate.exchange(
                baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        return response.getBody();
    }

    /** First string value of a DICOM JSON attribute ({@code {"vr":..,"Value":[..]}}); null if absent. */
    private static String djFirst(JsonNode dicomJson, String hexTag) {
        if (dicomJson == null) {
            return null;
        }
        JsonNode attr = dicomJson.get(hexTag);
        if (attr == null) {
            return null;
        }
        JsonNode value = attr.get("Value");
        if (value == null || !value.isArray() || value.isEmpty()) {
            return null;
        }
        JsonNode first = value.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (first.isObject() && first.has("Alphabetic")) { // PersonName
            return first.get("Alphabetic").asText();
        }
        return first.asText();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException(
                    "External PACS (DICOMweb) selected but no external-base-url is configured — fail closed.");
        }
    }

    private String[] split(String composite, int expected) {
        String[] parts = composite == null ? new String[0] : composite.split("\\" + SEP, -1);
        if (parts.length != expected) {
            throw new IllegalArgumentException(
                    "Malformed DICOMweb backend id (expected " + expected + " UID parts): " + composite);
        }
        return parts;
    }

    private static String enc(String uid) {
        // DICOM UIDs are digits and dots only; pass through as a path segment.
        return uid == null ? "" : uid.trim();
    }
}
