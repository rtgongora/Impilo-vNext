package zw.gov.mohcc.impilo.pacs.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pacs.api.dto.CorrelateStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.CreateImagingStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ForwardStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ImagingSearchRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.LaunchViewerRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.OrderLinkRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ReportLinkRequest;
import zw.gov.mohcc.impilo.pacs.core.ImagingStudyService;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingInstanceEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingOrderLinkEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingReportLinkEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingSeriesEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingViewerSessionEntity;

import java.util.List;

/**
 * REST controller for imaging study registration, Orthanc forwarding, DICOM hierarchy, viewer sessions,
 * and report/order linkage. Base path {@code /internal/v1/imaging-studies}.
 */
@RestController
@RequestMapping("/internal/v1/imaging-studies")
public class ImagingStudyController {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyController.class);

    private final ImagingStudyService imagingStudyService;

    public ImagingStudyController(ImagingStudyService imagingStudyService) {
        this.imagingStudyService = imagingStudyService;
    }

    @GetMapping
    public ResponseEntity<List<ImagingStudyEntity>> listStudies(
            @RequestParam(name = "patientCpid", required = false) String patientCpid) {
        return ResponseEntity.ok(imagingStudyService.listStudiesFiltered(patientCpid));
    }

    @PostMapping("/search")
    public ResponseEntity<List<ImagingStudyEntity>> searchStudies(@RequestBody(required = false) ImagingSearchRequest body) {
        ImagingSearchRequest req = body != null ? body : new ImagingSearchRequest();
        return ResponseEntity.ok(imagingStudyService.searchStudies(req, actorId(), actorType()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImagingStudyEntity> getStudy(@PathVariable Long id) {
        ImagingStudyEntity study = imagingStudyService.getStudy(id);
        return ResponseEntity.ok(study);
    }

    @PostMapping
    public ResponseEntity<ImagingStudyEntity> registerStudy(
            @Valid @RequestBody CreateImagingStudyRequest request) {
        ImagingStudyEntity study = imagingStudyService.registerStudy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(study);
    }

    @PostMapping("/{id}/forward")
    public ResponseEntity<ImagingStudyEntity> forwardStudy(
            @PathVariable Long id,
            @Valid @RequestBody ForwardStudyRequest request) {
        ImagingStudyEntity study = imagingStudyService.forwardStudy(id, request);
        return ResponseEntity.ok(study);
    }

    @PatchMapping("/{id}/correlate")
    public ResponseEntity<ImagingStudyEntity> correlateStudy(
            @PathVariable Long id,
            @Valid @RequestBody CorrelateStudyRequest request) {
        ImagingStudyEntity study = imagingStudyService.correlateStudy(id, request);
        return ResponseEntity.ok(study);
    }

    @GetMapping("/{id}/series")
    public ResponseEntity<List<ImagingSeriesEntity>> listSeries(@PathVariable Long id) {
        return ResponseEntity.ok(imagingStudyService.listSeriesForStudy(id));
    }

    @GetMapping("/{studyId}/series/{seriesId}/instances")
    public ResponseEntity<List<ImagingInstanceEntity>> listInstances(
            @PathVariable Long studyId,
            @PathVariable Long seriesId) {
        return ResponseEntity.ok(imagingStudyService.listInstancesForSeries(studyId, seriesId));
    }

    @PostMapping("/{id}/sync-hierarchy")
    public ResponseEntity<ImagingStudyEntity> syncHierarchy(@PathVariable Long id) {
        log.info("Sync imaging hierarchy from Orthanc for studyId={}", id);
        return ResponseEntity.ok(imagingStudyService.syncStudyHierarchy(id, actorId(), actorType()));
    }

    @PostMapping("/{id}/viewer-sessions")
    public ResponseEntity<ImagingViewerSessionEntity> launchViewer(
            @PathVariable Long id,
            @Valid @RequestBody LaunchViewerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imagingStudyService.launchViewerSession(id, request, actorId(), actorType()));
    }

    @PostMapping("/{id}/report-links")
    public ResponseEntity<ImagingReportLinkEntity> linkReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imagingStudyService.linkReport(id, request, actorId(), actorType()));
    }

    @GetMapping("/{id}/report-links")
    public ResponseEntity<List<ImagingReportLinkEntity>> listReportLinks(@PathVariable Long id) {
        return ResponseEntity.ok(imagingStudyService.listReportLinks(id));
    }

    @PostMapping("/{id}/order-links")
    public ResponseEntity<ImagingOrderLinkEntity> linkOrder(
            @PathVariable Long id,
            @Valid @RequestBody OrderLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imagingStudyService.linkOrder(id, request, actorId(), actorType()));
    }

    @GetMapping("/{id}/order-links")
    public ResponseEntity<List<ImagingOrderLinkEntity>> listOrderLinks(@PathVariable Long id) {
        return ResponseEntity.ok(imagingStudyService.listOrderLinks(id));
    }

    private static String actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            return jwt.getSubject() != null ? jwt.getSubject() : auth.getName();
        }
        return auth.getName();
    }

    private static String actorType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "UNKNOWN";
        }
        return auth.getAuthorities().isEmpty() ? "USER" : "JWT";
    }
}
