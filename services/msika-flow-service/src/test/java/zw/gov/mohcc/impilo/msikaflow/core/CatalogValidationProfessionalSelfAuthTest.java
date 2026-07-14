package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.integration.MsikaCoreClient;
import zw.gov.mohcc.impilo.msikaflow.integration.TusoClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VarapiClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VitoClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V009 professional self-authorization (allow/deny): a recognised licensed
 * provider buying for themselves satisfies the prescriber-standing
 * requirement ONLY for risk classes flagged professional_self_authorizable,
 * fail-closed on registry outage, identity checks unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogValidationProfessionalSelfAuthTest {

    @Mock private MsikaCoreClient msikaCoreClient;
    @Mock private TusoClient tusoClient;
    @Mock private VarapiClient varapiClient;
    @Mock private VitoClient vitoClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private CatalogValidationService service;

    private final String buyerHealthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new CatalogValidationService(msikaCoreClient, tusoClient, varapiClient, vitoClient, mapper);
    }

    private ObjectNode providerOnlyItem(String riskClass) {
        ObjectNode item = mapper.createObjectNode();
        item.put("riskClassification", riskClass);
        ObjectNode restrictions = item.putObject("restrictions");
        restrictions.put("provider_only_order", true);
        return item;
    }

    private ObjectNode friction(boolean selfAuthorizable) {
        ObjectNode f = mapper.createObjectNode();
        f.put("frictionLevel", "ELEVATED");
        f.put("professionalSelfAuthorizable", selfAuthorizable);
        return f;
    }

    private ObjectNode recognition(boolean recognised) {
        ObjectNode r = mapper.createObjectNode();
        r.put("recognised", recognised);
        return r;
    }

    private CatalogValidationService.ValidationResult validateAsPatientBuyer() {
        return service.validateCart(
                List.of(new CatalogValidationService.CartItem("MED-RX-1", 1)),
                "web", ActorType.PATIENT, buyerHealthId, null, null);
    }

    @Test
    void recognisedProviderBuyerAllowedOnFlaggedClass() {
        when(msikaCoreClient.lookupItem("MED-RX-1")).thenReturn(providerOnlyItem("HIGH_RISK"));
        when(msikaCoreClient.getRiskFriction("HIGH_RISK")).thenReturn(friction(true));
        when(varapiClient.getRecognitionByHealthId(buyerHealthId)).thenReturn(recognition(true));

        var result = validateAsPatientBuyer();

        assertThat(result.valid()).isTrue();
        assertThat(result.items().get(0).professionalSelfAuthorized()).isTrue();
    }

    @Test
    void nonProviderBuyerDeniedOnFlaggedClass() {
        when(msikaCoreClient.lookupItem("MED-RX-1")).thenReturn(providerOnlyItem("HIGH_RISK"));
        when(msikaCoreClient.getRiskFriction("HIGH_RISK")).thenReturn(friction(true));
        when(varapiClient.getRecognitionByHealthId(buyerHealthId)).thenReturn(recognition(false));

        var result = validateAsPatientBuyer();

        assertThat(result.valid()).isFalse();
        assertThat(result.items().get(0).errors())
                .anyMatch(e -> e.contains("only be ordered by a provider"));
        assertThat(result.items().get(0).professionalSelfAuthorized()).isFalse();
    }

    @Test
    void unflaggedClassNeverSelfAuthorizes_evenForRecognisedProvider() {
        when(msikaCoreClient.lookupItem("MED-RX-1")).thenReturn(providerOnlyItem("REGULATED"));
        when(msikaCoreClient.getRiskFriction("REGULATED")).thenReturn(friction(false));
        when(varapiClient.getRecognitionByHealthId(buyerHealthId)).thenReturn(recognition(true));

        var result = validateAsPatientBuyer();

        assertThat(result.valid()).isFalse();
        // The recognition lookup is not even consulted when the class is unflagged.
        verify(varapiClient, never()).getRecognitionByHealthId(any());
    }

    @Test
    void registryOutageFailsClosed() {
        when(msikaCoreClient.lookupItem("MED-RX-1")).thenReturn(providerOnlyItem("HIGH_RISK"));
        when(msikaCoreClient.getRiskFriction("HIGH_RISK")).thenReturn(friction(true));
        when(varapiClient.getRecognitionByHealthId(buyerHealthId)).thenReturn(null); // VARAPI down

        var result = validateAsPatientBuyer();

        assertThat(result.valid()).isFalse();
    }

    @Test
    void controlledItemIdentityVerificationIsUnchangedBySelfAuthorization() {
        // controlled_item: identity must still be VERIFIED even when standing
        // is self-satisfied — self-authorization touches ONLY the prescriber-
        // standing requirement.
        ObjectNode item = mapper.createObjectNode();
        item.put("riskClassification", "HIGH_RISK");
        ObjectNode restrictions = item.putObject("restrictions");
        restrictions.put("controlled_item", true);
        when(msikaCoreClient.lookupItem("MED-RX-1")).thenReturn(item);
        when(msikaCoreClient.getRiskFriction("HIGH_RISK")).thenReturn(friction(true));
        when(varapiClient.getRecognitionByHealthId(buyerHealthId)).thenReturn(recognition(true));
        // VITO identity: NOT verified.
        ObjectNode identity = mapper.createObjectNode();
        identity.put("verificationStatus", "PENDING");
        identity.put("identityAssuranceLevel", 0);
        when(vitoClient.getIdentitySummary(any())).thenReturn(identity);
        // Facility: absent — controlled items also demand facility context.

        var result = validateAsPatientBuyer();

        assertThat(result.valid()).isFalse();
        assertThat(result.items().get(0).errors())
                .anyMatch(e -> e.contains("identity not VERIFIED"));
    }
}
