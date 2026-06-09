package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataPipelineServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for data-pipeline-service ingestion sources and watermarks.
 */
@RestController
@RequestMapping("/internal/v1/pipeline")
public class DataPipelineController {

    private static final Logger log = LoggerFactory.getLogger(DataPipelineController.class);

    private final DataPipelineServiceClient pipelineClient;

    public DataPipelineController(DataPipelineServiceClient pipelineClient) {
        this.pipelineClient = pipelineClient;
    }

    @GetMapping("/watermarks")
    public ResponseEntity<Map<String, Object>> listWatermarks(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(pipelineClient::listWatermarks, requestId, correlationId);
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(pipelineClient::listSources, requestId, correlationId);
    }

    @PostMapping("/sources")
    public ResponseEntity<Map<String, Object>> createSource(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = pipelineClient.createSource(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("Pipeline create source failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = pipelineClient.ingest(body);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("Pipeline ingest failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> supplier,
            String requestId,
            String correlationId) {
        try {
            return ResponseEntity.ok(wrap(supplier.get(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("Pipeline proxy failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data != null ? data : Map.of());
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "PIPELINE_UNAVAILABLE", "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
