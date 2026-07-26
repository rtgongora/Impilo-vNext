package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.staffing.StaffIdentityResolver;

import java.util.*;

/**
 * Staffing surfaces: the week's roster, the on-call rota and the shift-swap workflow.
 *
 * <p><b>Repointed from tuso (route-contract completion).</b> These handlers called
 * {@code tuso /v1/staffing/*} — paths no service in the estate serves — so the roster screen, the
 * on-call screen and the control tower rendered empty while every layer reported success. Vashandi
 * owns rostering ({@code vsh_roster}/{@code vsh_shift}); scheduled on-call is a projection over the
 * shifts that carry an {@code on_call_role}, so there is one on-call truth with several read
 * shapes rather than a rival store.</p>
 *
 * <p><b>An empty roster is now distinguishable from a broken one.</b> The previous handlers
 * returned {@code data: []} both when the upstream answered with nothing and when it answered with
 * an empty list, so "no shifts rostered this week" and "the call produced nothing" looked
 * identical to the screen. The upstream response is passed through as given; only a genuine
 * failure takes the error path.</p>
 */
@RestController
@RequestMapping("/internal/v1/staffing")
public class StaffingController {

    private static final Logger log = LoggerFactory.getLogger(StaffingController.class);

    private final VashandiServiceClient vashandiClient;
    private final StaffIdentityResolver identityResolver;

    public StaffingController(VashandiServiceClient vashandiClient,
                              StaffIdentityResolver identityResolver) {
        this.vashandiClient = vashandiClient;
        this.identityResolver = identityResolver;
    }

    public record PatchSwapRequest(@NotBlank String status, String note) {}

    /**
     * A swap is raised against a rostered shift and a person, not against typed names.
     *
     * <p>The previous contract took {@code requestor_name}/{@code requestee_name} as free text.
     * Two staff share a name, and a shift swap changes who is clinically accountable for a window —
     * that is not an ambiguity to resolve by string match, so the identifiers are required.</p>
     */
    public record CreateSwapRequest(
            @NotBlank String requesting_shift_id,
            @NotBlank String requested_profile_id,
            String offered_shift_id,
            String reason
    ) {}

