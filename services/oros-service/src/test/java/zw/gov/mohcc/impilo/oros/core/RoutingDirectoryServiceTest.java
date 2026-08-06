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
        // claimed=true is now part of being a destination: this practitioner has opted in to
        // receiving requests here. Without it the entry is correctly withheld.
        when(varapiClient.searchProviders()).thenReturn(List.of(
                Map.of("providerPublicId", "prov-9", "givenName", "Jane", "familyName", "Doe",
                        "profession", "Radiologist", "claimed", true)));

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

    // ── The register is wider than the platform ───────────────────────────────────────────
    // Provider search returns every practitioner on the Health Professions Authority roll,
    // because registration is what makes someone searchable and verifiable. Being reachable
    // is a separate opt-in, and a routing destination is by definition somewhere work can be
    // sent — so the directory must be the narrower of the two.

    @Test
    @DisplayName("a registered practitioner who has not opted in is not offered as a destination")
    void unclaimedPractitionerIsNotADestination() {
        when(tusoClient.searchFacilities()).thenReturn(List.of());
        when(varapiClient.searchProviders()).thenReturn(List.of(
                Map.of("providerPublicId", "PROV-ZW-04241", "givenName", "Tendai",
                        "familyName", "Moyo", "profession", "Radiologist",
                        "status", "INACTIVE", "lifecycleStatus", "PRELOADED", "claimed", false)));

        assertThat(new RoutingDirectoryService(tusoClient, varapiClient).destinations())
                .as("routing an order to someone who never joined sends it into silence — it "
                        + "would look dispatched and simply stop")
                .isEmpty();
    }

    @Test
    @DisplayName("participation that cannot be determined fails closed")
    void unknownParticipationIsNotADestination() {
        when(tusoClient.searchFacilities()).thenReturn(List.of());
        when(varapiClient.searchProviders()).thenReturn(List.of(
                Map.of("providerPublicId", "prov-legacy", "givenName", "A", "familyName", "B")));

        assertThat(new RoutingDirectoryService(tusoClient, varapiClient).destinations())
                .as("a missing field is not evidence of consent")
                .isEmpty();
    }

    @Test
    @DisplayName("a claimedAt timestamp counts as the opt-in")
    void claimedAtTimestampCountsAsOptIn() {
        when(tusoClient.searchFacilities()).thenReturn(List.of());
        when(varapiClient.searchProviders()).thenReturn(List.of(
                Map.of("providerPublicId", "prov-7", "givenName", "C", "familyName", "D",
                        "claimedAt", "2026-08-06T04:00:00Z")));

        assertThat(new RoutingDirectoryService(tusoClient, varapiClient).destinations())
                .extracting(DestinationDto::id).containsExactly("prov-7");
    }
}
