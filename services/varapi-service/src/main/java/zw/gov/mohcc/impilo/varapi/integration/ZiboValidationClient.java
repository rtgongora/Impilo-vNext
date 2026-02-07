package zw.gov.mohcc.impilo.varapi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

/**
 * REST client for ZIBO (Terminology Service) validation of clinical codes.
 *
 * <p>Supports two operational modes configured via {@code varapi.zibo.validation-mode}:</p>
 * <ul>
 *   <li><b>STRICT</b> — rejects the request when a code cannot be validated
 *       (ZIBO unavailable or code not found).</li>
 *   <li><b>LENIENT</b> (default) — logs a warning but allows the request to proceed
 *       when validation cannot be completed.</li>
 * </ul>
 *
 * <p>When {@code varapi.zibo.enabled=false}, all validation methods return {@code true}
 * unconditionally, allowing VARAPI to function without a running ZIBO instance.</p>
 */
@Service
public class ZiboValidationClient {

    private static final Logger log = LoggerFactory.getLogger(ZiboValidationClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public ZiboValidationClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    /**
     * Validate a specialty code against the ZIBO terminology service.
     *
     * @param code the specialty code to validate
     * @return {@code true} if the code is valid (or validation is disabled/lenient)
     */
    public boolean validateSpecialtyCode(String code) {
        return validateCode("specialty", code);
    }

    /**
     * Validate a cadre type against the ZIBO terminology service.
     *
     * @param cadre the cadre type to validate (e.g., "DOCTOR", "NURSE")
     * @return {@code true} if the cadre is valid (or validation is disabled/lenient)
     */
    public boolean validateCadreType(String cadre) {
        return validateCode("cadre", cadre);
    }

    /**
     * Validate a council type against the ZIBO terminology service.
     *
     * @param type the council type to validate (e.g., "MEDICAL", "NURSING")
     * @return {@code true} if the type is valid (or validation is disabled/lenient)
     */
    public boolean validateCouncilType(String type) {
        return validateCode("council-type", type);
    }

    /**
     * Internal helper that performs a GET validation call against ZIBO for a given
     * code system and code value.
     *
     * @param system the ZIBO code system (e.g., "specialty", "cadre", "council-type")
     * @param code   the code value to validate
     * @return {@code true} if the code is valid or validation is bypassed
     */
    private boolean validateCode(String system, String code) {
        if (!varapiProperties.getZibo().isEnabled()) {
            return true;
        }
        if (code == null || code.isBlank()) {
            log.warn("ZIBO validation called with null/blank code for system={}", system);
            return false;
        }
        try {
            String url = varapiProperties.getZibo().getBaseUrl()
                    + "/v1/internal/validate/code?system=" + system + "&code=" + code;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            if (varapiProperties.getZibo().isStrict()) {
                log.error("ZIBO validation failed for system={}, code={}: {}",
                        system, code, e.getMessage());
                return false;
            }
            log.warn("ZIBO validation unavailable for system={}, code={}; LENIENT mode — allowing",
                    system, code);
            return true;
        }
    }
}
