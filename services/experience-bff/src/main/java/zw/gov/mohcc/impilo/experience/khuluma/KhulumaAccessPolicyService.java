package zw.gov.mohcc.impilo.experience.khuluma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Tshepo-aligned access gate for Khuluma comms in the Experience BFF — the defense-in-depth layer
 * above edge ext_authz (the authoritative governed decision lives in {@code infra/opa/impilo/khuluma.rego}
 * + the {@code V017} policy_rule seed). Mirrors {@code ImagingAccessPolicyService}: core comms is
 * MINIMAL-friction (any authenticated actor may converse/call), while binding a conversation to a
 * patient is a regulated clinical action requiring a clinical-plane role.
 */
@Service
public class KhulumaAccessPolicyService {

    private static final Logger log = LoggerFactory.getLogger(KhulumaAccessPolicyService.class);

    private static final Set<String> CLINICAL_AUTHORITIES = Set.of(
            "ROLE_CLINICIAN",
            "ROLE_NURSE",
            "ROLE_DOCTOR",
            "ROLE_FACILITY_ADMIN",
            "ROLE_SYSTEM_ADMIN",
            "ROLE_DEVELOPER"
    );

    /** Core comms (create conversation, send message, presence) — any authenticated actor. */
    public void requireCommsActor() {
        requireAuthenticated("comms");
    }

    /** Starting a 1:1 call — any authenticated actor (graduated friction stays MINIMAL/MEDIUM). */
    public void requireCallActor() {
        requireAuthenticated("call");
    }

    /**
     * Binding a conversation to a patient record is a regulated clinical action: requires a
     * clinical-plane role, matching {@code comms.conversation.link_patient} in khuluma.rego.
     */
    public void requireClinicalActorForPatientLink() {
        Authentication auth = requireAuthenticated("patient-link");
        boolean clinical = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(CLINICAL_AUTHORITIES::contains);
        if (!clinical) {
            log.warn("Khuluma patient-link denied for principal={} — missing clinical-plane role", auth.getName());
            throw new AccessDeniedException("Clinical role required to bind a conversation to a patient");
        }
    }

    private Authentication requireAuthenticated(String op) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required for Khuluma " + op);
        }
        return auth;
    }
}
