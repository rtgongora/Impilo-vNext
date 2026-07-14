package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.InventoryControlledDrugClient;
import zw.gov.mohcc.impilo.inpatient.integration.InventoryImplantClient;
import zw.gov.mohcc.impilo.inpatient.integration.TusoInstrumentSetClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * ── Wave 3 commodities / traceability depth ──
 * Surgical implants (UDI/serial traceability), sterile instrument-set issue/return (TUSO CSSD), and the
 * controlled-drug two-person-witness register — all orchestration-by-reference. Peers stay SoR:
 * inventory (DURA) owns the implant unit + controlled-drug register; TUSO owns the instrument-set registry
 * + CSSD lifecycle. The inpatient.procedure_* rows below hold only peer ids + a projection. Kept as a
 * separate service (like {@link TheatreReadinessBoardService}) so the Wave-1 {@link TheatreService}
 * constructor is untouched.
 */
@Service
public class TheatreCommoditiesService {

    private static final Logger log = LoggerFactory.getLogger(TheatreCommoditiesService.class);

    private static final Set<String> WITNESSED_ACTIONS = Set.of("ADMINISTER", "WASTE", "DISCARD");

    private final ProcedureEpisodeRepository episodeRepository;
    private final ProcedureImplantRepository implantRepository;
    private final ProcedureInstrumentSetUseRepository instrumentSetUseRepository;
    private final ProcedureControlledDrugRepository controlledDrugRepository;
    private final EventOutboxRepository outboxRepository;
    private final InventoryImplantClient implantClient;
    private final TusoInstrumentSetClient instrumentSetClient;
    private final InventoryControlledDrugClient controlledDrugClient;
    private final ObjectMapper objectMapper;

    public TheatreCommoditiesService(ProcedureEpisodeRepository episodeRepository,
                                     ProcedureImplantRepository implantRepository,
                                     ProcedureInstrumentSetUseRepository instrumentSetUseRepository,
                                     ProcedureControlledDrugRepository controlledDrugRepository,
                                     EventOutboxRepository outboxRepository,
                                     InventoryImplantClient implantClient,
                                     TusoInstrumentSetClient instrumentSetClient,
                                     InventoryControlledDrugClient controlledDrugClient,
                                     ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.implantRepository = implantRepository;
        this.instrumentSetUseRepository = instrumentSetUseRepository;
        this.controlledDrugRepository = controlledDrugRepository;
        this.outboxRepository = outboxRepository;
        this.implantClient = implantClient;
        this.instrumentSetClient = instrumentSetClient;
        this.controlledDrugClient = controlledDrugClient;
        this.objectMapper = objectMapper;
    }

