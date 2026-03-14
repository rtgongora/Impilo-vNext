package zw.gov.mohcc.impilo.pacs.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pacs.api.dto.CreateImagingStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ForwardStudyRequest;
import zw.gov.mohcc.impilo.pacs.core.ImagingStudyService;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;

import java.util.List;

/**
 * REST controller for imaging study registration and Orthanc forwarding.
 * All endpoints are under {@code /internal/v1/imaging-studies}.
 */
@RestController
@RequestMapping("/internal/v1/imaging-studies")
public class ImagingStudyController {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyController.class);

    private final ImagingStudyService imagingStudyService;

    public ImagingStudyController(ImagingStudyService imagingStudyService) {
        this.imagingStudyService = imagingStudyService;
    }

    /**
     * List all imaging studies.
     */
    @GetMapping
    public ResponseEntity<List<ImagingStudyEntity>> listStudies() {
        List<ImagingStudyEntity> studies = imagingStudyService.listStudies();
        return ResponseEntity.ok(studies);
    }

    /**
     * Get a single imaging study by its database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ImagingStudyEntity> getStudy(@PathVariable Long id) {
        ImagingStudyEntity study = imagingStudyService.getStudy(id);
        return ResponseEntity.ok(study);
    }

    /**
     * Register a new imaging study.
     */
    @PostMapping
    public ResponseEntity<ImagingStudyEntity> registerStudy(
            @Valid @RequestBody CreateImagingStudyRequest request) {
        ImagingStudyEntity study = imagingStudyService.registerStudy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(study);
    }

    /**
     * Forward an imaging study to Orthanc.
     */
    @PostMapping("/{id}/forward")
    public ResponseEntity<ImagingStudyEntity> forwardStudy(
            @PathVariable Long id,
            @Valid @RequestBody ForwardStudyRequest request) {
        ImagingStudyEntity study = imagingStudyService.forwardStudy(id, request);
        return ResponseEntity.ok(study);
    }
}
