package zw.gov.mohcc.impilo.pharmacy.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.integration.MushexIntegration;
import zw.gov.mohcc.impilo.pharmacy.integration.OrosIntegration;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * Internal REST controller for inter-service communication.
 *
 * <p>Provides endpoints for pushing status updates to OROS and
 * emitting charge events to MUSHEX. These endpoints are intended
 * for platform-internal calls only (INTERNAL access mode).</p>
 */
@RestController
@RequestMapping("/v1/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final OrosIntegration orosIntegration;
    private final MushexIntegration mushexIntegration;

    public InternalController(OrosIntegration orosIntegration,
                              MushexIntegration mushexIntegration) {
        this.orosIntegration = orosIntegration;
        this.mushexIntegration = mushexIntegration;
    }

    /**
     * Push a dispense status update to OROS.
     *
     * <p>Called internally when the dispense lifecycle reaches a milestone
     * that OROS needs to be aware of (e.g. DISPENSED, PARTIALLY_DISPENSED).</p>
     */
    @PostMapping("/oros/status")
    public ResponseEntity<ApiResponse<Map<String, String>>> pushOrosStatus(
            @RequestBody Map<String, String> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String orosOrderId = payload.get("orosOrderId");
        String status = payload.get("status");
        String summary = payload.get("summary");

        orosIntegration.pushDispenseStatus(orosOrderId, status, summary);

        log.info("OROS status pushed: orosOrderId={}, status={}", orosOrderId, status);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("orosOrderId", orosOrderId, "status", status), correlationId));
    }

    /**
     * Emit a billing charge event to MUSHEX.
     *
     * <p>Called internally when items are dispensed to generate a billing
     * charge for the patient's account.</p>
     */
    @PostMapping("/mushex/charge")
    public ResponseEntity<ApiResponse<Map<String, String>>> emitMushexCharge(
            @RequestBody Map<String, String> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String orderId = payload.get("orderId");
        String itemCode = payload.get("itemCode");
        int qty = Integer.parseInt(payload.getOrDefault("qty", "0"));
        String unitPrice = payload.get("unitPrice");

        mushexIntegration.emitChargeEvent(
                java.util.UUID.fromString(orderId), itemCode, qty, unitPrice,
                TrustContextHolder.require().tenantId());

        log.info("MUSHEX charge emitted: orderId={}, itemCode={}, qty={}", orderId, itemCode, qty);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("orderId", orderId, "status", "CHARGE_EMITTED"), correlationId));
    }
}