    @GetMapping("/roster-week")
    public ResponseEntity<Map<String, Object>> rosterWeek(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam,
            @RequestParam(name = "workspace_id", required = false) String workspaceId) {
        try {
            JsonNode roster = vashandiClient.getRosterWeek(facilityId, weekStartParam);
            StaffIdentityResolver.Resolution names = resolveNames(roster, "workforce_profile_id");
            enrichRows(roster, names, List.of(new NameField("workforce_profile_id", "")));
            return ok(roster, requestId, correlationId, Map.of(
                    "week_start", weekStartParam,
                    "identity_resolution", names.status()));
        } catch (Exception e) {
            log.warn("VASHANDI roster-week unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> listOnCall(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam) {
        try {
            JsonNode onCall = vashandiClient.listOnCall(facilityId, weekStartParam);
            StaffIdentityResolver.Resolution names = resolveNames(
                    onCall, "primary_workforce_profile_id", "backup_workforce_profile_id");
            enrichRows(onCall, names, List.of(
                    new NameField("primary_workforce_profile_id", "primary_"),
                    new NameField("backup_workforce_profile_id", "backup_")));
            return ok(onCall, requestId, correlationId, Map.of(
                    "week_start", weekStartParam,
                    "identity_resolution", names.status()));
        } catch (Exception e) {
            log.warn("VASHANDI on-call unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            JsonNode swaps = vashandiClient.listSwapRequests(facilityId);
            StaffIdentityResolver.Resolution names = resolveNames(
                    swaps, "requesting_workforce_profile_id", "requested_workforce_profile_id");
            enrichRows(swaps, names, List.of(
                    new NameField("requesting_workforce_profile_id", "requesting_"),
                    new NameField("requested_workforce_profile_id", "requested_")));
            return ok(swaps, requestId, correlationId, Map.of("identity_resolution", names.status()));
        } catch (Exception e) {
            log.warn("VASHANDI swap list unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @PostMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> createSwap(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateSwapRequest body) {

        Map<String, Object> swapData = new LinkedHashMap<>();
        swapData.put("requestingShiftId", body.requesting_shift_id());
        swapData.put("requestedProfileId", body.requested_profile_id());
        if (body.offered_shift_id() != null && !body.offered_shift_id().isBlank()) {
            swapData.put("offeredShiftId", body.offered_shift_id());
        }
        swapData.put("reason", body.reason());

        try {
            JsonNode result = vashandiClient.createSwapRequest(swapData);
            if (result == null) {
                return upstreamFailure(requestId, correlationId,
                        "VASHANDI_UNAVAILABLE", "Swap request was not persisted");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.warn("VASHANDI create swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @PatchMapping("/on-call/swaps/{id}")
    public ResponseEntity<Map<String, Object>> patchSwap(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody PatchSwapRequest body) {

        String normalized = body.status().trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("APPROVED") && !normalized.equals("DECLINED")
                && !normalized.equals("WITHDRAWN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be APPROVED, DECLINED or WITHDRAWN");
        }

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("status", normalized);
        updateData.put("note", body.note());
        try {
            JsonNode result = vashandiClient.updateSwapRequest(id.toString(), updateData);
            if (result == null) {
                return upstreamFailure(requestId, correlationId,
                        "VASHANDI_UNAVAILABLE", "Swap update was not persisted");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("VASHANDI patch swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    // ── display-name composition ─────────────────────────────────────────────
    //
    // Vashandi holds no names and no telephone numbers by design; the rota therefore arrives
    // carrying profile ids and worker references. The names are composed here against the identity
    // registries (see StaffIdentityResolver) rather than copied into the rostering service, and
    // when the registries cannot be reached the references stay exactly as they were — with the
    // response saying so, so that "nobody is on file" never masquerades as "we could not ask".

    /** One profile-id field on a row and the prefix its resolved facts are written under. */
    private record NameField(String profileIdField, String outputPrefix) {}

    private StaffIdentityResolver.Resolution resolveNames(JsonNode rows, String... profileIdFields) {
        Set<String> profileIds = new LinkedHashSet<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode attributes = row.path("attributes");
                for (String field : profileIdFields) {
                    JsonNode value = attributes.get(field);
                    if (value != null && !value.isNull() && !value.asText().isBlank()) {
                        profileIds.add(value.asText());
                    }
                }
            }
        }
        return identityResolver.resolve(profileIds);
    }

    private void enrichRows(JsonNode rows, StaffIdentityResolver.Resolution names, List<NameField> fields) {
        if (rows == null || !rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            if (!(row.path("attributes") instanceof ObjectNode attributes)) {
                continue;
            }
            for (NameField field : fields) {
                JsonNode idNode = attributes.get(field.profileIdField());
                String profileId = idNode == null || idNode.isNull() ? null : idNode.asText();
                StaffIdentityResolver.StaffIdentity identity = names.get(profileId);

                // Written on every row, resolved or not: a screen that reads display_name must be
                // able to tell a person with no registry name from a field that was never composed.
                putOrNull(attributes, field.outputPrefix() + "display_name",
                        identity == null ? null : identity.displayName());
                putOrNull(attributes, field.outputPrefix() + "profession",
                        identity == null ? null : identity.profession());

                // The rota's contact number: vashandi always sends null, so a number here is the
                // one the provider registry holds, never one this layer made up.
                String phoneField = field.outputPrefix() + "phone";
                if (identity != null && identity.phone() != null) {
                    attributes.put(phoneField, identity.phone());
                    attributes.put(field.outputPrefix() + "phone_source", "PROVIDER_REGISTRY");
                } else if (!attributes.has(phoneField)) {
                    attributes.putNull(phoneField);
                }
            }
        }
    }

    private static void putOrNull(ObjectNode attributes, String field, String value) {
        if (value == null) {
            attributes.putNull(field);
        } else {
            attributes.put(field, value);
        }
    }

    /**
     * Pass the upstream answer through as given. An empty list means the week is genuinely empty —
     * it is not a stand-in for a call that produced nothing, which takes the error path instead.
     */
    private ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId,
                                                   String correlationId, Map<String, String> extraMeta) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.putAll(extraMeta);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data == null ? List.of() : data);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String requestId, String correlationId, String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
