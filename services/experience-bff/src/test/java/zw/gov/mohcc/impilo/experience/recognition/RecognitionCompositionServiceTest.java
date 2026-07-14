package zw.gov.mohcc.impilo.experience.recognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dual-source recognition composition: class resolution
 * (LICENSED_PROVIDER / HEALTH_WORKFORCE / BOTH), fail-open on source
 * outage, and the short-TTL cache actually short-circuiting.
 */
@ExtendWith(MockitoExtension.class)
class RecognitionCompositionServiceTest {

    @Mock private VarapiServiceClient varapiClient;
    @Mock private VashandiServiceClient vashandiClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private RecognitionCompositionService service;

    @BeforeEach
    void setUp() {
        service = new RecognitionCompositionService(varapiClient, vashandiClient, 30);
    }

    private ObjectNode providerBody(boolean recognised) {
        ObjectNode n = mapper.createObjectNode();
        n.put("recognised", recognised);
        if (recognised) {
            n.put("licenceStatus", "ACTIVE");
            n.put("profession", "Medical Doctor");
            n.put("cadre", "GP");
        }
        return n;
    }

    private ObjectNode workforceBody(boolean recognised) {
        ObjectNode n = mapper.createObjectNode();
        n.put("recognised", recognised);
        if (recognised) {
            n.put("cadre", "RGN");
            n.put("profession", "Nursing");
        }
        return n;
    }

    @Test
    void licensedProviderOnly() {
        when(varapiClient.getRecognitionByHealthId("h1")).thenReturn(providerBody(true));
        when(vashandiClient.getRecognitionByHealthId("h1")).thenReturn(workforceBody(false));

        var view = service.resolve("h1");

        assertThat(view.recognised()).isTrue();
        assertThat(view.recognitionClass()).isEqualTo("LICENSED_PROVIDER");
        assertThat(view.licenceStatus()).isEqualTo("ACTIVE");
        assertThat(view.profession()).isEqualTo("Medical Doctor");
    }

    @Test
    void workforceOnly() {
        when(varapiClient.getRecognitionByHealthId("h2")).thenReturn(providerBody(false));
        when(vashandiClient.getRecognitionByHealthId("h2")).thenReturn(workforceBody(true));

        var view = service.resolve("h2");

        assertThat(view.recognitionClass()).isEqualTo("HEALTH_WORKFORCE");
        assertThat(view.cadre()).isEqualTo("RGN");
        assertThat(view.licenceStatus()).isNull();
    }

    @Test
    void bothSourcesYieldBoth_providerDetailWins() {
        when(varapiClient.getRecognitionByHealthId("h3")).thenReturn(providerBody(true));
        when(vashandiClient.getRecognitionByHealthId("h3")).thenReturn(workforceBody(true));

        var view = service.resolve("h3");

        assertThat(view.recognitionClass()).isEqualTo("BOTH");
        assertThat(view.profession()).isEqualTo("Medical Doctor");
    }

    @Test
    void neitherSourceRecognises_genericNegative() {
        when(varapiClient.getRecognitionByHealthId("h4")).thenReturn(providerBody(false));
        when(vashandiClient.getRecognitionByHealthId("h4")).thenReturn(workforceBody(false));

        assertThat(service.resolve("h4"))
                .isEqualTo(RecognitionCompositionService.RecognitionView.notRecognised());
    }

    @Test
    void sourceOutageFailsOpenToNotRecognised() {
        when(varapiClient.getRecognitionByHealthId("h5")).thenThrow(new RuntimeException("varapi down"));
        when(vashandiClient.getRecognitionByHealthId("h5")).thenReturn(null);

        assertThat(service.resolve("h5"))
                .isEqualTo(RecognitionCompositionService.RecognitionView.notRecognised());
    }

    @Test
    void secondResolveWithinTtlIsServedFromCache() {
        when(varapiClient.getRecognitionByHealthId("h6")).thenReturn(providerBody(true));
        when(vashandiClient.getRecognitionByHealthId("h6")).thenReturn(workforceBody(false));

        service.resolve("h6");
        service.resolve("h6");

        verify(varapiClient, times(1)).getRecognitionByHealthId("h6");
        verify(vashandiClient, times(1)).getRecognitionByHealthId("h6");
    }

    @Test
    void blankHealthIdIsGenericNegativeWithoutUpstreamCalls() {
        assertThat(service.resolve("  "))
                .isEqualTo(RecognitionCompositionService.RecognitionView.notRecognised());
        verify(varapiClient, times(0)).getRecognitionByHealthId("  ");
    }
}
