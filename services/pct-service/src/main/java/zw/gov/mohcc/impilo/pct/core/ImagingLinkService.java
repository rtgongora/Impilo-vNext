package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.api.dto.CreateImagingLinkRequest;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImagingLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImagingLinkRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ImagingLinkService {

    private final ImagingLinkRepository imagingLinkRepository;
    private final EncounterRepository encounterRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImagingLinkService(ImagingLinkRepository imagingLinkRepository,
                              EncounterRepository encounterRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.imagingLinkRepository = imagingLinkRepository;
        this.encounterRepository = encounterRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImagingLinkEntity createLink(Long encounterId, CreateImagingLinkRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .filter(e -> ctx.tenantId().equals(e.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));

        ImagingLinkEntity link = new ImagingLinkEntity();
        link.setTenantId(ctx.tenantId());
        link.setJourneyId(encounter.getJourneyId());
        link.setEncounterId(encounter.getId());
        link.setEncounterRef(request.encounterRef() != null ? request.encounterRef() : encounter.getEncounterRef());
        link.setGovernedStudyId(request.governedStudyId().trim());
        link.setOrosOrderId(request.orosOrderId());
        link.setLinkType(request.linkType() != null && !request.linkType().isBlank()
                ? request.linkType().trim()
                : "TRIAGE_IMAGING");
        link.setCreatedBy(ctx.actorId());
        link = imagingLinkRepository.save(link);

        writeOutbox("IMAGING_LINK", link.getId().toString(), "IMAGING_LINK_CREATED", Map.of(
                "encounterId", encounterId,
                "journeyId", encounter.getJourneyId(),
                "governedStudyId", link.getGovernedStudyId(),
                "orosOrderId", link.getOrosOrderId() != null ? link.getOrosOrderId() : "",
                "linkType", link.getLinkType(),
                "createdBy", ctx.actorId() != null ? ctx.actorId() : ""));

        return link;
    }

    @Transactional(readOnly = true)
    public List<ImagingLinkEntity> listByEncounter(Long encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        encounterRepository.findById(encounterId)
                .filter(e -> ctx.tenantId().equals(e.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + encounterId));
        return imagingLinkRepository.findByTenantIdAndEncounterIdOrderByCreatedAtDesc(ctx.tenantId(), encounterId);
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            outbox.setPayload("{}");
        }
        outboxRepository.save(outbox);
    }
}
