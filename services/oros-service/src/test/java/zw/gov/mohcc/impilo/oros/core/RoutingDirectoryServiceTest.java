package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.api.dto.DestinationDto;
import zw.gov.mohcc.impilo.oros.integration.TusoClient;
import zw.gov.mohcc.impilo.oros.integration.VarapiClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingDirectoryServiceTest {

    @Mock private TusoClient tusoClient;
    @Mock private VarapiClient varapiClient;

    @Test
    @DisplayName("aggregates internal facilities (TUSO) and external providers (VARAPI)")
    void aggregatesBothSources() {
        when(tusoClient.searchFacilities()).thenReturn(List.of(
                Map.of("id", "fac-1", "name", "Parirenyatwa")));
        when(varapiClient.searchProviders()).thenReturn(List.of(
                Map.of("providerPublicId", "prov-9", "givenName", "Jane", "familyName", "Doe", "profession", "Radiologist")));

        List<DestinationDto> dests = new RoutingDirectoryService(tusoClient, varapiClient).destinations();

        assertThat(dests).hasSize(2);
        assertThat(dests).anySatisfy(d -> {
            assertThat(d.destinationType()).isEqualTo("EXTERNAL_FACILITY");
            assertThat(d.id()).isEqualTo("fac-1");
            assertThat(d.name()).isEqualTo("Parirenyatwa");
        });
        assertThat(dests).anySatisfy(d -> {
            assertThat(d.destinationType()).isEqualTo("EXTERNAL_PROVIDER");
            assertThat(d.id()).isEqualTo("prov-9");
            assertThat(d.name()).isEqualTo("Jane Doe (Radiologist)");
        });
    }

    @Test
    @DisplayName("returns empty when neither registry is configured/reachable")
    void emptyWhenSourcesEmpty() {
        when(tusoClient.searchFacilities()).thenReturn(List.of());
        when(varapiClient.searchProviders()).thenReturn(List.of());

        assertThat(new RoutingDirectoryService(tusoClient, varapiClient).destinations()).isEmpty();
    }
}
