package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the ZIBO terminology and definitions sovereign service.
 */
@Component
public class ZiboServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ZiboServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ZiboServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ziboBaseUrl();
    }

    public JsonNode createArtifact(Map<String, Object> request) {
        String url = baseUrl + "/v1/artifacts";
        log.info("ZIBO: createArtifact operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateArtifact(UUID id, Map<String, Object> request) {
        String url = baseUrl + "/v1/artifacts/" + id;
        log.info("ZIBO: updateArtifact operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode publishArtifact(UUID id) {
        String url = baseUrl + "/v1/artifacts/" + id + "/publish";
        log.info("ZIBO: publishArtifact operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode deprecateArtifact(UUID id) {
        String url = baseUrl + "/v1/artifacts/" + id + "/deprecate";
        log.info("ZIBO: deprecateArtifact operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode retireArtifact(UUID id) {
        String url = baseUrl + "/v1/artifacts/" + id + "/retire";
        log.info("ZIBO: retireArtifact operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getArtifact(UUID id) {
        String url = baseUrl + "/v1/artifacts/" + id;
        log.info("ZIBO: getArtifact operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listArtifacts(String fhirType, String status, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/artifacts")
                .queryParam("page", page)
                .queryParam("size", size);
        if (fhirType != null && !fhirType.isBlank()) {
            b.queryParam("fhirType", fhirType);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: listArtifacts operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listArtifactVersions(String canonicalUrl) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/artifacts/versions")
                .queryParam("canonicalUrl", canonicalUrl)
                .build()
                .encode()
                .toUriString();
        log.info("ZIBO: listArtifactVersions operation [canonicalUrl={}]", canonicalUrl);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode resolveArtifactByCanonical(String canonicalUrl, String version) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/artifacts/resolve")
                .queryParam("canonicalUrl", canonicalUrl)
                .queryParam("version", version)
                .build()
                .encode()
                .toUriString();
        log.info("ZIBO: resolveArtifactByCanonical operation [canonicalUrl={}, version={}]", canonicalUrl, version);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode assignPack(Map<String, Object> request) {
        String url = baseUrl + "/v1/assignments";
        log.info("ZIBO: assignPack operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode unassignPack(UUID id) {
        String url = baseUrl + "/v1/assignments/" + id;
        log.info("ZIBO: unassignPack operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAssignmentsForScope(String scopeType, UUID scopeId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/assignments")
                .queryParam("scopeType", scopeType)
                .queryParam("scopeId", scopeId)
                .build()
                .encode()
                .toUriString();
        log.info("ZIBO: getAssignmentsForScope operation [scopeType={}, scopeId={}]", scopeType, scopeId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getGovernanceValidationLogs(String severity, UUID facilityId, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/governance/validation-logs")
                .queryParam("page", page)
                .queryParam("size", size);
        if (severity != null && !severity.isBlank()) {
            b.queryParam("severity", severity);
        }
        if (facilityId != null) {
            b.queryParam("facilityId", facilityId);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: getGovernanceValidationLogs operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getGovernanceTopFailures(int topN, String since) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/governance/top-failures")
                .queryParam("topN", topN);
        if (since != null && !since.isBlank()) {
            b.queryParam("since", since);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: getGovernanceTopFailures operation [topN={}]", topN);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getGovernanceValidationStats(UUID facilityId, String since) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/governance/stats");
        if (facilityId != null) {
            b.queryParam("facilityId", facilityId);
        }
        if (since != null && !since.isBlank()) {
            b.queryParam("since", since);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: getGovernanceValidationStats operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode importFhirBundle(String bundleJson) {
        String url = baseUrl + "/v1/import/fhir-bundle";
        log.info("ZIBO: importFhirBundle operation");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(bundleJson, headers), JsonNode.class);
        return extractData(response);
    }

    public JsonNode importCodeSystemFromCsv(byte[] csvBytes, String filename, String systemUrl, String name,
                                            String version) {
        String url = baseUrl + "/v1/import/csv";
        log.info("ZIBO: importCodeSystemFromCsv operation [systemUrl={}, name={}, version={}]", systemUrl, name, version);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csvBytes) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "import.csv";
            }
        });
        body.add("systemUrl", systemUrl);
        body.add("name", name);
        body.add("version", version);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
        return extractData(response);
    }

    public JsonNode exportPackBundle(UUID packId) {
        String url = baseUrl + "/v1/export/packs/" + packId + "/bundle";
        log.info("ZIBO: exportPackBundle operation [packId={}]", packId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode exportArtifactJson(UUID artifactId) {
        String url = baseUrl + "/v1/export/artifacts/" + artifactId;
        log.info("ZIBO: exportArtifactJson operation [artifactId={}]", artifactId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode mapCode(Map<String, Object> request) {
        String url = baseUrl + "/v1/map";
        log.info("ZIBO: mapCode operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAvailableMappingSources(String sourceSystem) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/map/sources");
        if (sourceSystem != null && !sourceSystem.isBlank()) {
            b.queryParam("sourceSystem", sourceSystem);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: getAvailableMappingSources operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode rebuildMappingIndexes() {
        String url = baseUrl + "/v1/map/rebuild";
        log.info("ZIBO: rebuildMappingIndexes operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createPack(Map<String, Object> request) {
        String url = baseUrl + "/v1/packs";
        log.info("ZIBO: createPack operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode addArtifactToPack(UUID packId, Map<String, Object> request) {
        String url = baseUrl + "/v1/packs/" + packId + "/artifacts";
        log.info("ZIBO: addArtifactToPack operation [packId={}]", packId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode removeArtifactFromPack(UUID packId, UUID artifactId) {
        String url = baseUrl + "/v1/packs/" + packId + "/artifacts/" + artifactId;
        log.info("ZIBO: removeArtifactFromPack operation [packId={}, artifactId={}]", packId, artifactId);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode publishPack(UUID id) {
        String url = baseUrl + "/v1/packs/" + id + "/publish";
        log.info("ZIBO: publishPack operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode deprecatePack(UUID id) {
        String url = baseUrl + "/v1/packs/" + id + "/deprecate";
        log.info("ZIBO: deprecatePack operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getPack(UUID id) {
        String url = baseUrl + "/v1/packs/" + id;
        log.info("ZIBO: getPack operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getPackArtifacts(UUID id) {
        String url = baseUrl + "/v1/packs/" + id + "/artifacts";
        log.info("ZIBO: getPackArtifacts operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPacks(String status, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/packs")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.build().encode().toUriString();
        log.info("ZIBO: listPacks operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getArtifactSnapshot(int cursor, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/snapshots/artifacts")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUriString();
        log.info("ZIBO: getArtifactSnapshot operation [cursor={}, limit={}]", cursor, limit);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emitArtifactSnapshot() {
        String url = baseUrl + "/internal/v1/snapshots/artifacts/emit";
        log.info("ZIBO: emitArtifactSnapshot operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode validateCoding(Map<String, Object> request) {
        String url = baseUrl + "/v1/validate/coding";
        log.info("ZIBO: validateCoding operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode validateResource(Map<String, Object> request) {
        String url = baseUrl + "/v1/validate/resource";
        log.info("ZIBO: validateResource operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode submitValidationJob(Map<String, Object> request) {
        String url = baseUrl + "/v1/validate/job";
        log.info("ZIBO: submitValidationJob operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getValidationJobResult(UUID jobId) {
        String url = baseUrl + "/v1/validate/job/" + jobId;
        log.info("ZIBO: getValidationJobResult operation [jobId={}]", jobId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Validate a single FHIR coding against a registered CodeSystem (TM-B6 pre-submission check).
     * Returns the {@code ValidationResultDto} ({@code valid} flag + message). Used to catch a
     * coded diagnosis / order code that isn't in the governed terminology before it is filed.
     */
    public JsonNode validateCoding(String system, String code, String display) {
        String url = baseUrl + "/v1/validate/coding";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("system", system);
        body.put("code", code);
        if (display != null) body.put("display", display);
        log.info("ZIBO: validateCoding system={} code={}", system, code);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
