package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.LocalityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.LocalityProposalEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.LocalityProposalRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.LocalityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ZwAdminDistrictRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LocalityGazetteerService {

    private final LocalityRepository localityRepository;
    private final LocalityProposalRepository proposalRepository;
    private final ZwAdminDistrictRepository districtRepository;

    public LocalityGazetteerService(LocalityRepository localityRepository,
                                  LocalityProposalRepository proposalRepository,
                                  ZwAdminDistrictRepository districtRepository) {
        this.localityRepository = localityRepository;
        this.proposalRepository = proposalRepository;
        this.districtRepository = districtRepository;
    }

    public List<Map<String, Object>> search(String districtCode, String q, int limit) {
        if (districtCode == null || districtCode.isBlank()) {
            return List.of();
        }
        String needle = q == null ? "" : q.trim();
        if (needle.isEmpty()) {
            return List.of();
        }
        List<LocalityEntity> rows = localityRepository.searchApproved(districtCode, needle);
        List<Map<String, Object>> out = new ArrayList<>();
        int cap = Math.max(1, Math.min(limit, 100));
        for (int i = 0; i < rows.size() && i < cap; i++) {
            out.add(toLocalityMap(rows.get(i)));
        }
        return out;
    }

    public List<Map<String, Object>> listPendingProposals() {
        List<LocalityProposalEntity> rows = proposalRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalityProposalEntity e : rows) {
            out.add(toProposalMap(e));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> propose(String districtCode, String wardCode, String proposedName) {
        requireDistrict(districtCode);
        String name = require(proposedName, "proposedName");
        LocalityProposalEntity p = new LocalityProposalEntity();
        p.setDistrictCode(districtCode.trim());
        if (wardCode != null && !wardCode.isBlank()) {
            p.setWardCode(wardCode.trim());
        }
        p.setProposedName(name.trim());
        p.setStatus("PENDING");
        p = proposalRepository.save(p);
        return toProposalMap(p);
    }

    @Transactional
    public Map<String, Object> approveProposal(long proposalId, String reviewerNote) {
        LocalityProposalEntity p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("proposal not found"));
        if (!"PENDING".equalsIgnoreCase(p.getStatus())) {
            throw new IllegalStateException("proposal not pending");
        }
        LocalityEntity loc = new LocalityEntity();
        loc.setDistrictCode(p.getDistrictCode());
        loc.setWardCode(p.getWardCode());
        loc.setName(p.getProposedName());
        loc.setNameNormalized(normalize(p.getProposedName()));
        loc.setStatus("APPROVED");
        loc = localityRepository.save(loc);
        p.setStatus("APPROVED");
        p.setLocalityId(loc.getId());
        p.setReviewerNote(reviewerNote);
        p.setDecidedAt(Instant.now());
        proposalRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locality", toLocalityMap(loc));
        m.put("proposal", toProposalMap(p));
        return m;
    }

    @Transactional
    public Map<String, Object> rejectProposal(long proposalId, String reviewerNote) {
        LocalityProposalEntity p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("proposal not found"));
        if (!"PENDING".equalsIgnoreCase(p.getStatus())) {
            throw new IllegalStateException("proposal not pending");
        }
        p.setStatus("REJECTED");
        p.setReviewerNote(reviewerNote);
        p.setDecidedAt(Instant.now());
        proposalRepository.save(p);
        return toProposalMap(p);
    }

    private void requireDistrict(String districtCode) {
        if (districtCode == null || districtCode.isBlank()) {
            throw new IllegalArgumentException("districtCode required");
        }
        districtRepository.findByDistrictCode(districtCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("unknown districtCode"));
    }

    private static String require(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return v;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> toLocalityMap(LocalityEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("districtCode", e.getDistrictCode());
        m.put("wardCode", e.getWardCode());
        m.put("name", e.getName());
        m.put("status", e.getStatus());
        return m;
    }

    private static Map<String, Object> toProposalMap(LocalityProposalEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("districtCode", p.getDistrictCode());
        m.put("wardCode", p.getWardCode());
        m.put("proposedName", p.getProposedName());
        m.put("status", p.getStatus());
        m.put("reviewerNote", p.getReviewerNote());
        m.put("localityId", p.getLocalityId());
        m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("decidedAt", p.getDecidedAt() != null ? p.getDecidedAt().toString() : null);
        return m;
    }
}
