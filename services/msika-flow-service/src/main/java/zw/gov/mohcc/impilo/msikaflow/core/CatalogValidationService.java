package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.integration.MsikaCoreClient;
import zw.gov.mohcc.impilo.msikaflow.integration.TusoClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VarapiClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VitoClient;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class CatalogValidationService {

    private static final Logger log = LoggerFactory.getLogger(CatalogValidationService.class);

    private final MsikaCoreClient msikaCoreClient;
    private final TusoClient tusoClient;
    private final VarapiClient varapiClient;
    private final VitoClient vitoClient;
    private final ObjectMapper objectMapper;

    private static final Pattern REF_PREFIX = Pattern.compile("^[a-zA-Z0-9_-]+:");

    public CatalogValidationService(MsikaCoreClient msikaCoreClient,
                                    TusoClient tusoClient,
                                    VarapiClient varapiClient,
                                    VitoClient vitoClient,
                                    ObjectMapper objectMapper) {
        this.msikaCoreClient = msikaCoreClient;
        this.tusoClient = tusoClient;
        this.varapiClient = varapiClient;
        this.vitoClient = vitoClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates a list of cart items against MSIKA Core catalog restrictions.
     * Returns a ValidationResult with per-item results.
     */
    public ValidationResult validateCart(List<CartItem> items,
                                         String channel,
                                         ActorType actorType,
                                         String patientCpid,
                                         String facilityRef,
                                         String providerRef) {
        List<ItemValidation> results = new ArrayList<>();
        boolean allValid = true;

        for (CartItem item : items) {
            ItemValidation validation = validateItem(item, channel, actorType, patientCpid, facilityRef, providerRef);
            results.add(validation);
            if (!validation.valid()) {
                allValid = false;
            }
        }

        return new ValidationResult(allValid, results);
    }

    private ItemValidation validateItem(CartItem item,
                                        String channel,
                                        ActorType actorType,
                                        String patientCpid,
                                        String facilityRef,
                                        String providerRef) {
        List<String> errors = new ArrayList<>();
        boolean professionalSelfAuthorized = false;

        try {
            JsonNode catalogItem = msikaCoreClient.lookupItem(item.msikaCoreCode());
            if (catalogItem == null || catalogItem.isMissingNode()) {
                return new ItemValidation(item.msikaCoreCode(), false,
                        List.of("Item not found in MSIKA Core catalog"), null, false);
            }

            JsonNode restrictions = catalogItem.path("restrictions");

            // Risk classification + governed friction rule (fetched once; also
            // drives the V009 professional_self_authorizable convenience).
            String riskClassification = firstNonBlank(
                    catalogItem.path("riskClassification").asText(null),
                    catalogItem.path("risk_classification").asText(null),
                    restrictions.path("risk_classification").asText(null));
            JsonNode friction = riskClassification != null
                    ? msikaCoreClient.getRiskFriction(riskClassification)
                    : null;
            boolean proSelfAuthorizableClass = friction != null
                    && friction.path("professionalSelfAuthorizable").asBoolean(false);

            // Check prescription_required
            if (restrictions.path("prescription_required").asBoolean(false)) {
                if (actorType == ActorType.PATIENT) {
                    boolean ok = verifyPatientIdentity(patientCpid, 1, errors);
                    if (!ok) {
                        // keep specific errors from identity summary
                    }
                }
                if (!"facility".equalsIgnoreCase(channel)) {
                    errors.add("Prescription required — order must be initiated in a facility context");
                } else {
                    verifyFacilityLegitimacy(facilityRef, errors);
                }
            }

            // Check controlled_item
            if (restrictions.path("controlled_item").asBoolean(false)) {
                if (actorType == ActorType.PATIENT) {
                    verifyPatientIdentity(patientCpid, 2, errors);
                }
                // V009 convenience: for flagged classes ONLY, a recognised
                // licensed provider buying for themselves self-satisfies the
                // prescriber-standing requirement (identity checks above are
                // unchanged; facility legitimacy below is unchanged).
                if (isBlankRef(providerRef) && proSelfAuthorizableClass
                        && buyerIsRecognisedLicensedProvider(patientCpid)) {
                    professionalSelfAuthorized = true;
                } else {
                    verifyProviderStanding(providerRef, errors);
                }
                verifyFacilityLegitimacy(facilityRef, errors);
            }

            // Check facility_only
            if (restrictions.path("facility_only").asBoolean(false)) {
                if (!"facility".equals(channel)) {
                    errors.add("This item can only be ordered at a facility");
                } else {
                    verifyFacilityLegitimacy(facilityRef, errors);
                }
            }

            // Check provider_only_order
            if (restrictions.path("provider_only_order").asBoolean(false)) {
                if (actorType != ActorType.PROVIDER && actorType != ActorType.OPS) {
                    if (proSelfAuthorizableClass && buyerIsRecognisedLicensedProvider(patientCpid)) {
                        professionalSelfAuthorized = true;
                    } else {
                        errors.add("This item can only be ordered by a provider");
                    }
                } else {
                    verifyProviderStanding(providerRef, errors);
                }
            }

            // Risk-friction: enforce the per-order quantity ceiling configured
            // for the item's marketplace risk classification (msika-service
            // msika_risk_friction_mapping). No rule / unreachable ⇒ no ceiling.
            if (friction != null && !friction.isMissingNode()) {
                int maxQty = friction.path("maxQtyPerOrder").asInt(0);
                if (maxQty > 0 && item.qty() > maxQty) {
                    errors.add("Quantity " + item.qty() + " exceeds the per-order maximum of "
                            + maxQty + " for " + riskClassification + " items");
                }
            }

            // Check channel restrictions
            JsonNode allowedChannels = restrictions.path("allowed_channels");
            if (allowedChannels.isArray() && allowedChannels.size() > 0) {
                boolean channelAllowed = false;
                for (JsonNode ch : allowedChannels) {
                    if (ch.asText().equalsIgnoreCase(channel)) {
                        channelAllowed = true;
                        break;
                    }
                }
                if (!channelAllowed) {
                    errors.add("Item not available on channel: " + channel);
                }
            }

            String restrictionsJson = null;
            try {
                restrictionsJson = objectMapper.writeValueAsString(restrictions);
            } catch (Exception e) {
                log.warn("Failed to serialize restrictions for {}", item.msikaCoreCode());
            }

            return new ItemValidation(item.msikaCoreCode(), errors.isEmpty(), errors,
                    restrictionsJson, professionalSelfAuthorized && errors.isEmpty());

        } catch (Exception e) {
            log.error("Error validating item {}: {}", item.msikaCoreCode(), e.getMessage());
            return new ItemValidation(item.msikaCoreCode(), false,
                    List.of("Catalog validation error: " + e.getMessage()), null, false);
        }
    }

    /**
     * V009 professional self-authorization: is the BUYER (patientCpid carries
     * the Health ID) a recognised licensed provider in good standing per
     * VARAPI recognition? FAIL CLOSED: null / unreachable / not recognised
     * all mean NO — a down registry never authorizes a regulated purchase.
     * Workforce-only recognition deliberately does NOT satisfy prescriber
     * standing (only VARAPI licensed providers are consulted).
     */
    private boolean buyerIsRecognisedLicensedProvider(String buyerHealthId) {
        if (buyerHealthId == null || buyerHealthId.isBlank()) {
            return false;
        }
        JsonNode recognition = varapiClient.getRecognitionByHealthId(buyerHealthId);
        return recognition != null && recognition.path("recognised").asBoolean(false);
    }

    private static boolean isBlankRef(String ref) {
        return ref == null || ref.isBlank();
    }

    private void verifyFacilityLegitimacy(String facilityRef, List<String> errors) {
        Long facilityId = parseNumericRef(facilityRef);
        if (facilityId == null) {
            errors.add("Facility context missing — facilityRef is required for this item");
            return;
        }
        JsonNode summary = tusoClient.getFacilityStatusSummary(facilityId);
        if (summary == null || summary.isMissingNode()) {
            errors.add("Unable to verify facility legitimacy at this time");
            return;
        }
        String status = summary.path("status").asText(null);
        if (status == null || !"ACTIVE".equalsIgnoreCase(status)) {
            errors.add("Facility is not ACTIVE — cannot place regulated order");
        }
    }

    private void verifyProviderStanding(String providerRef, List<String> errors) {
        String providerPublicId = parseStringRef(providerRef);
        if (providerPublicId == null || providerPublicId.isBlank()) {
            errors.add("Provider context missing — providerRef is required for this item");
            return;
        }
        JsonNode summary = varapiClient.getStandingSummary(providerPublicId);
        if (summary == null || summary.isMissingNode()) {
            errors.add("Unable to verify provider standing at this time");
            return;
        }
        boolean active = summary.path("active").asBoolean(false);
        boolean hasValidLicense = summary.path("hasValidLicense").asBoolean(false);
        if (!active) errors.add("Provider is not ACTIVE — cannot authorize regulated items");
        if (!hasValidLicense) errors.add("Provider does not have a valid license — cannot authorize regulated items");
    }

    private boolean verifyPatientIdentity(String patientCpid, int minAssurance, List<String> errors) {
        UUID healthId = parseUuid(patientCpid);
        if (healthId == null) {
            errors.add("Patient identity missing — healthId required for regulated items");
            return false;
        }
        JsonNode summary = vitoClient.getIdentitySummary(healthId);
        if (summary == null || summary.isMissingNode()) {
            errors.add("Unable to verify patient identity at this time");
            return false;
        }
        String verificationStatus = summary.path("verificationStatus").asText(null);
        int assurance = summary.path("identityAssuranceLevel").asInt(0);
        if (verificationStatus == null || !"VERIFIED".equalsIgnoreCase(verificationStatus)) {
            errors.add("Patient identity not VERIFIED — cannot place regulated order");
            return false;
        }
        if (assurance < minAssurance) {
            errors.add("Patient identity assurance too low (required >= " + minAssurance + ")");
            return false;
        }
        return true;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) return v;
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long parseNumericRef(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String v = REF_PREFIX.matcher(ref) .find() ? ref.substring(ref.indexOf(':') + 1) : ref;
        try {
            return Long.parseLong(v.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String parseStringRef(String ref) {
        if (ref == null || ref.isBlank()) return null;
        return REF_PREFIX.matcher(ref).find() ? ref.substring(ref.indexOf(':') + 1) : ref;
    }

    public record CartItem(String msikaCoreCode, int qty) {}
    public record ItemValidation(String msikaCoreCode, boolean valid, List<String> errors,
                                 String restrictionsSnapshot,
                                 /** V009: prescriber standing satisfied by the buyer's own recognised licence. */
                                 boolean professionalSelfAuthorized) {}
    public record ValidationResult(boolean valid, List<ItemValidation> items) {}
}
