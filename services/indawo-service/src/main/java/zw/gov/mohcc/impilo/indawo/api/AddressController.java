package zw.gov.mohcc.impilo.indawo.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.indawo.api.dto.AddressResponse;
import zw.gov.mohcc.impilo.indawo.api.dto.CreateAddressRequest;
import zw.gov.mohcc.impilo.indawo.domain.AddressEntity;
import zw.gov.mohcc.impilo.indawo.integration.NdilaGeocodeClient;
import zw.gov.mohcc.impilo.indawo.repository.AddressRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/internal/v1/addresses")
public class AddressController {

    private final AddressRepository addressRepository;
    private final NdilaGeocodeClient geocodeClient;

    public AddressController(AddressRepository addressRepository, NdilaGeocodeClient geocodeClient) {
        this.addressRepository = addressRepository;
        this.geocodeClient = geocodeClient;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<AddressResponse> addresses = addressRepository
                .findByTenantId(UUID.fromString(tenantId))
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(addresses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getAddress(@PathVariable UUID id) {
        return addressRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @Valid @RequestBody CreateAddressRequest request) {
        AddressEntity address = new AddressEntity(
                UUID.fromString(tenantId), podId,
                request.addressType(), request.line1(), request.province());
        address.setLine2(request.line2());
        address.setSuburb(request.suburb());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setPostalCode(request.postalCode());

        if (request.latitude() != null && request.longitude() != null) {
            // Caller supplied coordinates → treat as MANUAL (operator-provided), unverified.
            address.setLatitude(request.latitude());
            address.setLongitude(request.longitude());
            address.setGeocodeQuality("MANUAL");
        } else {
            // G21: delegate geocoding to ndila (the geography SoR) instead of leaving lat/long null.
            String query = Stream.of(request.line1(), request.suburb(), request.city(), request.province())
                    .filter(s -> s != null && !s.isBlank())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(request.line1());
            NdilaGeocodeClient.GeocodeResult geo = geocodeClient.geocode(query, request.province(), request.district());
            if (geo != null) {
                address.setLatitude(geo.latitude());
                address.setLongitude(geo.longitude());
                address.setGeocodeQuality(geo.quality());
                address.setVerified(geo.verified());
            }
            // else: no coordinates — honest, address persists unlocated (geocode_quality stays null).
        }

        addressRepository.save(address);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(address));
    }

    private AddressResponse toResponse(AddressEntity entity) {
        return new AddressResponse(
                entity.getId(),
                entity.getAddressType(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getSuburb(),
                entity.getCity(),
                entity.getDistrict(),
                entity.getProvince(),
                entity.getCountry(),
                entity.getPostalCode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getGeocodeQuality(),
                entity.isVerified()
        );
    }
}
