package zw.gov.mohcc.impilo.experience.imaging;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Tshepo-aligned imaging access gate for the Experience BFF (layer above Spring Security URL RBAC).
 * URL rules still require clinical roles; this service enforces patient–study binding when an
 * explicit chart context is supplied.
 */
@Service
public class ImagingAccessPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ImagingAccessPolicyService.class);

    private static final Set<String> CLINICAL_AUTHORITIES = Set.of(
            "ROLE_CLINICIAN",
            "ROLE_NURSE",
            "ROLE_FACILITY_ADMIN",
            "ROLE_SYSTEM_ADMIN",
            "ROLE_DEVELOPER"
    );

    /** Roles allowed to see raw networking (hosts, ports, AE titles) and mutate imaging infrastructure config. */
    private static final Set<String> TECHNICAL_ADMIN_AUTHORITIES = Set.of(
            "ROLE_FACILITY_ADMIN",
            "ROLE_SYSTEM_ADMIN",
            "ROLE_DEVELOPER"
    );

    public boolean isTechnicalImagingAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(TECHNICAL_ADMIN_AUTHORITIES::contains);
    }

    public void requireTechnicalImagingAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required for imaging administration");
        }
        if (!isTechnicalImagingAdmin()) {
            log.warn("Imaging admin access denied for principal={} — missing technical/admin role", auth.getName());
            throw new AccessDeniedException("Technical/admin role required for imaging infrastructure configuration");
        }
    }

    public void requireClinicalImagingActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required for imaging");
        }
        boolean clinical = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(CLINICAL_AUTHORITIES::contains);
        if (!clinical) {
            log.warn("Imaging access denied for principal={} — missing clinical plane role", auth.getName());
            throw new AccessDeniedException("Clinical role required for governed imaging operations");
        }
    }

    /**
     * When {@code chartPatientCpid} is present, ensures the study row returned by PACS adapter belongs to that patient.
     */
    public void assertStudyMatchesPatient(JsonNode studyJson, String chartPatientCpid) {
        if (chartPatientCpid == null || chartPatientCpid.isBlank()) {
            return;
        }
        String studyPatient = studyJson.path("patientCpid").asText("");
        if (studyPatient.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study has no patient anchor");
        }
        if (!chartPatientCpid.equalsIgnoreCase(studyPatient)) {
            log.warn("Blocked governed imaging cross-patient access chart={} studyPatient={}",
                    chartPatientCpid, studyPatient);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study does not belong to chart patient context");
        }
    }
}
