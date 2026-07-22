package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.RegulatoryAppointmentService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AppointmentRoleEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Regulatory appointments (ROM-W1): the login-context anchor for HPA + council personnel.
 * Appointments are org-scoped and never facility-attached. Create lands PENDING_VERIFICATION;
 * verify promotes to ACTIVE (which vashandi mirrors into an org-scoped work assignment, W2).
 */
@RestController
@RequestMapping("/v1/regulatory")
public class RegulatoryAppointmentController {

    private final RegulatoryAppointmentService appointmentService;

    public RegulatoryAppointmentController(RegulatoryAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/appointment-roles")
    public List<AppointmentRoleEntity> roles() {
        return appointmentService.listRoles();
    }

    @PostMapping("/organizations/{organizationId}/appointments")
    public ResponseEntity<RegulatoryAppointmentEntity> create(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.CreateAppointmentRequest request) throws Exception {
        RegulatoryAppointmentEntity created =
                appointmentService.create(tenantId(), organizationId, request, actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/organizations/{organizationId}/appointments")
    public List<RegulatoryAppointmentEntity> listForOrganization(@PathVariable UUID organizationId) {
        return appointmentService.listForOrganization(tenantId(), organizationId);
    }

    @GetMapping("/appointments/by-person/{personHealthId}")
    public List<RegulatoryAppointmentEntity> listForPerson(@PathVariable String personHealthId) {
        return appointmentService.listForPerson(tenantId(), personHealthId);
    }

    @PostMapping("/appointments/{appointmentId}/verify")
    public RegulatoryAppointmentEntity verify(@PathVariable UUID appointmentId) throws Exception {
        return appointmentService.verify(tenantId(), appointmentId, actorId());
    }

    @PostMapping("/appointments/{appointmentId}/end")
    public RegulatoryAppointmentEntity end(
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) OrgRegistryDtos.EndAppointmentRequest request) throws Exception {
        return appointmentService.end(tenantId(), appointmentId,
                request != null ? request.reason() : null, actorId());
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
