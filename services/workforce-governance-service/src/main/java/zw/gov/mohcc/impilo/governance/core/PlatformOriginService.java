package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.CountryOperationEntity;
import zw.gov.mohcc.impilo.governance.persistence.CountryOperationRepository;
import zw.gov.mohcc.impilo.governance.persistence.NationalAppointmentEntity;
import zw.gov.mohcc.impilo.governance.persistence.NationalAppointmentRepository;
import zw.gov.mohcc.impilo.governance.persistence.PlatformOriginEntity;
import zw.gov.mohcc.impilo.governance.persistence.PlatformOriginRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Platform Origin governance: country operation creation and national
 * appointments/revocations. Per doctrine, every mutating platform action
 * executes ONLY via an APPROVED access request of type {@code PLATFORM_ACTION}
 * carrying a two-person approvals_required list:
 *
 * <ol>
 *   <li><b>initiate</b> — creates a PENDING PLATFORM_ACTION access request
 *       whose payload names the action and parameters;</li>
 *   <li><b>approve</b> — distinct approvers (never the initiator) record
 *       decisions in {@code wgv_platform_action_approval} via
 *       {@link TwoPersonApprovalService}; when the two-person rule is
 *       satisfied the request transitions to APPROVED;</li>
 *   <li><b>execute</b> — the APPROVED action is executed exactly once and the
 *       request transitions to EXECUTED.</li>
 * </ol>
 *
 * Appointment records carry source, appointer, date, expiry and scope per
 * doctrine; revocation is itself a two-person platform action.
 */
@Service
public class PlatformOriginService {

    private static final Logger log = LoggerFactory.getLogger(PlatformOriginService.class);

    public static final String REQUEST_TYPE_PLATFORM_ACTION = "PLATFORM_ACTION";
    public static final String ACTION_CREATE_COUNTRY_OPERATION = "CREATE_COUNTRY_OPERATION";
    public static final String ACTION_APPOINT_NATIONAL_ADMIN = "APPOINT_NATIONAL_ADMIN";
    public static final String ACTION_REVOKE_APPOINTMENT = "REVOKE_APPOINTMENT";
    /**
     * Found a regulator by appointing its first administrator (RB-3). Same species as
     * APPOINT_NATIONAL_ADMIN — a privileged grant no single person may make — so it rides the same
     * two-person rail. The domain record and the activation event are owned by
     * {@code RegulatorBootstrapService}; this rail supplies only the four-eyes vehicle.
     */
    public static final String ACTION_APPOINT_FOUNDING_REGULATOR_ADMIN = "APPOINT_FOUNDING_REGULATOR_ADMIN";

    /** Two-person rule: every platform action needs two distinct PLATFORM_ORIGIN_ADMINISTRATOR approvals. */
    public static final List<String> TWO_PERSON_PLATFORM_APPROVALS =
            List.of("PLATFORM_ORIGIN_ADMINISTRATOR", "PLATFORM_ORIGIN_ADMINISTRATOR");

    static final String STATUS_APPROVED = "APPROVED";
    static final String STATUS_EXECUTED = "EXECUTED";

    private final PlatformOriginRepository platformOriginRepository;
    private final CountryOperationRepository countryOperationRepository;
    private final NationalAppointmentRepository nationalAppointmentRepository;
    private final AccessRequestService accessRequestService;
    private final TwoPersonApprovalService twoPersonApprovalService;
    private final GovernanceEventService governanceEventService;
    private final ObjectMapper objectMapper;

    public PlatformOriginService(PlatformOriginRepository platformOriginRepository,
                                 CountryOperationRepository countryOperationRepository,
                                 NationalAppointmentRepository nationalAppointmentRepository,
                                 AccessRequestService accessRequestService,
                                 TwoPersonApprovalService twoPersonApprovalService,
                                 GovernanceEventService governanceEventService,
                                 ObjectMapper objectMapper) {
        this.platformOriginRepository = platformOriginRepository;
        this.countryOperationRepository = countryOperationRepository;
        this.nationalAppointmentRepository = nationalAppointmentRepository;
        this.accessRequestService = accessRequestService;
        this.twoPersonApprovalService = twoPersonApprovalService;
        this.governanceEventService = governanceEventService;
        this.objectMapper = objectMapper;
    }

