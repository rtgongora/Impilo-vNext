package zw.gov.mohcc.impilo.ndila.core.location;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical Ndila location service. Creates, updates and verifies location
 * records that mirror or own the geospatial representation of entities from
 * Tuso, Indawo, Varapi, Vito, Nhume, etc.
 */
@Service
public class NdilaLocationService {

    private final NdilaLocationRepository repository;
    private final NdilaOutboxPublisher outbox;

    public NdilaLocationService(NdilaLocationRepository repository, NdilaOutboxPublisher outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public NdilaLocationEntity create(CreateLocation cmd, UUID correlationId) {
        NdilaLocationEntity entity = new NdilaLocationEntity(
                cmd.tenantId(), cmd.ownerService(), cmd.ownerEntityType(),
                cmd.name(), cmd.locationType());
        entity.setOwnerEntityId(cmd.ownerEntityId());
        entity.setDescription(cmd.description());
        entity.setLatitude(cmd.latitude());
        entity.setLongitude(cmd.longitude());
        entity.setAltitudeMeters(cmd.altitudeMeters());
        entity.setAddressLine1(cmd.addressLine1());
        entity.setLocality(cmd.locality());
        entity.setWard(cmd.ward());
        entity.setDistrict(cmd.district());
        entity.setProvince(cmd.province());
        entity.setCountry(cmd.country() == null ? "ZWE" : cmd.country());
        entity.setSource(cmd.source() == null ? "USER_ENTERED" : cmd.source());
        entity.setSensitivityLevel(cmd.sensitivityLevel() == null ? "PUBLIC" : cmd.sensitivityLevel());
        entity.setCoordinateAccuracyMeters(cmd.coordinateAccuracyMeters());
        repository.save(entity);

        outbox.publish(buildEvent(NdilaEventTopics.LOCATION_CREATED, entity, correlationId));
        return entity;
    }

    @Transactional
    public Optional<NdilaLocationEntity> updateCoordinates(UUID id, BigDecimal latitude, BigDecimal longitude,
                                                          BigDecimal accuracyMeters, String source,
                                                          UUID correlationId) {
        return repository.findById(id).map(entity -> {
            entity.setLatitude(latitude);
            entity.setLongitude(longitude);
            entity.setCoordinateAccuracyMeters(accuracyMeters);
            if (source != null) entity.setSource(source);
            entity.touch();
            outbox.publish(buildEvent(NdilaEventTopics.LOCATION_UPDATED, entity, correlationId));
            return entity;
        });
    }

    @Transactional
    public Optional<NdilaLocationEntity> verify(UUID id, String verifiedBy, String verificationStatus,
                                                UUID correlationId) {
        return repository.findById(id).map(entity -> {
            entity.setVerifiedBy(verifiedBy);
            entity.setVerifiedAt(OffsetDateTime.now());
            entity.setVerificationStatus(verificationStatus == null ? "FIELD_VERIFIED" : verificationStatus);
            entity.touch();
            String topic = "REJECTED".equals(entity.getVerificationStatus())
                    ? NdilaEventTopics.LOCATION_REJECTED
                    : NdilaEventTopics.LOCATION_VERIFIED;
            outbox.publish(buildEvent(topic, entity, correlationId));
            return entity;
        });
    }

    public Optional<NdilaLocationEntity> findById(UUID id) { return repository.findById(id); }

    public List<NdilaLocationEntity> findByOwner(UUID tenantId, String ownerService, String ownerEntityId) {
        return repository.findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, ownerService, ownerEntityId);
    }

    public List<NdilaLocationEntity> findByTenant(UUID tenantId) { return repository.findByTenantId(tenantId); }

    private NdilaOutboxPublisher.NdilaEvent buildEvent(String topic, NdilaLocationEntity e, UUID correlationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", topic);
        payload.put("tenantId", e.getTenantId());
        payload.put("correlationId", correlationId);
        payload.put("ownerService", e.getOwnerService());
        payload.put("ownerEntityType", e.getOwnerEntityType());
        payload.put("ownerEntityId", e.getOwnerEntityId());
        payload.put("locationId", e.getId());
        payload.put("latitude", e.getLatitude());
        payload.put("longitude", e.getLongitude());
        payload.put("verificationStatus", e.getVerificationStatus());
        payload.put("sensitivityLevel", e.getSensitivityLevel());
        payload.put("occurredAt", OffsetDateTime.now().toString());
        return new NdilaOutboxPublisher.NdilaEvent(
                topic, "Location", e.getId().toString(),
                e.getTenantId(), correlationId, payload);
    }

    public record CreateLocation(
            UUID tenantId,
            String ownerService,
            String ownerEntityType,
            String ownerEntityId,
            String name,
            String description,
            String locationType,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal altitudeMeters,
            String addressLine1,
            String locality,
            String ward,
            String district,
            String province,
            String country,
            String source,
            String sensitivityLevel,
            BigDecimal coordinateAccuracyMeters
    ) {}
}
