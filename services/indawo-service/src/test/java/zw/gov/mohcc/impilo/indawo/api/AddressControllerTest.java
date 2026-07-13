package zw.gov.mohcc.impilo.indawo.api;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.indawo.api.dto.AddressResponse;
import zw.gov.mohcc.impilo.indawo.api.dto.CreateAddressRequest;
import zw.gov.mohcc.impilo.indawo.domain.AddressEntity;
import zw.gov.mohcc.impilo.indawo.integration.NdilaGeocodeClient;
import zw.gov.mohcc.impilo.indawo.repository.AddressRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressControllerTest {

    private static final String TENANT = UUID.randomUUID().toString();

    private CreateAddressRequest request(BigDecimal lat, BigDecimal lng) {
        return new CreateAddressRequest("PHYSICAL", "12 Samora Machel Ave", null, "CBD", "Harare",
                "Harare", "Harare", "00263", lat, lng);
    }

    @Test
    void create_geocodes_via_ndila_when_no_coordinates_supplied() {
        AddressRepository repo = mock(AddressRepository.class);
        NdilaGeocodeClient geo = mock(NdilaGeocodeClient.class);
        when(geo.geocode(anyString(), anyString(), anyString()))
                .thenReturn(new NdilaGeocodeClient.GeocodeResult(
                        new BigDecimal("-17.8292"), new BigDecimal("31.0522"), "HIGH", true));
        when(repo.save(any(AddressEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var controller = new AddressController(repo, geo);
        AddressResponse body = controller.createAddress(TENANT, "national-spine", request(null, null)).getBody();

        assertThat(body.latitude()).isEqualByComparingTo("-17.8292");
        assertThat(body.longitude()).isEqualByComparingTo("31.0522");
        assertThat(body.geocodeQuality()).isEqualTo("HIGH");
        assertThat(body.verified()).isTrue();
        verify(geo).geocode(anyString(), anyString(), anyString());
    }

    @Test
    void create_keeps_supplied_coordinates_as_manual_without_geocoding() {
        AddressRepository repo = mock(AddressRepository.class);
        NdilaGeocodeClient geo = mock(NdilaGeocodeClient.class);
        when(repo.save(any(AddressEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var controller = new AddressController(repo, geo);
        AddressResponse body = controller.createAddress(TENANT, "national-spine",
                request(new BigDecimal("-18.0"), new BigDecimal("31.0"))).getBody();

        assertThat(body.geocodeQuality()).isEqualTo("MANUAL");
        assertThat(body.latitude()).isEqualByComparingTo("-18.0");
        verify(geo, never()).geocode(anyString(), any(), any());
    }

    @Test
    void create_persists_unlocated_when_ndila_returns_no_match() {
        AddressRepository repo = mock(AddressRepository.class);
        NdilaGeocodeClient geo = mock(NdilaGeocodeClient.class);
        when(geo.geocode(anyString(), anyString(), anyString())).thenReturn(null);
        when(repo.save(any(AddressEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var controller = new AddressController(repo, geo);
        AddressResponse body = controller.createAddress(TENANT, "national-spine", request(null, null)).getBody();

        // Honest: no fabricated coordinates when the geocoder can't resolve.
        assertThat(body.latitude()).isNull();
        assertThat(body.geocodeQuality()).isNull();
        assertThat(body.verified()).isFalse();
    }
}
