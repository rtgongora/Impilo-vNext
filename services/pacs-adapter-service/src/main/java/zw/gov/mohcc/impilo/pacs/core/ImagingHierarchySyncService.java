package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.integration.OrthancClient;
import zw.gov.mohcc.impilo.pacs.integration.OrthancMainDicomHelper;
import zw.gov.mohcc.impilo.pacs.integration.OrthancTagParser;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingArchiveObjectEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAccessAuditEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingInstanceEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingSeriesEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAccessAuditRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingArchiveObjectRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingInstanceRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingSeriesRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronises DICOM study / series / instance rows from Orthanc REST into the PACS adapter database.
 */
@Service
public class ImagingHierarchySyncService {

    private static final Logger log = LoggerFactory.getLogger(ImagingHierarchySyncService.class);

    public static final String AGGREGATE_IMAGING_STUDY = "IMAGING_STUDY";
    public static final String EVENT_HIERARCHY_SYNCED = "imaging.study.hierarchy_synced";

    private final OrthancClient orthancClient;
    private final ImagingStudyRepository studyRepository;
    private final ImagingSeriesRepository seriesRepository;
    private final ImagingInstanceRepository instanceRepository;
    private final ImagingArchiveObjectRepository archiveObjectRepository;
    private final ImagingAccessAuditRepository accessAuditRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImagingHierarchySyncService(
            OrthancClient orthancClient,
            ImagingStudyRepository studyRepository,
            ImagingSeriesRepository seriesRepository,
            ImagingInstanceRepository instanceRepository,
            ImagingArchiveObjectRepository archiveObjectRepository,
            ImagingAccessAuditRepository accessAuditRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.orthancClient = orthancClient;
        this.studyRepository = studyRepository;
        this.seriesRepository = seriesRepository;
        this.instanceRepository = instanceRepository;
        this.archiveObjectRepository = archiveObjectRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImagingStudyEntity syncFromOrthanc(Long studyId, String actorId, String actorType) {
        ImagingStudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> new StudyNotFoundException("Imaging study not found: " + studyId));

        if (study.getOrthancId() == null || study.getOrthancId().isBlank()) {
            throw new IllegalStateException("Cannot sync hierarchy: study has no orthanc_id (study id=" + studyId + ")");
        }

        seriesRepository.deleteByStudy_Id(study.getId());

        JsonNode orthancStudy = orthancClient.getStudy(study.getOrthancId());
        JsonNode seriesIds = orthancStudy.get("Series");
        if (seriesIds == null || !seriesIds.isArray() || seriesIds.isEmpty()) {
            study.setArchiveStatus("EMPTY_REMOTE");
            study.setUpdatedAt(OffsetDateTime.now());
            studyRepository.save(study);
            recordAudit(study.getId(), null, null, actorId, actorType, "SYNC_HIERARCHY", "no_series_in_orthanc");
            return study;
        }

        int seriesTotal = 0;
        int instanceTotal = 0;

        for (JsonNode sid : seriesIds) {
            if (!sid.isTextual()) {
                continue;
            }
            String orthancSeriesId = sid.asText();
            JsonNode ser = orthancClient.getSeries(orthancSeriesId);
            JsonNode main = ser.get("MainDicomTags");

            String seriesUid = OrthancMainDicomHelper.stringField(main, "SeriesInstanceUID");
            if (seriesUid == null || seriesUid.isBlank()) {
                log.warn("Skipping Orthanc series {} — missing SeriesInstanceUID", orthancSeriesId);
                continue;
            }

            ImagingSeriesEntity seriesEntity = new ImagingSeriesEntity();
            seriesEntity.setStudy(study);
            seriesEntity.setSeriesInstanceUid(seriesUid);
            seriesEntity.setModality(OrthancMainDicomHelper.stringField(main, "Modality"));
            seriesEntity.setSeriesNumber(OrthancMainDicomHelper.intField(main, "SeriesNumber"));
            seriesEntity.setSeriesDescription(OrthancMainDicomHelper.stringField(main, "SeriesDescription"));
            seriesEntity.setBodyPartExamined(OrthancMainDicomHelper.stringField(main, "BodyPartExamined"));
            seriesEntity.setOrthancSeriesId(orthancSeriesId);
            seriesEntity = seriesRepository.save(seriesEntity);

            JsonNode instances = ser.get("Instances");
            int count = 0;
            if (instances != null && instances.isArray()) {
                for (JsonNode iid : instances) {
                    if (!iid.isTextual()) {
                        continue;
                    }
                    String orthancInstanceId = iid.asText();
                    JsonNode tags = orthancClient.getInstanceSimplifiedTags(orthancInstanceId);
                    String sop = OrthancTagParser.stringTag(tags, "00080018");
                    if (sop == null || sop.isBlank()) {
                        log.warn("Skipping Orthanc instance {} — missing SOPInstanceUID", orthancInstanceId);
                        continue;
                    }
                    Integer instNum = OrthancTagParser.intTag(tags, "00200013");
                    Integer frames = OrthancTagParser.intTag(tags, "00280008");
                    int frameCount = frames != null && frames > 0 ? frames : 1;
                    String transferSyntax = OrthancTagParser.stringTag(tags, "00020010");

                    String storageRef = "studies/" + study.getStudyUid()
                            + "/series/" + seriesUid
                            + "/instances/" + sop;

                    ImagingInstanceEntity inst = new ImagingInstanceEntity();
                    inst.setSeries(seriesEntity);
                    inst.setSopInstanceUid(sop);
                    inst.setInstanceNumber(instNum);
                    inst.setStorageRef(storageRef);
                    inst.setTransferSyntaxUid(transferSyntax);
                    inst.setFrameCount(frameCount);
                    inst.setOrthancInstanceId(orthancInstanceId);
                    inst.setPreviewRef("/internal/v1/pacs/instances/" + orthancInstanceId + "/preview");
                    inst = instanceRepository.save(inst);

                    ImagingArchiveObjectEntity arc = new ImagingArchiveObjectEntity();
                    arc.setStudyId(study.getId());
                    arc.setSeriesId(seriesEntity.getId());
                    arc.setInstanceId(inst.getId());
                    arc.setStorageType("ORTHANC_DICOMWEB");
                    arc.setStorageRef(storageRef);
                    arc.setStatus("ACTIVE");
                    archiveObjectRepository.save(arc);
                    count++;
                    instanceTotal++;
                }
            }
            seriesEntity.setInstanceCount(count);
            seriesRepository.save(seriesEntity);
            seriesTotal++;
        }

        study.setArchiveStatus("SYNCED");
        study.setUpdatedAt(OffsetDateTime.now());
        studyRepository.save(study);

        appendHierarchySyncedOutbox(study, seriesTotal, instanceTotal);
        recordAudit(study.getId(), null, null, actorId, actorType, "SYNC_HIERARCHY",
                "series=" + seriesTotal + ",instances=" + instanceTotal);

        return studyRepository.findById(studyId).orElse(study);
    }