    // ── 1. Implant UDI/serial traceability ───────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> recordImplant(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity e = requireEpisode(episodeId);
        String udi = ClinicalPayloadMapper.str(body, "udi", "UDI");
        String serial = ClinicalPayloadMapper.str(body, "serialNumber", "serial_number", "serial");
        if ((udi == null || udi.isBlank()) && (serial == null || serial.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A UDI or serial number is required to record an implant");
        }
        int seq = implantRepository.countByEpisodeId(episodeId) + 1;
        ProcedureImplantEntity link = new ProcedureImplantEntity();
        link.setTenantId(tenant());
        link.setEpisodeId(episodeId);
        link.setSeq(seq);
        link.setUdi(udi);
        link.setSerialNumber(serial);
        link.setLotNumber(ClinicalPayloadMapper.str(body, "lotNumber", "lot_number", "lot"));
        link.setDeviceType(ClinicalPayloadMapper.str(body, "deviceType", "device_type"));
        link.setBodySite(ClinicalPayloadMapper.str(body, "bodySite", "body_site"));
        link.setLaterality(normaliseLaterality(ClinicalPayloadMapper.str(body, "laterality")));
        link.setImplantedBy(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "implantedBy", "implanted_by"), actor()));
        link.setImplantedAt(OffsetDateTime.now());
        link.setCreatedBy(actor());
        String idem = idempotency(episodeId, "implant-" + seq);
        link.setIdempotencyKey(idem);

        InventoryImplantClient.ImplantView view = implantClient.recordImplant(
                e.getSubjectCpid(), episodeId.toString(), link.getUdi(), link.getSerialNumber(),
                link.getLotNumber(), link.getDeviceType(), link.getBodySite(), link.getLaterality(),
                link.getImplantedBy(), idem);
        if (view.available()) {
            link.setInventoryImplantUnitId(view.implantUnitId());
            link.setInventoryPatientImplantId(view.patientImplantId());
            if (view.lotNumber() != null) link.setLotNumber(view.lotNumber());
            link.setStatus("IMPLANTED");
        } else {
            link.setStatus("UNAVAILABLE");
        }
        implantRepository.save(link);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.implant.recorded", Map.of(
                "episode_id", episodeId.toString(),
                "udi", nullSafe(link.getUdi()),
                "inventory_implant_unit_id", nullSafe(link.getInventoryImplantUnitId()),
                "body_site", nullSafe(link.getBodySite()),
                "status", link.getStatus()));
        return implantRow(link);
    }

    public List<Map<String, Object>> listImplants(UUID episodeId) {
        requireEpisode(episodeId);
        return implantRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream().map(this::implantRow).toList();
    }

    /** Case-side recall: given a recalled UDI or lot, find every patient/episode it was implanted in. */
    public List<Map<String, Object>> traceRecall(String udi, String lot) {
        List<ProcedureImplantEntity> hits = new ArrayList<>();
        if (udi != null && !udi.isBlank()) hits.addAll(implantRepository.findByTenantIdAndUdi(tenant(), udi));
        if (lot != null && !lot.isBlank()) hits.addAll(implantRepository.findByTenantIdAndLotNumber(tenant(), lot));
        return hits.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("episode_id", l.getEpisodeId().toString());
            m.put("udi", l.getUdi());
            m.put("serial_number", l.getSerialNumber());
            m.put("lot_number", l.getLotNumber());
            m.put("body_site", l.getBodySite());
            m.put("laterality", l.getLaterality());
            m.put("implanted_by", l.getImplantedBy());
            m.put("implanted_at", l.getImplantedAt());
            m.put("inventory_patient_implant_id", l.getInventoryPatientImplantId());
            return m;
        }).toList();
    }

    // ── 2. Sterile instrument-set issue / return (TUSO CSSD) ─────────────────────────────────────
    @Transactional
    public Map<String, Object> issueInstrumentSet(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String setId = ClinicalPayloadMapper.str(body, "instrumentSetId", "instrument_set_id", "setId");
        if (setId == null || setId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instrumentSetId is required");
        }
        String idem = idempotency(episodeId, "instrument-set-issue-" + setId);
        TusoInstrumentSetClient.SetView view = instrumentSetClient.issue(setId, episodeId.toString(),
                ClinicalPayloadMapper.str(body, "issuedTo", "issued_to"), idem);
        int seq = instrumentSetUseRepository.countByEpisodeId(episodeId) + 1;
        ProcedureInstrumentSetUseEntity use = new ProcedureInstrumentSetUseEntity();
        use.setTenantId(tenant());
        use.setEpisodeId(episodeId);
        use.setSeq(seq);
        use.setTusoInstrumentSetId(setId);
        use.setSetName(view.name());
        use.setSterileUntil(view.sterileUntil());
        use.setIssuedAt(OffsetDateTime.now());
        // Only a real TUSO issue (set moved to IN_USE) is ISSUED; anything else stays UNAVAILABLE.
        use.setIssueStatus(view.available() && "IN_USE".equals(view.sterilisationState()) ? "ISSUED" : "UNAVAILABLE");
        use.setIdempotencyKey(idem);
        use.setCreatedBy(actor());
        instrumentSetUseRepository.save(use);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.instrument_set.issued", Map.of(
                "episode_id", episodeId.toString(), "tuso_instrument_set_id", setId,
                "set_name", nullSafe(use.getSetName()), "issue_status", use.getIssueStatus()));
        return instrumentSetRow(use);
    }

    @Transactional
    public Map<String, Object> returnInstrumentSet(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String setId = ClinicalPayloadMapper.str(body, "instrumentSetId", "instrument_set_id", "setId");
        ProcedureInstrumentSetUseEntity use = (setId != null
                ? instrumentSetUseRepository.findFirstByEpisodeIdAndTusoInstrumentSetIdOrderBySeqDesc(episodeId, setId)
                : instrumentSetUseRepository.findByEpisodeIdAndIssueStatus(episodeId, "ISSUED").stream().findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No issued instrument set to return for this episode"));
        boolean contaminated = flag(body, "contaminated");
        boolean incomplete = flag(body, "incomplete");
        String idem = idempotency(episodeId, "instrument-set-return-" + use.getTusoInstrumentSetId());
        instrumentSetClient.returnSet(use.getTusoInstrumentSetId(), contaminated, incomplete, idem);
        use.setIssueStatus("RETURNED");
        use.setReturnedAt(OffsetDateTime.now());
        use.setContaminated(contaminated);
        use.setIncomplete(incomplete);
        instrumentSetUseRepository.save(use);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.instrument_set.returned", Map.of(
                "episode_id", episodeId.toString(), "tuso_instrument_set_id", use.getTusoInstrumentSetId(),
                "contaminated", contaminated, "incomplete", incomplete));
        return instrumentSetRow(use);
    }

    public List<Map<String, Object>> listInstrumentSets(UUID episodeId) {
        requireEpisode(episodeId);
        return instrumentSetUseRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream()
                .map(this::instrumentSetRow).toList();
    }

    // ── 3. Controlled-drug register (two-person witness) ─────────────────────────────────────────
    @Transactional
    public Map<String, Object> recordControlledDrug(UUID episodeId, Map<String, Object> body) {
        requireEpisode(episodeId);
        String action = Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "action"), "").trim().toUpperCase();
        if (!Set.of("ISSUE", "ADMINISTER", "WASTE", "DISCARD").contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be ISSUE | ADMINISTER | WASTE | DISCARD");
        }
        String witness = ClinicalPayloadMapper.str(body, "witnessProvider", "witness_provider");
        // Two-person integrity: administering / wasting / discarding a controlled drug requires a witness.
        if (WITNESSED_ACTIONS.contains(action) && (witness == null || witness.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "witnessProvider is required to " + action.toLowerCase() + " a controlled drug");
        }
        String itemCode = ClinicalPayloadMapper.str(body, "itemCode", "item_code");
        if (itemCode == null || itemCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemCode is required");
        }
        BigDecimal qty = decimal(body, "quantity", "qty");
        int seq = controlledDrugRepository.countByEpisodeId(episodeId) + 1;
        ProcedureControlledDrugEntity link = new ProcedureControlledDrugEntity();
        link.setTenantId(tenant());
        link.setEpisodeId(episodeId);
        link.setSeq(seq);
        link.setItemCode(itemCode);
        link.setDrugName(ClinicalPayloadMapper.str(body, "drugName", "drug_name"));
        link.setAction(action);
        link.setQuantity(qty);
        link.setUnit(ClinicalPayloadMapper.str(body, "unit"));
        link.setAdministeringProvider(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "administeringProvider", "administering_provider"), actor()));
        link.setWitnessProvider(witness);
        UUID chartEntryId = ClinicalPayloadMapper.uuid(body, "chartEntryId", "chart_entry_id");
        link.setChartEntryId(chartEntryId);
        link.setCreatedBy(actor());
        String idem = idempotency(episodeId, "controlled-drug-" + seq);
        link.setIdempotencyKey(idem);

        InventoryControlledDrugClient.RegisterView view = controlledDrugClient.record(
                itemCode, link.getDrugName(), action, qty, link.getUnit(),
                link.getAdministeringProvider(), witness, episodeId.toString(),
                chartEntryId != null ? chartEntryId.toString() : null, idem);
        if (view.rejected()) {
            // inventory (SoR) rejected — surface the 400 rather than persisting a phantom register entry.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Controlled-drug register rejected the action: " + nullSafe(view.message()));
        }
        if (view.available()) {
            link.setInventoryRegisterEntryId(view.entryId());
            link.setRunningBalance(view.runningBalance());
            link.setStatus("RECORDED");
        } else {
            link.setStatus("UNAVAILABLE");
        }
        controlledDrugRepository.save(link);
        appendOutbox("PROCEDURE", episodeId.toString(), "theatre.controlled_drug.recorded", Map.of(
                "episode_id", episodeId.toString(), "item_code", itemCode, "action", action,
                "witness_provider", nullSafe(witness),
                "inventory_register_entry_id", nullSafe(link.getInventoryRegisterEntryId()),
                "status", link.getStatus()));
        return controlledDrugRow(link);
    }

    public List<Map<String, Object>> listControlledDrugs(UUID episodeId) {
        requireEpisode(episodeId);
        return controlledDrugRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream()
                .map(this::controlledDrugRow).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────
    private Map<String, Object> implantRow(ProcedureImplantEntity l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getImplantId() != null ? l.getImplantId().toString() : null);
        m.put("episode_id", l.getEpisodeId().toString());
        m.put("seq", l.getSeq());
        m.put("udi", l.getUdi());
        m.put("serial_number", l.getSerialNumber());
        m.put("lot_number", l.getLotNumber());
        m.put("device_type", l.getDeviceType());
        m.put("body_site", l.getBodySite());
        m.put("laterality", l.getLaterality());
        m.put("implanted_by", l.getImplantedBy());
        m.put("implanted_at", l.getImplantedAt());
        m.put("status", l.getStatus());
        m.put("inventory_implant_unit_id", l.getInventoryImplantUnitId());
        m.put("inventory_patient_implant_id", l.getInventoryPatientImplantId());
        return m;
    }

    private Map<String, Object> instrumentSetRow(ProcedureInstrumentSetUseEntity u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getUseId() != null ? u.getUseId().toString() : null);
        m.put("episode_id", u.getEpisodeId().toString());
        m.put("tuso_instrument_set_id", u.getTusoInstrumentSetId());
        m.put("set_name", u.getSetName());
        m.put("issue_status", u.getIssueStatus());
        m.put("sterile_until", u.getSterileUntil());
        m.put("issued_at", u.getIssuedAt());
        m.put("returned_at", u.getReturnedAt());
        m.put("contaminated", u.isContaminated());
        m.put("incomplete", u.isIncomplete());
        return m;
    }

    private Map<String, Object> controlledDrugRow(ProcedureControlledDrugEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getRecordId() != null ? c.getRecordId().toString() : null);
        m.put("episode_id", c.getEpisodeId().toString());
        m.put("item_code", c.getItemCode());
        m.put("drug_name", c.getDrugName());
        m.put("action", c.getAction());
        m.put("quantity", c.getQuantity());
        m.put("unit", c.getUnit());
        m.put("running_balance", c.getRunningBalance());
        m.put("administering_provider", c.getAdministeringProvider());
        m.put("witness_provider", c.getWitnessProvider());
        m.put("status", c.getStatus());
        m.put("inventory_register_entry_id", c.getInventoryRegisterEntryId());
        return m;
    }

    private ProcedureEpisodeEntity requireEpisode(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedure episode not found"));
    }

    private UUID tenant() { return TrustContextHolder.require().tenantId(); }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null && !a.isBlank() ? a : "system";
        } catch (IllegalStateException e) { return "system"; }
    }

    private String idempotency(UUID episodeId, String op) {
        return "inpatient-theatre:" + episodeId + ":" + op;
    }

    private static boolean flag(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static BigDecimal decimal(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null) {
                try { return new BigDecimal(v.toString()); } catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    private static String normaliseLaterality(String raw) {
        if (raw == null) return null;
        String l = raw.trim().toUpperCase();
        return Set.of("LEFT", "RIGHT", "BILATERAL", "NA").contains(l) ? l : "NA";
    }

    private static String nullSafe(String s) { return s != null ? s : ""; }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setTenantId(tenant().toString());
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize theatre commodities outbox payload", ex);
        }
        outboxRepository.save(outbox);
    }
}
