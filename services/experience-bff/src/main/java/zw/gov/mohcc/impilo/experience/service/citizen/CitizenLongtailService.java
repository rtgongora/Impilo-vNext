package zw.gov.mohcc.impilo.experience.service.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.CredentialServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.BffCitizenLongtailProperties;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen long-tail orchestration: stub vs sovereign (VITO patient-share, credential verification).
 */
@Service
public class CitizenLongtailService {

    private static final Logger log = LoggerFactory.getLogger(CitizenLongtailService.class);

    private final BffCitizenLongtailProperties properties;
    private final VitoServiceClient vitoServiceClient;
    private final CredentialServiceClient credentialServiceClient;

    public CitizenLongtailService(
            BffCitizenLongtailProperties properties,
            VitoServiceClient vitoServiceClient,
            CredentialServiceClient credentialServiceClient) {
        this.properties = properties;
        this.vitoServiceClient = vitoServiceClient;
        this.credentialServiceClient = credentialServiceClient;
    }

    public Map<String, Object> issueShareData(
            String actorId,
            CitizenLongtailDtos.IssueShareCodeRequest request) {
        if (properties.getMode() == BffCitizenLongtailProperties.Mode.stub) {
            return stubIssueShare(request);
        }
        try {
            UUID healthId = parseRequiredActorHealthId(actorId);
            Map<String, Object> vitoBody = new LinkedHashMap<>();
            vitoBody.put("purposeOfUse", request != null ? request.purpose() : null);
            vitoBody.put("scopeType", "LIMITED_CLINICAL_SUMMARY");
            vitoBody.put("expiryHours", 72);
            vitoBody.put("revocableFlag", true);
            vitoBody.put("oneTimeUse", true);
            JsonNode data = vitoServiceClient.createPatientShare(healthId, vitoBody);
            String shareToken = text(data, "shareToken");
            if (shareToken == null || shareToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "vito patient-share: missing shareToken in response");
            }
            return Map.of(
                    "code", shareToken,
                    "issued_at", OffsetDateTime.now(),
                    "purpose", request != null ? request.purpose() : null);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RestClientException e) {
            return handleDownstreamFailure("issueShare", e, () -> stubIssueShare(request));
        }
    }

    public Map<String, Object> claimShareData(CitizenLongtailDtos.ClaimShareRequest request) {
        if (properties.getMode() == BffCitizenLongtailProperties.Mode.stub) {
            return stubClaimShare(request);
        }
        try {
            String code = request != null ? request.code() : null;
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            JsonNode data = vitoServiceClient.validatePublicPatientShareArtifact(Map.of("shareToken", code.trim()));
            boolean valid = data != null && data.path("valid").asBoolean(false);
            return Map.of(
                    "claimed", valid,
                    "code", code,
                    "claimed_at", OffsetDateTime.now());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RestClientException e) {
            return handleDownstreamFailure("claimShare", e, () -> stubClaimShare(request));
        }
    }

    public Map<String, Object> verifyCredentialData(CitizenLongtailDtos.VerifyCredentialRequest request) {
        if (properties.getMode() == BffCitizenLongtailProperties.Mode.stub) {
            return stubVerifyCredential(request);
        }
        try {
            String token = request != null ? request.payload() : null;
            if (token == null || token.isBlank()) {
                return Map.of("verified", false, "checked_at", OffsetDateTime.now());
            }
            JsonNode data = credentialServiceClient.publicVerifyByToken(token.trim());
            if (data == null || data.isNull()) {
                return Map.of("verified", false, "checked_at", OffsetDateTime.now());
            }
            String status = data.path("status").asText("");
            boolean verified = "VALID".equalsIgnoreCase(status);
            return Map.of("verified", verified, "checked_at", OffsetDateTime.now());
        } catch (RestClientException e) {
            return handleDownstreamFailure("verifyCredential", e, () -> stubVerifyCredential(request));
        }
    }

    public Map<String, Object> issueDelegatedPickupData(CitizenLongtailDtos.IssueDelegatedPickupRequest request) {
        // MSIKA Flow pickup tokens are order-scoped; no stable sovereign mapping yet — keep stub under both modes.
        return stubDelegatedPickup(request);
    }

    private Map<String, Object> handleDownstreamFailure(String op, RestClientException ex, java.util.function.Supplier<Map<String, Object>> stub) {
        log.warn("Citizen long-tail {} downstream failure: {}", op, ex.getMessage());
        if (properties.getFailurePolicy() == BffCitizenLongtailProperties.FailurePolicy.propagate) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "downstream unavailable: " + op, ex);
        }
        return stub.get();
    }

    private static UUID parseRequiredActorHealthId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("X-Actor-ID (Health ID) is required for live patient-share issuance");
        }
        try {
            return UUID.fromString(actorId.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("X-Actor-ID must be a UUID Health ID for live patient-share issuance");
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String s = n.get(field).asText();
        return s.isEmpty() ? null : s;
    }

    private static Map<String, Object> stubIssueShare(CitizenLongtailDtos.IssueShareCodeRequest request) {
        String code = "SHARE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Map.of(
                "code", code,
                "issued_at", OffsetDateTime.now(),
                "purpose", request != null ? request.purpose() : null);
    }

    private static Map<String, Object> stubClaimShare(CitizenLongtailDtos.ClaimShareRequest request) {
        return Map.of(
                "claimed", true,
                "code", request != null ? request.code() : null,
                "claimed_at", OffsetDateTime.now());
    }

    private static Map<String, Object> stubVerifyCredential(CitizenLongtailDtos.VerifyCredentialRequest request) {
        boolean verified = request != null && request.payload() != null && request.payload().length() > 10;
        return Map.of("verified", verified, "checked_at", OffsetDateTime.now());
    }

    private static Map<String, Object> stubDelegatedPickup(CitizenLongtailDtos.IssueDelegatedPickupRequest request) {
        String token = "DP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return Map.of(
                "token", token,
                "delegate_name", request != null ? request.delegate_name() : null,
                "delegate_phone", request != null ? request.delegate_phone() : null,
                "issued_at", OffsetDateTime.now());
    }
}
