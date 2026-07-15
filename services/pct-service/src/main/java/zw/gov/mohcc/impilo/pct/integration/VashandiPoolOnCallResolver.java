package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * READ-ONLY resolution of the on-call trauma team via VASHANDI's existing on-duty virtual-pool
 * endpoint ({@code GET /v1/internal/vashandi/virtual-pools/{poolId}/on-duty}). The trauma panel is
 * modelled as the duty pool {@code trauma-oncall:<facilityId>}; VASHANDI applies the on-duty status
 * filter ('confirmed','checked_in') + shift-window server-side, so this client just consumes the
 * response. No VASHANDI writes; the cadre→role decision belongs to the workforce roster (the seeded
 * {@code shift_type} carries the trauma role), not to PCT.
 */
@Service
public class VashandiPoolOnCallResolver implements VashandiOnCallResolver {

    private static final Logger log = LoggerFactory.getLogger(VashandiPoolOnCallResolver.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VashandiPoolOnCallResolver(
            RestTemplate restTemplate,
            @Value("${pct.integration.vashandi.base-url:http://localhost:8087}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResolvedTeamMember> resolveTraumaTeam(UUID tenantId, UUID facilityId) {
        List<ResolvedTeamMember> members = new ArrayList<>();
        if (facilityId == null) return members;
        String poolId = "trauma-oncall:" + facilityId;
        String url = baseUrl + "/v1/internal/vashandi/virtual-pools/" + poolId + "/on-duty";
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers(tenantId)), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("VASHANDI on-duty for pool {} returned {}", poolId, resp.getStatusCode());
                return members;
            }
            Object onDuty = resp.getBody().get("onDuty");
            if (onDuty instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> m) {
                        Object profile = m.get("workforceProfileId");
                        if (profile == null) continue;
                        members.add(new ResolvedTeamMember(
                                profile.toString(),
                                m.get("shiftType") != null ? m.get("shiftType").toString() : null,
                                m.get("shiftId") != null ? m.get("shiftId").toString() : null));
                    }
                }
            }
            log.info("Resolved {} on-call trauma member(s) for facility {} from VASHANDI pool {}",
                    members.size(), facilityId, poolId);
        } catch (RestClientException e) {
            log.warn("VASHANDI unreachable resolving trauma team for facility {}: {} — activation proceeds with empty panel",
                    facilityId, e.getMessage());
        }
        return members;
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        return h;
    }
}
