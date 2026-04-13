package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Endpoint 1: Table-like read — GET /internal/v1/facilities
 * Queries actual Postgres with filtering and pagination.
 */
@RestController
@RequestMapping("/internal/v1/facilities")
public class FacilityController {

    public FacilityController() {
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFacilities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "facility_type") String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String search) {
    throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
}
}
