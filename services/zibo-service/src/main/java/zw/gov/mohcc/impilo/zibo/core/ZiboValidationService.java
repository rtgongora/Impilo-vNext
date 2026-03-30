package zw.gov.mohcc.impilo.zibo.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ZiboValidationService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final ValidationEngine validationEngine;

    public ZiboValidationService(ArtifactRepository artifactRepository,
                                  ArtifactService artifactService,
                                  ValidationEngine validationEngine) {
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.validationEngine = validationEngine;
    }

    @Transactional(readOnly = true)
    public Page<ArtifactEntity> listCodeSystems(int cursor, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(
                ctx.tenantId(),
                ArtifactType.CODE_SYSTEM,
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("createdAt")));
    }

    @Transactional(readOnly = true)
    public long countCodeSystems() {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.countByTenantIdAndFhirType(ctx.tenantId(), ArtifactType.CODE_SYSTEM);
    }

    @Transactional(readOnly = true)
    public Page<ArtifactEntity> listValueSets(int cursor, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(
                ctx.tenantId(),
                ArtifactType.VALUE_SET,
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("createdAt")));
    }

    @Transactional(readOnly = true)
    public long countValueSets() {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.countByTenantIdAndFhirType(ctx.tenantId(), ArtifactType.VALUE_SET);
    }

    @Transactional(readOnly = true)
    public Page<ArtifactEntity> listConceptMaps(int cursor, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(
                ctx.tenantId(),
                ArtifactType.CONCEPT_MAP,
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("createdAt")));
    }

    @Transactional(readOnly = true)
    public long countConceptMaps() {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.countByTenantIdAndFhirType(ctx.tenantId(), ArtifactType.CONCEPT_MAP);
    }

    @Transactional(readOnly = true)
    public Optional<ArtifactEntity> findCodeSystemById(UUID codeSystemId) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndArtifactId(ctx.tenantId(), codeSystemId)
                .filter(a -> a.getFhirType() == ArtifactType.CODE_SYSTEM);
    }

    @Transactional(readOnly = true)
    public Optional<ArtifactEntity> findValueSetById(UUID valueSetId) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndArtifactId(ctx.tenantId(), valueSetId)
                .filter(a -> a.getFhirType() == ArtifactType.VALUE_SET);
    }

    @Transactional(readOnly = true)
    public Optional<ArtifactEntity> findConceptMapById(UUID conceptMapId) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndArtifactId(ctx.tenantId(), conceptMapId)
                .filter(a -> a.getFhirType() == ArtifactType.CONCEPT_MAP);
    }

    @Transactional
    public ValidationEngine.ValidationResult validateCodeAgainstCodeSystem(UUID codeSystemId, String code, String display) {
        ArtifactEntity codeSystem = findCodeSystemById(codeSystemId)
                .orElseThrow(() -> new IllegalArgumentException("CodeSystem not found: " + codeSystemId));
        if (codeSystem.getStatus() != ArtifactStatus.PUBLISHED) {
            throw new IllegalStateException("CodeSystem is not in PUBLISHED status: " + codeSystemId);
        }
        return validationEngine.validateCoding(codeSystem.getCanonicalUrl(), code, display, PolicyMode.STRICT, null);
    }

    @Transactional
    public ArtifactEntity createCodeSystem(String canonicalUrl, String version, String name,
                                            String title, String description, String contentJson,
                                            String publisher) {
        return artifactService.createDraft(
                ArtifactType.CODE_SYSTEM, canonicalUrl, version, name, title, description, contentJson, publisher);
    }

    @Transactional
    public ArtifactEntity createValueSet(String canonicalUrl, String version, String name,
                                          String title, String description, String contentJson,
                                          String publisher) {
        return artifactService.createDraft(
                ArtifactType.VALUE_SET, canonicalUrl, version, name, title, description, contentJson, publisher);
    }

    @Transactional
    public ArtifactEntity createConceptMap(String canonicalUrl, String version, String name,
                                            String title, String description, String contentJson,
                                            String publisher) {
        return artifactService.createDraft(
                ArtifactType.CONCEPT_MAP, canonicalUrl, version, name, title, description, contentJson, publisher);
    }

    @Transactional(readOnly = true)
    public List<ArtifactEntity> listPublishedCodeSystems() {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(ctx.tenantId(), ArtifactType.CODE_SYSTEM).stream()
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArtifactEntity> listPublishedValueSets() {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(ctx.tenantId(), ArtifactType.VALUE_SET).stream()
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .toList();
    }
}