    private void recordAudit(Long studyId, Long seriesId, Long instanceId,
                             String actorId, String actorType, String accessType, String workflowContext) {
        ImagingAccessAuditEntity row = new ImagingAccessAuditEntity();
        row.setStudyId(studyId);
        row.setSeriesId(seriesId);
        row.setInstanceId(instanceId);
        row.setActorId(actorId);
        row.setActorType(actorType);
        row.setAccessType(accessType);
        row.setWorkflowContext(workflowContext);
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            row.setCorrelationId(ctx.correlationId());
        }
        accessAuditRepository.save(row);
    }

    private void appendHierarchySyncedOutbox(ImagingStudyEntity study, int seriesCount, int instanceCount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("tenant_id", study.getTenantId().toString());
            payload.put("studyInstanceUid", study.getStudyUid());
            payload.put("series_count", seriesCount);
            payload.put("instance_count", instanceCount);
            payload.put("archive_objects_created", instanceCount);

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_HIERARCHY_SYNCED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.study.hierarchy_synced payload", e);
        }
    }

    private void applyContext(EventOutboxEntity row, String tenantFallback) {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            row.setTenantId(ctx.tenantId());
            row.setPodId(ctx.podId());
            row.setCorrelationId(ctx.correlationId());
        } else {
            row.setTenantId(tenantFallback);
            row.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
            row.setCorrelationId(UUID.randomUUID().toString());
        }
    }
}
