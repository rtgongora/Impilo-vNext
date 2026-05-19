package zw.gov.mohcc.impilo.surv.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.EnvironmentalComplaintEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EnvironmentalComplaintRepository;

@Service
public class EnvironmentalComplaintService {

    private final EnvironmentalComplaintRepository complaintRepository;

    public EnvironmentalComplaintService(EnvironmentalComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentalComplaintEntity> listComplaints(UUID tenantId) {
        return complaintRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public EnvironmentalComplaintEntity fileComplaint(
            UUID tenantId,
            String actorId,
            String category,
            String title,
            String description,
            String province,
            String district,
            UUID siteId,
            UUID facilityId,
            String reporterContact,
            String jurisdictionPack) {

        EnvironmentalComplaintEntity complaint = new EnvironmentalComplaintEntity();
        complaint.setTenantId(tenantId);
        complaint.setCategory(category != null && !category.isBlank() ? category : "NUISANCE");
        complaint.setTitle(title != null && !title.isBlank() ? title : "Environmental complaint");
        complaint.setDescription(description);
        complaint.setProvince(province);
        complaint.setDistrict(district);
        complaint.setSiteId(siteId);
        complaint.setFacilityId(facilityId);
        complaint.setReporterContact(reporterContact);
        complaint.setJurisdictionPack(jurisdictionPack);
        complaint.setStatus(ComplaintStatus.RECEIVED);
        complaint.setCreatedBy(actorId);
        return complaintRepository.save(complaint);
    }

    @Transactional
    public EnvironmentalComplaintEntity transition(UUID tenantId, Long complaintId, ComplaintStatus nextStatus) {
        EnvironmentalComplaintEntity complaint = complaintRepository.findById(complaintId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        validateTransition(complaint.getStatus(), nextStatus);
        complaint.setStatus(nextStatus);
        return complaintRepository.save(complaint);
    }

    private void validateTransition(ComplaintStatus current, ComplaintStatus next) {
        if (current == next) {
            return;
        }
        Map<ComplaintStatus, List<ComplaintStatus>> allowed = Map.of(
                ComplaintStatus.RECEIVED, List.of(ComplaintStatus.TRIAGED, ComplaintStatus.CLOSED),
                ComplaintStatus.TRIAGED, List.of(ComplaintStatus.INVESTIGATING, ComplaintStatus.CLOSED),
                ComplaintStatus.INVESTIGATING, List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED),
                ComplaintStatus.RESOLVED, List.of(ComplaintStatus.CLOSED),
                ComplaintStatus.CLOSED, List.of());
        if (!allowed.getOrDefault(current, List.of()).contains(next)) {
            throw new IllegalArgumentException("Invalid complaint transition from " + current + " to " + next);
        }
    }
}
