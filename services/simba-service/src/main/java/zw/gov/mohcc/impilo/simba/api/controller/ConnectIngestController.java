package zw.gov.mohcc.impilo.simba.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.ConnectIngestService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/connect")
public class ConnectIngestController {

    private final ConnectIngestService connectIngestService;

    public ConnectIngestController(ConnectIngestService connectIngestService) {
        this.connectIngestService = connectIngestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) throws JsonProcessingException {

        String personCpid = required(body, "person_cpid");
        String dataType = required(body, "data_type");
        String sourceId = required(body, "source_id");

        @SuppressWarnings("unchecked")
        Map<String, Object> ext = body.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : null;

        boolean inserted = connectIngestService.ingest(tenantId, personCpid, dataType, sourceId, ext);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accepted", inserted);
        res.put("duplicate", !inserted);
        return ResponseEntity.status(inserted ? HttpStatus.CREATED : HttpStatus.OK).body(res);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
