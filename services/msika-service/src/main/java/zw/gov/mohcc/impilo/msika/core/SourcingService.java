package zw.gov.mohcc.impilo.msika.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.persistence.entity.RequirementSourcingMapEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.SourcingCategoryEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.RequirementSourcingMapRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.SourcingCategoryRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Requirement → sourcing-category resolution for the marketplace.
 *
 * <p>Regulator-neutral: requirement codes resolve to CATEGORIES only, never
 * suppliers or SKUs. Resolution is explicitly partial — codes without an
 * APPROVED mapping are simply absent from the result. Where a code has
 * multiple approved versions for the same category, the highest version wins
 * (re-seeds must never double the result).</p>
 */
@Service
public class SourcingService {

    private final SourcingCategoryRepository categoryRepository;
    private final RequirementSourcingMapRepository mapRepository;
    private final ListingRepository listingRepository;

    public SourcingService(SourcingCategoryRepository categoryRepository,
                           RequirementSourcingMapRepository mapRepository,
                           ListingRepository listingRepository) {
        this.categoryRepository = categoryRepository;
        this.mapRepository = mapRepository;
        this.listingRepository = listingRepository;
    }

    public record CategoryView(String code, String label, String description, boolean restricted,
                               String requiredCredential, long publishedListingCount) {}

    public record ResolutionView(String requirementCode, String categoryCode, String categoryLabel,
                                 boolean restricted, String requiredCredential, long publishedListingCount) {}

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        Map<String, Long> counts = publishedCounts();
        return categoryRepository.findByStatusOrderByLabelAsc("ACTIVE").stream()
                .map(category -> new CategoryView(category.getCode(), category.getLabel(),
                        category.getDescription(), category.isRestricted(), category.getRequiredCredential(),
                        counts.getOrDefault(category.getCode(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CategoryView> getCategory(String code) {
        return categoryRepository.findById(code)
                .filter(category -> "ACTIVE".equals(category.getStatus()))
                .map(category -> new CategoryView(category.getCode(), category.getLabel(),
                        category.getDescription(), category.isRestricted(), category.getRequiredCredential(),
                        publishedCounts().getOrDefault(category.getCode(), 0L)));
    }

    /**
     * Batch resolve requirement codes. Unmapped codes are absent — the
     * mapping's partiality is a feature, not an error.
     */
    @Transactional(readOnly = true)
    public Map<String, ResolutionView> resolve(Collection<String> requirementCodes) {
        if (requirementCodes == null || requirementCodes.isEmpty()) {
            return Map.of();
        }
        // Highest APPROVED version per (code, category) pair wins.
        Map<String, RequirementSourcingMapEntity> byCode = new HashMap<>();
        for (RequirementSourcingMapEntity row
                : mapRepository.findByRequirementCodeInAndStatus(requirementCodes, "APPROVED")) {
            byCode.merge(row.getRequirementCode(), row,
                    (existing, candidate) -> candidate.getVersion() > existing.getVersion() ? candidate : existing);
        }
        if (byCode.isEmpty()) {
            return Map.of();
        }
        Map<String, SourcingCategoryEntity> categories = new HashMap<>();
        categoryRepository.findAllById(byCode.values().stream()
                        .map(RequirementSourcingMapEntity::getSourcingCategoryCode).distinct().toList())
                .forEach(category -> categories.put(category.getCode(), category));
        Map<String, Long> counts = publishedCounts();

        Map<String, ResolutionView> result = new LinkedHashMap<>();
        for (String code : requirementCodes) {
            RequirementSourcingMapEntity row = byCode.get(code);
            if (row == null) {
                continue;
            }
            SourcingCategoryEntity category = categories.get(row.getSourcingCategoryCode());
            if (category == null || !"ACTIVE".equals(category.getStatus())) {
                continue;
            }
            result.put(code, new ResolutionView(code, category.getCode(), category.getLabel(),
                    category.isRestricted(), category.getRequiredCredential(),
                    counts.getOrDefault(category.getCode(), 0L)));
        }
        return result;
    }

    /** Whether a catalog item's sourcing category requires a verified credentialed seller. */
    @Transactional(readOnly = true)
    public boolean isRestrictedCategory(String sourcingCategoryCode) {
        if (sourcingCategoryCode == null) {
            return false;
        }
        return categoryRepository.findById(sourcingCategoryCode)
                .map(SourcingCategoryEntity::isRestricted)
                .orElse(false);
    }

    private Map<String, Long> publishedCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : listingRepository.countPublishedByCategory()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