    // ── Initiation ──────────────────────────────────────────────────────────

    /** Initiate CREATE_COUNTRY_OPERATION: creates a PENDING PLATFORM_ACTION access request. */
    @Transactional
    public AccessRequestEntity initiateCountryOperation(UUID tenantId, String requesterId,
                                                        Map<String, Object> body) {
        UUID targetTenantId = requireUuid(body.get("tenantId"), "tenantId");
        String iso = requireIso(body.get("isoCountryCode"));
        if (countryOperationRepository.findByTenantId(targetTenantId).isPresent()) {
            throw new IllegalArgumentException(
                    "A country operation already exists for tenant " + targetTenantId);
        }
        ensurePlatformOrigin();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", targetTenantId.toString());
        params.put("isoCountryCode", iso);
        params.put("displayName", str(body.get("displayName")));
        params.put("legalFramework", body.get("legalFramework"));
        params.put("sovereignOrganisationId", str(body.get("sovereignOrganisationId")));
        return initiatePlatformAction(tenantId, requesterId, ACTION_CREATE_COUNTRY_OPERATION, params);
    }

    /** Initiate APPOINT_NATIONAL_ADMIN against an existing country operation. */
    @Transactional
    public AccessRequestEntity initiateAppointment(UUID tenantId, String requesterId,
                                                   UUID countryOperationId, Map<String, Object> body) {
        CountryOperationEntity operation = getCountryOperation(countryOperationId);
        String subjectUserId = str(body.get("subjectUserId"));
        if (subjectUserId == null || subjectUserId.isBlank()) {
            throw new IllegalArgumentException("subjectUserId is required");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("countryOperationId", operation.getId().toString());
        params.put("subjectUserId", subjectUserId);
        params.put("role", strOrDefault(body.get("role"), "NATIONAL_ADMINISTRATOR"));
        params.put("appointmentSource", strOrDefault(body.get("appointmentSource"), "PLATFORM_ORIGIN"));
        params.put("expiresAt", str(body.get("expiresAt")));
        params.put("scope", body.get("scope"));
        return initiatePlatformAction(tenantId, requesterId, ACTION_APPOINT_NATIONAL_ADMIN, params);
    }

    /**
     * Start a founding-regulator-administrator appointment on the two-person rail (RB-3). The
     * caller ({@code RegulatorBootstrapService}) has already written the domain request row and
     * passes its identifiers through {@code params}; this only creates the four-eyes vehicle.
     */
    public AccessRequestEntity initiateFoundingRegulatorAdmin(UUID tenantId, String requesterId,
                                                              Map<String, Object> params) {
        return initiatePlatformAction(tenantId, requesterId, ACTION_APPOINT_FOUNDING_REGULATOR_ADMIN, params);
    }

    /** Initiate REVOKE_APPOINTMENT — revocation is itself a two-person platform action. */
    @Transactional
    public AccessRequestEntity initiateRevocation(UUID tenantId, String requesterId,
                                                  UUID countryOperationId, UUID appointmentId,
                                                  String reason) {
        NationalAppointmentEntity appointment = nationalAppointmentRepository
                .findByIdAndCountryOperationId(appointmentId, countryOperationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment " + appointmentId + " not found in country operation " + countryOperationId));
        if (!"ACTIVE".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalStateException(
                    "Appointment " + appointmentId + " is not ACTIVE (status " + appointment.getStatus() + ")");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("countryOperationId", countryOperationId.toString());
        params.put("appointmentId", appointmentId.toString());
        params.put("reason", reason);
        return initiatePlatformAction(tenantId, requesterId, ACTION_REVOKE_APPOINTMENT, params);
    }

    private AccessRequestEntity initiatePlatformAction(UUID tenantId, String requesterId,
                                                       String action, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("params", params);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestType", REQUEST_TYPE_PLATFORM_ACTION);
        request.put("status", "PENDING");
        request.put("requesterId", requesterId);
        request.put("requestedScope", "PLATFORM");
        request.put("riskLevel", "CRITICAL");
        request.put("approvalsRequired", TWO_PERSON_PLATFORM_APPROVALS);
        request.put("payload", payload);
        AccessRequestEntity entity = accessRequestService.create(tenantId, request);
        log.info("Initiated platform action {} as access request {} by {}", action, entity.getId(), requesterId);
        return entity;
    }

    // ── Approval ────────────────────────────────────────────────────────────

    /**
     * Record one approver's decision on a PLATFORM_ACTION access request. When
     * the two-person rule becomes satisfied, the request transitions to APPROVED.
     */
    @Transactional
    public Map<String, Object> approve(UUID accessRequestId, String approverUserId,
                                       String approverRole, String decision, String note) {
        AccessRequestEntity request = requirePlatformAction(accessRequestId);
        if (STATUS_EXECUTED.equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Platform action " + accessRequestId + " has already been executed");
        }
        twoPersonApprovalService.recordApproval(request, approverUserId, approverRole, decision, note);
        boolean satisfied = twoPersonApprovalService.isSatisfied(request);
        if (satisfied && !STATUS_APPROVED.equalsIgnoreCase(request.getStatus())) {
            request = accessRequestService.transition(accessRequestId, STATUS_APPROVED,
                    "Two-person approval satisfied", approverUserId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessRequestId", accessRequestId.toString());
        out.put("status", request.getStatus());
        out.put("approvalsRecorded", twoPersonApprovalService.approvalCount(request));
        out.put("approvalsSatisfied", satisfied);
        return out;
    }

    // ── Execution ───────────────────────────────────────────────────────────

    /**
     * Execute an APPROVED PLATFORM_ACTION exactly once. Rejects requests that
     * are not APPROVED (including already-EXECUTED ones).
     */
    @Transactional
    public Map<String, Object> execute(UUID accessRequestId, String actorId) {
        AccessRequestEntity request = requirePlatformAction(accessRequestId);
        if (!STATUS_APPROVED.equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException(
                    "Platform action " + accessRequestId + " must be APPROVED before execution (status "
                    + request.getStatus() + ")");
        }
        Map<String, Object> payload = readPayload(request);
        String action = str(payload.get("action"));
        Map<String, Object> params = asMap(payload.get("params"));
        Object result;
        switch (action == null ? "" : action.toUpperCase(Locale.ROOT)) {
            case ACTION_CREATE_COUNTRY_OPERATION ->
                    result = executeCreateCountryOperation(request, params);
            case ACTION_APPOINT_NATIONAL_ADMIN ->
                    result = executeAppointment(request, params);
            case ACTION_REVOKE_APPOINTMENT ->
                    result = executeRevocation(request, params, actorId);
            case ACTION_APPOINT_FOUNDING_REGULATOR_ADMIN ->
                    result = executeFoundingRegulatorAdmin(request, params);
            default -> throw new IllegalArgumentException("Unknown platform action: " + action);
        }
        accessRequestService.transition(accessRequestId, STATUS_EXECUTED,
                "Platform action executed", actorId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessRequestId", accessRequestId.toString());
        out.put("action", action);
        out.put("status", STATUS_EXECUTED);
        out.put("result", result);
        return out;
    }

    /**
     * Read the current state of a PLATFORM_ACTION for the two-person console.
     * The durable terminal state (incl. EXECUTED) is served from the access
     * request itself, so the browser can render the true state after a refresh
     * rather than a lost in-session flag.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAction(UUID accessRequestId) {
        AccessRequestEntity request = requirePlatformAction(accessRequestId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessRequestId", accessRequestId.toString());
        out.put("requestType", request.getRequestType());
        out.put("status", request.getStatus());
        out.put("approvalsRecorded", twoPersonApprovalService.approvalCount(request));
        out.put("approvalsSatisfied", twoPersonApprovalService.isSatisfied(request));
        out.put("approvalsRequired", TWO_PERSON_PLATFORM_APPROVALS.size());
        return out;
    }

    private CountryOperationEntity executeCreateCountryOperation(AccessRequestEntity request,
                                                                 Map<String, Object> params) {
        UUID targetTenantId = requireUuid(params.get("tenantId"), "tenantId");
        if (countryOperationRepository.findByTenantId(targetTenantId).isPresent()) {
            throw new IllegalStateException(
                    "A country operation already exists for tenant " + targetTenantId);
        }
        CountryOperationEntity entity = new CountryOperationEntity();
        entity.setTenantId(targetTenantId);
        entity.setIsoCountryCode(requireIso(params.get("isoCountryCode")));
        entity.setDisplayName(str(params.get("displayName")));
        entity.setLegalFramework(asMap(params.get("legalFramework")));
        entity.setSovereignOrganisationId(parseUuid(params.get("sovereignOrganisationId")));
        entity.setStatus("ACTIVE");
        entity.setCreatedByPlatformAction(request.getId());
        CountryOperationEntity saved = countryOperationRepository.save(entity);
        emit("COUNTRY_OPERATION", saved.getId(), "impilo.governance.country_operation.created",
                Map.of("countryOperationId", saved.getId().toString(),
                        "isoCountryCode", saved.getIsoCountryCode(),
                        "accessRequestId", request.getId().toString()),
                request.getTenantId());
        log.info("Country operation {} ({}) created via platform action {}",
                saved.getId(), saved.getIsoCountryCode(), request.getId());
        return saved;
    }

    /**
     * Execute a founding-regulator-administrator appointment (RB-3): with the two approvals in
     * hand, emit the event org-registry consumes to create and verify the founding appointment.
     *
     * <p>This service does not write the appointment — appointments are org-registry's (R2). It
     * emits and returns; {@code RegulatorBootstrapService} marks its domain row ACTIVATED. The
     * event carries the approval vehicle's id so org-registry's {@code verify} can record which
     * four-eyes decision authorised the activation, rather than a bare system actor.</p>
     */
    private Map<String, Object> executeFoundingRegulatorAdmin(AccessRequestEntity request,
                                                              Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bootstrapRequestId", str(params.get("bootstrapRequestId")));
        payload.put("organizationId", str(params.get("organizationId")));
        payload.put("personHealthId", str(params.get("personHealthId")));
        payload.put("roleCode", str(params.get("roleCode")));
        payload.put("evidenceRef", str(params.get("evidenceRef")));
        payload.put("accessRequestId", request.getId().toString());
        payload.put("approvedVia", "TWO_PERSON_PLATFORM_APPROVAL");

        emit("REGULATOR_BOOTSTRAP", request.getId(),
                "impilo.governance.regulator_bootstrap.activated", payload, request.getTenantId());
        return payload;
    }

    private NationalAppointmentEntity executeAppointment(AccessRequestEntity request,
                                                         Map<String, Object> params) {
        CountryOperationEntity operation =
                getCountryOperation(requireUuid(params.get("countryOperationId"), "countryOperationId"));
        NationalAppointmentEntity entity = new NationalAppointmentEntity();
        entity.setCountryOperationId(operation.getId());
        entity.setSubjectUserId(str(params.get("subjectUserId")));
        entity.setRole(strOrDefault(params.get("role"), "NATIONAL_ADMINISTRATOR"));
        entity.setAppointmentSource(strOrDefault(params.get("appointmentSource"), "PLATFORM_ORIGIN"));
        entity.setAppointedBy(request.getRequesterId());
        entity.setAppointedAt(Instant.now());
        entity.setExpiresAt(parseInstant(params.get("expiresAt")));
        entity.setScope(asMap(params.get("scope")));
        entity.setStatus("ACTIVE");
        NationalAppointmentEntity saved = nationalAppointmentRepository.save(entity);
        emit("NATIONAL_APPOINTMENT", saved.getId(), "impilo.governance.national_appointment.created",
                Map.of("appointmentId", saved.getId().toString(),
                        "countryOperationId", operation.getId().toString(),
                        "role", saved.getRole(),
                        "accessRequestId", request.getId().toString()),
                request.getTenantId());
        return saved;
    }

    private NationalAppointmentEntity executeRevocation(AccessRequestEntity request,
                                                        Map<String, Object> params, String actorId) {
        UUID countryOperationId = requireUuid(params.get("countryOperationId"), "countryOperationId");
        UUID appointmentId = requireUuid(params.get("appointmentId"), "appointmentId");
        NationalAppointmentEntity appointment = nationalAppointmentRepository
                .findByIdAndCountryOperationId(appointmentId, countryOperationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment " + appointmentId + " not found in country operation " + countryOperationId));
        if (!"ACTIVE".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalStateException(
                    "Appointment " + appointmentId + " is not ACTIVE (status " + appointment.getStatus() + ")");
        }
        appointment.setStatus("REVOKED");
        appointment.setRevocationReason(strOrDefault(params.get("reason"),
                "Revoked via platform action " + request.getId()));
        NationalAppointmentEntity saved = nationalAppointmentRepository.save(appointment);
        emit("NATIONAL_APPOINTMENT", saved.getId(), "impilo.governance.national_appointment.revoked",
                Map.of("appointmentId", saved.getId().toString(),
                        "countryOperationId", countryOperationId.toString(),
                        "revokedBy", actorId != null ? actorId : "system",
                        "accessRequestId", request.getId().toString()),
                request.getTenantId());
        return saved;
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CountryOperationEntity> listCountryOperations() {
        return countryOperationRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public CountryOperationEntity getCountryOperation(UUID id) {
        return countryOperationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Country operation not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<NationalAppointmentEntity> listAppointments(UUID countryOperationId) {
        getCountryOperation(countryOperationId);
        return nationalAppointmentRepository.findByCountryOperationIdOrderByAppointedAtAsc(countryOperationId);
    }

    /**
     * Who-approved projection for a PLATFORM_ACTION access request: each recorded
     * approver decision (id/role/decision/note/timestamp). Read-only — does not
     * touch the two-person decision engine.
     */
    @Transactional(readOnly = true)
    public List<PlatformActionApprovalView> listApprovals(UUID accessRequestId) {
        AccessRequestEntity request = requirePlatformAction(accessRequestId);
        return twoPersonApprovalService.approvals(request).stream()
                .map(PlatformActionApprovalView::from)
                .toList();
    }

    /** The singleton Platform Origin root record, created on first use. */
    @Transactional
    public PlatformOriginEntity ensurePlatformOrigin() {
        return platformOriginRepository.findFirstByOrderByCreatedAtAsc().orElseGet(() -> {
            PlatformOriginEntity root = new PlatformOriginEntity();
            root.setRootAccountRefs(new ArrayList<>());
            root.setRecoveryPolicy(Map.of(
                    "type", "TWO_PERSON_RECOVERY",
                    "minApprovals", 2,
                    "approverRole", "PLATFORM_ORIGIN_ADMINISTRATOR"));
            PlatformOriginEntity saved = platformOriginRepository.save(root);
            emit("PLATFORM_ORIGIN", saved.getId(), "impilo.governance.platform_origin.created",
                    Map.of("platformOriginId", saved.getId().toString()), null);
            log.info("Platform Origin root record {} created", saved.getId());
            return saved;
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private AccessRequestEntity requirePlatformAction(UUID accessRequestId) {
        AccessRequestEntity request = accessRequestService.get(accessRequestId);
        if (!REQUEST_TYPE_PLATFORM_ACTION.equalsIgnoreCase(request.getRequestType())) {
            throw new IllegalArgumentException(
                    "Access request " + accessRequestId + " is not a PLATFORM_ACTION");
        }
        return request;
    }

    private Map<String, Object> readPayload(AccessRequestEntity request) {
        if (request.getPayload() == null || request.getPayload().isBlank()) {
            throw new IllegalStateException("Platform action " + request.getId() + " has no payload");
        }
        try {
            return objectMapper.readValue(request.getPayload(),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Platform action " + request.getId() + " payload is unreadable: " + e.getMessage());
        }
    }

    private void emit(String aggregateType, UUID aggregateId, String eventType,
                      Map<String, Object> payload, UUID tenantId) {
        governanceEventService.enqueue(aggregateType, aggregateId.toString(), eventType, payload,
                tenantId, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String requireIso(Object value) {
        String iso = str(value);
        if (iso == null || !iso.matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException("isoCountryCode must be a 2-letter ISO 3166-1 code");
        }
        return iso.toUpperCase(Locale.ROOT);
    }

    private static UUID requireUuid(Object value, String field) {
        UUID parsed = parseUuid(value);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " must be a valid UUID");
        }
        return parsed;
    }

    private static UUID parseUuid(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant parseInstant(Object value) {
        String s = str(value);
        if (s == null || s.isBlank()) return null;
        return Instant.parse(s);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return s == null || s.isBlank() ? defaultValue : s;
    }
}
