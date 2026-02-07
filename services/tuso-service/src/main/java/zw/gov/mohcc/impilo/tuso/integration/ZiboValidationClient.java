package zw.gov.mohcc.impilo.tuso.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.util.Map;

/**
 * Client for ZIBO Terminology Service validation.
 *
 * <p>Validates facility types, workspace types, and capability codes against
 * the authoritative ZIBO terminology service. Supports two modes:
 * <ul>
 *   <li><b>STRICT</b>: throws {@link ZiboValidationException} when validation fails</li>
 *   <li><b>LENIENT</b>: logs a warning, marks the item as {@code zibo_validated = false},
 *       and allows processing to continue</li>
 * </ul>
 *
 * <p>If ZIBO is not available or disabled, all codes are auto-approved but
 * returned as {@code ziboValidated = false}.</p>
 */
@Service
public class ZiboValidationClient {

    private static final Logger log = LoggerFactory.getLogger(ZiboValidationClient.class);

    private final TusoProperties tusoProperties;
    private final RestClient restClient;

    public ZiboValidationClient(TusoProperties tusoProperties,
                                 RestClient.Builder restClientBuilder) {
        this.tusoProperties = tusoProperties;
        this.restClient = restClientBuilder
                .baseUrl(tusoProperties.getZibo().getBaseUrl())
                .build();
    }

    /**
     * Validate a capability code against ZIBO.
     *
     * @param code the capability code to validate
     * @param type the capability type context (e.g. "SERVICE", "EQUIPMENT")
     * @return true if the code is valid or validation is disabled/lenient
     * @throws ZiboValidationException in STRICT mode when validation fails
     */
    public boolean validateCapabilityCode(String code, String type) {
        return validate("capability-codes", code, Map.of("type", type));
    }

    /**
     * Validate a workspace type against ZIBO.
     *
     * @param type the workspace type code to validate
     * @return true if the type is valid or validation is disabled/lenient
     * @throws ZiboValidationException in STRICT mode when validation fails
     */
    public boolean validateWorkspaceType(String type) {
        return validate("workspace-types", type, Map.of());
    }

    /**
     * Validate a facility type against ZIBO.
     *
     * @param type the facility type code to validate
     * @return true if the type is valid or validation is disabled/lenient
     * @throws ZiboValidationException in STRICT mode when validation fails
     */
    public boolean validateFacilityType(String type) {
        return validate("facility-types", type, Map.of());
    }

    /**
     * Core validation logic shared by all validate* methods.
     *
     * @param system     the terminology system (e.g. "facility-types", "capability-codes")
     * @param code       the code to validate
     * @param extraParams any additional query context parameters
     * @return true if valid, or if validation is bypassed
     * @throws ZiboValidationException in STRICT mode when validation fails
     */
    @SuppressWarnings("unchecked")
    boolean validate(String system, String code, Map<String, String> extraParams) {
        if (!tusoProperties.getZibo().isEnabled()) {
            log.debug("ZIBO validation disabled; auto-approving code '{}' in system '{}' (zibo_validated=false)",
                    code, system);
            return true;
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/v1/terminology/validate")
                                .queryParam("system", system)
                                .queryParam("code", code);
                        extraParams.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .retrieve()
                    .body((Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("valid"))) {
                return true;
            }

            String message = response != null
                    ? (String) response.getOrDefault("message", "Unknown validation error")
                    : "Null response from ZIBO";

            return handleValidationFailure(system, code, message);

        } catch (RestClientException e) {
            log.error("ZIBO validation call failed for code '{}' in system '{}': {}",
                    code, system, e.getMessage());
            return handleUnavailable(system, code, e.getMessage());
        }
    }

    private boolean handleValidationFailure(String system, String code, String message) {
        if (tusoProperties.getZibo().isStrict()) {
            log.warn("STRICT validation failed for code '{}' in system '{}': {}", code, system, message);
            throw new ZiboValidationException(
                    String.format("ZIBO validation failed for code '%s' in system '%s': %s",
                            code, system, message));
        }

        log.warn("LENIENT validation: code '{}' in system '{}' is invalid but allowed (zibo_validated=false): {}",
                code, system, message);
        return false;
    }

    private boolean handleUnavailable(String system, String code, String errorMessage) {
        if (tusoProperties.getZibo().isStrict()) {
            throw new ZiboValidationException(
                    String.format("ZIBO unreachable during STRICT validation for code '%s' in system '%s': %s",
                            code, system, errorMessage));
        }

        log.warn("ZIBO unreachable; LENIENT mode allows code '{}' in system '{}' (zibo_validated=false)", code, system);
        return true;
    }

    /**
     * Exception thrown in STRICT mode when ZIBO validation fails or ZIBO is unreachable.
     */
    public static class ZiboValidationException extends RuntimeException {
        public ZiboValidationException(String message) {
            super(message);
        }
    }
}
