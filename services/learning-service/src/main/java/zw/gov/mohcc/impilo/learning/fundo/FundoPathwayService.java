package zw.gov.mohcc.impilo.learning.fundo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayItemRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;

/** Native pathway list/detail with ordered course items (Phase 5B). */
@Service
public class FundoPathwayService {

    private final FundoPathwayRepository pathwayRepository;
    private final FundoPathwayItemRepository pathwayItemRepository;
    private final CourseRepository courseRepository;

    public FundoPathwayService(
            FundoPathwayRepository pathwayRepository,
            FundoPathwayItemRepository pathwayItemRepository,
            CourseRepository courseRepository) {
        this.pathwayRepository = pathwayRepository;
        this.pathwayItemRepository = pathwayItemRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPathways(UUID tenantId, String status, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        String s = status != null ? status : "PUBLISHED";
        return pathwayRepository.findByTenantIdAndStatus(tenantId, s, PageRequest.of(0, cap))
                .stream().map(FundoPathwayService::toPathwayView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getPathway(UUID tenantId, UUID pathwayId) {
        Optional<FundoPathwayEntity> p = pathwayRepository.findByTenantIdAndId(tenantId, pathwayId);
        if (p.isEmpty()) return Optional.empty();
        List<FundoPathwayItemEntity> items = pathwayItemRepository.findByPathwayIdOrderBySequenceNoAsc(pathwayId);
        List<Map<String, Object>> itemViews = new ArrayList<>();
        for (FundoPathwayItemEntity it : items) {
            Map<String, Object> iv = new LinkedHashMap<>();
            iv.put("id", it.getId().toString());
            iv.put("courseId", it.getCourseId().toString());
            iv.put("sequence", it.getSequenceNo());
            iv.put("required", it.isRequired());
            iv.put("prerequisiteCourseId",
                    it.getPrerequisiteCourseId() == null ? null : it.getPrerequisiteCourseId().toString());
            Optional<CourseEntity> course = courseRepository.findById(it.getCourseId())
                    .filter(c -> c.getTenantId().equals(tenantId));
            course.ifPresent(c -> iv.put("course", FundoCatalogService.toView(c)));
            itemViews.add(iv);
        }
        Map<String, Object> v = new LinkedHashMap<>(toPathwayView(p.get()));
        v.put("items", itemViews);
        return Optional.of(v);
    }

    public static Map<String, Object> toPathwayView(FundoPathwayEntity p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId().toString());
        v.put("code", p.getCode());
        v.put("title", p.getTitle());
        v.put("description", p.getDescription());
        v.put("targetCadres", p.getTargetCadres());
        v.put("targetRoles", p.getTargetRoles());
        v.put("targetFacilityLevels", p.getTargetFacilityLevels());
        v.put("status", p.getStatus());
        return v;
    }
}
