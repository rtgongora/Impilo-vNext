package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.indawo.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.indawo.domain.PlaceLinkEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.indawo.repository.PlaceLinkRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-registry typed place links (Place Journey Doctrine §6, D-L2).
 *
 * <p>Indawo is the single writer; Tuso and the BFF read both directions.
 * Writes are steward-gated at the controller (defence-in-depth behind
 * ext_authz + the PLACE-LINK-STEWARD policy spec). An INDAWO_SITE ref must
 * exist in this registry; TUSO_FACILITY refs are held as opaque canonical
 * UUIDs — Tuso remains their authority, and a link never merges identity.</p>
 */
@Service
public class PlaceLinkService {

    private static final Logger log = LoggerFactory.getLogger(PlaceLinkService.class);

    private static final String AGGREGATE_TYPE = "PLACE_LINK";
    private static final String PRODUCER = "indawo-service";

    private final PlaceLinkRepository placeLinkRepository;
    private final SiteRepository siteRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PlaceLinkService(PlaceLinkRepository placeLinkRepository,
                            SiteRepository siteRepository,
                            OutboxEventRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.placeLinkRepository = placeLinkRepository;
        this.siteRepository = siteRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public record CreateLink(String leftRefType, UUID leftRefId,
                             String rightRefType, UUID rightRefId,
                             String linkType, OffsetDateTime validFrom, OffsetDateTime validTo,
                             String provenance) {}

    @Transactional
    public PlaceLinkEntity create(UUID tenantId, String podId, String correlationId,
                                  String stewardActor, CreateLink request) {
        validateRefType(request.leftRefType());
        validateRefType(request.rightRefType());
        if (!PlaceLinkEntity.LINK_TYPES.contains(request.linkType())) {
            throw new IllegalArgumentException("Unknown link type: " + request.linkType());
        }
        if (request.leftRefType().equals(request.rightRefType())
                && request.leftRefId().equals(request.rightRefId())) {
            throw new IllegalArgumentException("A place cannot be linked to itself");
        }
        // An INDAWO_SITE ref must exist here (we are its registry); TUSO refs are
        // opaque — Tuso owns their existence.
        requireSiteExists(tenantId, request.leftRefType(), request.leftRefId());
        requireSiteExists(tenantId, request.rightRefType(), request.rightRefId());

        placeLinkRepository
                .findFirstByTenantIdAndLeftRefTypeAndLeftRefIdAndRightRefTypeAndRightRefIdAndLinkTypeAndStatus(
                        tenantId, request.leftRefType(), request.leftRefId(),
                        request.rightRefType(), request.rightRefId(), request.linkType(),
                        PlaceLinkEntity.STATUS_ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException("An ACTIVE link of this type already exists between these places");
                });

        PlaceLinkEntity link = new PlaceLinkEntity();
        link.setTenantId(tenantId);
        link.setLeftRefType(request.leftRefType());
        link.setLeftRefId(request.leftRefId());
        link.setRightRefType(request.rightRefType());
        link.setRightRefId(request.rightRefId());
        link.setLinkType(request.linkType());
        link.setStatus(PlaceLinkEntity.STATUS_ACTIVE);
        if (request.validFrom() != null) {
            link.setValidFrom(request.validFrom());
        }
        link.setValidTo(request.validTo());
        link.setProvenance(request.provenance());
        link.setStewardActor(stewardActor);
        link = placeLinkRepository.save(link);

        publish("indawo.place_link.created", tenantId, podId, correlationId, link);
        log.info("place link created: {} {} {} -> {} {} by {}",
                link.getLinkType(), link.getLeftRefType(), link.getLeftRefId(),
                link.getRightRefType(), link.getRightRefId(), stewardActor);
        return link;
    }

    @Transactional
    public PlaceLinkEntity end(UUID tenantId, String podId, String correlationId,
                               String stewardActor, UUID linkId) {
        PlaceLinkEntity link = placeLinkRepository.findByIdAndTenantId(linkId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown place link"));
        if (!PlaceLinkEntity.STATUS_ACTIVE.equals(link.getStatus())) {
            throw new IllegalStateException("Place link is not ACTIVE");
        }
        link.setStatus(PlaceLinkEntity.STATUS_ENDED);
        link.setValidTo(OffsetDateTime.now());
        link.setStewardActor(stewardActor);
        link = placeLinkRepository.save(link);

        publish("indawo.place_link.ended", tenantId, podId, correlationId, link);
        log.info("place link ended: id={} by {}", link.getId(), stewardActor);
        return link;
    }

    @Transactional(readOnly = true)
    public List<PlaceLinkEntity> findByRef(UUID tenantId, String refType, UUID refId) {
        validateRefType(refType);
        return placeLinkRepository.findByRef(tenantId, refType, refId, PlaceLinkEntity.STATUS_ACTIVE);
    }

    private void requireSiteExists(UUID tenantId, String refType, UUID refId) {
        if (PlaceLinkEntity.REF_INDAWO_SITE.equals(refType)
                && siteRepository.findById(refId).isEmpty()) {
            throw new IllegalArgumentException("Unknown Indawo site: " + refId);
        }
    }

    private static void validateRefType(String refType) {
        if (!PlaceLinkEntity.REF_TUSO_FACILITY.equals(refType)
                && !PlaceLinkEntity.REF_INDAWO_SITE.equals(refType)) {
            throw new IllegalArgumentException("Unknown place ref type: " + refType);
        }
    }

    private void publish(String eventType, UUID tenantId, String podId, String correlationId,
                         PlaceLinkEntity link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("link_id", link.getId().toString());
        payload.put("left_ref_type", link.getLeftRefType());
        payload.put("left_ref_id", link.getLeftRefId().toString());
        payload.put("right_ref_type", link.getRightRefType());
        payload.put("right_ref_id", link.getRightRefId().toString());
        payload.put("link_type", link.getLinkType());
        payload.put("status", link.getStatus());
        payload.put("valid_from", String.valueOf(link.getValidFrom()));
        payload.put("valid_to", link.getValidTo() != null ? String.valueOf(link.getValidTo()) : null);

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(link.getId().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(parseUuid(correlationId));
        outbox.setCausationId(parseUuid(correlationId));
        outbox.setIdempotencyKey(eventType + ":" + link.getId() + ":" + link.getStatus());
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(link.getId().toString());
        outbox.setSubjectType(AGGREGATE_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(link.getLeftRefId().toString());
        outboxRepository.save(outbox);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise place-link payload", e);
        }
    }
}
