package zw.gov.mohcc.impilo.nhume.integration.writeback;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenInterceptor;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * HTTP implementation of the delivery write-back gateway.
 *
 * <p>Headers follow the service-originated call rules (MISSING_REQUIRED_HEADER
 * defect family): X-Pod-ID / X-Request-ID / Idempotency-Key are synthesized
 * per call; tenant, correlation and actor context flow through from the
 * sign-off request, including the signing actor's bearer token so the owning
 * service authorizes and attributes the transition honestly.
 *
 * <p><strong>Authentication.</strong> The signing actor's bearer only exists while the
 * write-back runs inside the sign-off request; a scheduled retry has no request context and
 * previously sent no credential at all. A {@link WorkloadTokenInterceptor} now attaches
 * Nhume's <em>own</em> workload token, and only when no {@code Authorization} header is
 * already present — a delegated human token is never overwritten by the stronger service
 * credential. When no token can be minted the header is simply absent and the callee
 * refuses; the gateway never invents an unauthenticated fallback.
 */
@Component
public class HttpNhumeWriteBackGateway implements NhumeWriteBackGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpNhumeWriteBackGateway.class);

    private final RestClient restClient;
    private final String orosBaseUrl;
    private final String madiBaseUrl;
    private final String pctBaseUrl;
    private final String msikaFlowBaseUrl;

    public HttpNhumeWriteBackGateway(
            @Value("${nhume.writeback.oros-base-url:http://oros-service:8089}") String orosBaseUrl,
            @Value("${nhume.writeback.madi-base-url:http://madi-service:8300}") String madiBaseUrl,
            @Value("${nhume.writeback.pct-base-url:http://pct-service:8088}") String pctBaseUrl,
            @Value("${nhume.writeback.msika-flow-base-url:http://msika-flow-service:8100}") String msikaFlowBaseUrl,
            @Value("${nhume.writeback.timeout-ms:5000}") int timeoutMs,
            WorkloadTokenProvider workloadTokenProvider) {
        this(orosBaseUrl, madiBaseUrl, pctBaseUrl, msikaFlowBaseUrl,
                defaultBuilder(timeoutMs, workloadTokenProvider));
    }

    /** Testing seam: a builder the test binds a mock server to. */
    HttpNhumeWriteBackGateway(String orosBaseUrl, String madiBaseUrl, String pctBaseUrl,
                              String msikaFlowBaseUrl, RestClient.Builder builder) {
        this.restClient = builder.build();
        this.orosBaseUrl = trim(orosBaseUrl);
        this.madiBaseUrl = trim(madiBaseUrl);
        this.pctBaseUrl = trim(pctBaseUrl);
        this.msikaFlowBaseUrl = trim(msikaFlowBaseUrl);
    }

    /** Package-private so the test exercises the real interceptor wiring, not a copy of it. */
    static RestClient.Builder defaultBuilder(int timeoutMs,
                                             WorkloadTokenProvider workloadTokenProvider) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(new WorkloadTokenInterceptor(workloadTokenProvider));
    }

    @Override
    public WriteBackOutcome orosReceiveByOrder(String orosOrderRef, WriteBackContext ctx) {
        try {
            JsonNode specimens = restClient.get()
                    .uri(orosBaseUrl + "/v1/orders/{orderId}/specimens", orosOrderRef)
                    .headers(trustHeaders(ctx, "oros-list"))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = specimens == null ? null : specimens.path("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                return WriteBackOutcome.noop("no specimens found for order " + orosOrderRef);
            }
            int received = 0;
            int alreadyDone = 0;
            for (JsonNode s : data) {
                String status = s.path("status").asText("");
                if ("RECEIVED".equals(status)) {
                    alreadyDone++;
                    continue;
                }
                if (!"DISPATCHED".equals(status) && !"IN_TRANSIT".equals(status)) {
                    continue;
                }
                String specimenId = s.path("specimenId").asText(null);
                if (specimenId == null) continue;
                restClient.post()
                        .uri(orosBaseUrl + "/v1/specimens/{id}/receive", specimenId)
                        .headers(trustHeaders(ctx, "oros-receive-" + specimenId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of())
                        .retrieve()
                        .toBodilessEntity();
                received++;
            }
            if (received == 0 && alreadyDone > 0) {
                return WriteBackOutcome.noop("all " + alreadyDone + " specimens already received");
            }
            if (received == 0) {
                return WriteBackOutcome.noop("no in-flight specimens to receive for order " + orosOrderRef);
            }
            return WriteBackOutcome.ok("received " + received + " specimen(s) into the lab");
        } catch (RestClientResponseException ex) {
            return failedFrom("OROS", ex);
        } catch (Exception ex) {
            return failedUnreachable("OROS", ex);
        }
    }

    @Override
    public WriteBackOutcome madiCompleteOrder(String madiOrderRef, WriteBackContext ctx) {
        try {
            restClient.post()
                    .uri(madiBaseUrl + "/internal/v1/madi/orders/{orderId}/complete", madiOrderRef)
                    .headers(trustHeaders(ctx, "madi-complete"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "completed_by", ctx.actorId() == null ? "nhume-service" : ctx.actorId(),
                            "delivery_ref", ctx.deliveryId()))
                    .retrieve()
                    .toBodilessEntity();
            return WriteBackOutcome.ok("blood order marked COMPLETED");
        } catch (RestClientResponseException ex) {
            return failedFrom("MADI", ex);
        } catch (Exception ex) {
            return failedUnreachable("MADI", ex);
        }
    }

    @Override
    public WriteBackOutcome pctAcceptReferral(String pctReferralRef, WriteBackContext ctx) {
        try {
            restClient.post()
                    .uri(pctBaseUrl + "/v1/referrals/{id}/accept", pctReferralRef)
                    .headers(trustHeaders(ctx, "pct-accept"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("acceptedVia", "NHUME_DELIVERY", "deliveryRef", ctx.deliveryId()))
                    .retrieve()
                    .toBodilessEntity();
            return WriteBackOutcome.ok("referral accepted on arrival");
        } catch (RestClientResponseException ex) {
            return failedFrom("PCT", ex);
        } catch (Exception ex) {
            return failedUnreachable("PCT", ex);
        }
    }

    @Override
    public WriteBackOutcome msikaFlowDispatchStatus(String orderRef, String status, String deliveryId,
                                                    String proofRef, WriteBackContext ctx) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("deliveryId", deliveryId);
            if (proofRef != null && !proofRef.isBlank()) {
                body.put("proofRef", proofRef);
            }
            restClient.post()
                    .uri(msikaFlowBaseUrl + "/internal/v1/msika-flow/orders/{orderId}/dispatch-status",
                            orderRef)
                    .headers(msikaFlowHeaders(ctx, "msika-flow-" + status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return WriteBackOutcome.ok("marketplace order reported " + status + " to Msika Flow");
        } catch (RestClientResponseException ex) {
            return failedFrom("MSIKA_FLOW", ex);
        } catch (Exception ex) {
            return failedUnreachable("MSIKA_FLOW", ex);
        }
    }

    @Override
    public WriteBackOutcome msikaFlowSelectionDeliveryStatus(String selectionRef, String status,
                                                             String deliveryId, String proofRef,
                                                             String verificationGrade,
                                                             WriteBackContext ctx) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("deliveryId", deliveryId);
            if (proofRef != null && !proofRef.isBlank()) {
                body.put("proofRef", proofRef);
            }
            if (verificationGrade != null && !verificationGrade.isBlank()) {
                body.put("verificationGrade", verificationGrade);
            }
            restClient.post()
                    .uri(msikaFlowBaseUrl + "/internal/v1/msika-flow/selections/{selectionId}/delivery-status",
                            selectionRef)
                    .headers(msikaFlowHeaders(ctx, "msika-flow-selection-" + status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return WriteBackOutcome.ok("marketplace selection reported " + status + " to Msika Flow");
        } catch (RestClientResponseException ex) {
            return failedFrom("MSIKA_FLOW_SELECTION", ex);
        } catch (Exception ex) {
            return failedUnreachable("MSIKA_FLOW_SELECTION", ex);
        }
    }

    /**
     * Msika Flow write-backs are service-originated marketplace fulfilment calls, not
     * actor-attributed clinical transitions: the actor is pinned to nhume-service / SYSTEM.
     *
     * <p>The purpose is {@code OPERATIONS}, and choosing it was the substance of this fix.
     * Three codes were candidates:
     *
     * <ul>
     *   <li><b>SYSTEM</b> — what this sent before, and wrong twice over. In
     *       {@code PolicyEngine.evaluatePolicies} SYSTEM returns {@code continueWith(null)}
     *       from both the no-rules and the no-matching-ALLOW-rule branches, and it
     *       short-circuits consent as well. It is the broadest code in the vocabulary, and a
     *       courier reporting that a parcel arrived has no business holding it. It is also
     *       now refused outright unless the caller presents a validated workload session
     *       ({@code AuthorizeController.isWorkloadSession}).</li>
     *   <li><b>CARE_COORDINATION</b> — what the sibling {@link #trustHeaders} sends, and
     *       tempting because the enum's own javadoc lists "delivery write-backs". But that
     *       sentence describes the OROS / MADI / PCT write-backs next door, which move
     *       specimens, blood orders and referrals. CARE_COORDINATION is HL7 ActReason COC, a
     *       <em>specialization of TREAT</em> carrying the same visibility envelope as
     *       TREATMENT. Attaching it to a marketplace parcel callback would widen clinical
     *       visibility for a call that touches no patient and no clinical record.</li>
     *   <li><b>OPERATIONS</b> — "facility operations, scheduling, queue management". What
     *       these two endpoints actually drive: an order state machine, a courier
     *       chain-of-custody record, and the proof-of-delivery projection that opens the
     *       escrow / refund seams. Operational, non-clinical, and the narrowest honest fit.
     *       The vocabulary has no LOGISTICS code — that was tried once and denied with
     *       INVALID_PURPOSE at Step 2, because it is not a PurposeOfUse constant.</li>
     * </ul>
     *
     * <p>Narrowing the purpose is not, by itself, what makes these calls safe — see the
     * class javadoc. Authentication is; the purpose is what the decision log and any future
     * rule will read.
     */
    private Consumer<HttpHeaders> msikaFlowHeaders(WriteBackContext ctx, String idempotencySuffix) {
        return headers -> {
            headers.set("X-Tenant-ID", ctx.tenantId());
            headers.set("X-Pod-ID", ctx.podId() == null ? "national-spine" : ctx.podId());
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID",
                    ctx.correlationId() == null ? UUID.randomUUID().toString() : ctx.correlationId());
            // Stable per (delivery, milestone): retried sign-offs do not double-apply.
            String idempotencyKey = "nhume-writeback:" + ctx.deliveryId() + ":" + idempotencySuffix;
            headers.set("Idempotency-Key", idempotencyKey);
            headers.set("X-Idempotency-Key", idempotencyKey);
            headers.set("X-Actor-ID", "nhume-service");
            headers.set("X-Actor-Type", "SYSTEM");
            headers.set("X-Purpose-Of-Use", "OPERATIONS");
            // Only a delegated bearer is set here; when there is none the workload-token
            // interceptor attaches Nhume's own credential. Order matters: the human token wins.
            if (ctx.bearerToken() != null && !ctx.bearerToken().isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, ctx.bearerToken());
            }
        };
    }

    private Consumer<HttpHeaders> trustHeaders(WriteBackContext ctx, String idempotencySuffix) {
        return headers -> {
            headers.set("X-Tenant-ID", ctx.tenantId());
            headers.set("X-Pod-ID", ctx.podId() == null ? "national-spine" : ctx.podId());
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID",
                    ctx.correlationId() == null ? UUID.randomUUID().toString() : ctx.correlationId());
            // Stable per (delivery, target-action): retried sign-offs do not double-apply.
            headers.set("Idempotency-Key", "nhume-writeback:" + ctx.deliveryId() + ":" + idempotencySuffix);
            if (ctx.actorId() != null) headers.set("X-Actor-ID", ctx.actorId());
            headers.set("X-Actor-Type", ctx.actorType() == null ? "SYSTEM" : ctx.actorType());
            headers.set("X-Purpose-Of-Use", "CARE_COORDINATION");
            if (ctx.bearerToken() != null && !ctx.bearerToken().isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, ctx.bearerToken());
            }
        };
    }

    private WriteBackOutcome failedFrom(String system, RestClientResponseException ex) {
        String detail = system + " rejected the transition: HTTP " + ex.getStatusCode().value();
        log.warn("Nhume write-back to {} failed: {} {}", system, ex.getStatusCode().value(),
                ex.getResponseBodyAsString());
        return WriteBackOutcome.failed(detail);
    }

    private WriteBackOutcome failedUnreachable(String system, Exception ex) {
        log.warn("Nhume write-back to {} unreachable: {}", system, ex.toString());
        return WriteBackOutcome.failed(system + " unreachable: " + ex.getClass().getSimpleName());
    }

    private static String trim(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
