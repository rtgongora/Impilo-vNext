package zw.gov.mohcc.impilo.surv.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.FieldTaskEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.OutbreakEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertEventRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.EnvironmentalComplaintRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.FieldTaskRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.IntelligenceBriefRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.InvestigationRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.OutbreakEventRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.SignalRepository;

@Service
public class PublicHealthOperationsHomeService {

    private final SignalRepository signalRepository;
    private final CaseRepository caseRepository;
    private final AlertEventRepository alertEventRepository;
    private final OutbreakEventRepository outbreakEventRepository;
    private final FieldTaskRepository fieldTaskRepository;
    private final InvestigationRepository investigationRepository;
    private final IntelligenceBriefRepository briefRepository;
    private final EnvironmentalComplaintRepository complaintRepository;

    public PublicHealthOperationsHomeService(
            SignalRepository signalRepository,
            CaseRepository caseRepository,
            AlertEventRepository alertEventRepository,
            OutbreakEventRepository outbreakEventRepository,
            FieldTaskRepository fieldTaskRepository,
            InvestigationRepository investigationRepository,
            IntelligenceBriefRepository briefRepository,
            EnvironmentalComplaintRepository complaintRepository) {
        this.signalRepository = signalRepository;
        this.caseRepository = caseRepository;
        this.alertEventRepository = alertEventRepository;
        this.outbreakEventRepository = outbreakEventRepository;
        this.fieldTaskRepository = fieldTaskRepository;
        this.investigationRepository = investigationRepository;
        this.briefRepository = briefRepository;
        this.complaintRepository = complaintRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildHome(UUID tenantId) {
        long activeSignals = signalRepository.findByTenantId(tenantId).stream()
                .filter(s -> s.getStatus() == SignalStatus.ACTIVE)
                .count();
        long openCases = caseRepository.countByTenantIdAndStatusNot(tenantId, CaseStatus.CLOSED);
        long openAlerts = alertEventRepository.countByTenantIdAndAcknowledgedFalse(tenantId);
        long activeOutbreaks = outbreakEventRepository.countByTenantIdAndLifecycleStatusNot(
                tenantId, OutbreakLifecycleStatus.CLOSED);
        long fieldTasksOpen = fieldTaskRepository.countByTenantIdAndStatusIn(
                tenantId, List.of(FieldTaskStatus.PLANNED, FieldTaskStatus.ASSIGNED, FieldTaskStatus.IN_PROGRESS));
        long fieldTasksInProgress = fieldTaskRepository.countByTenantIdAndStatus(tenantId, FieldTaskStatus.IN_PROGRESS);
        long openInvestigations = investigationRepository.countByTenantIdAndStatusNot(
                tenantId, InvestigationStatus.CLOSED);
        long draftBriefs = briefRepository.countByTenantIdAndStatus(tenantId, BriefStatus.DRAFT);
        long openComplaints = complaintRepository.countByTenantIdAndStatusNot(tenantId, ComplaintStatus.CLOSED);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("active_signals", activeSignals);
        kpis.put("open_cases", openCases);
        kpis.put("open_alerts", openAlerts);
        kpis.put("active_outbreaks", activeOutbreaks);
        kpis.put("field_tasks_open", fieldTasksOpen);
        kpis.put("field_tasks_in_progress", fieldTasksInProgress);
        kpis.put("open_investigations", openInvestigations);
        kpis.put("draft_briefs", draftBriefs);
        kpis.put("open_complaints", openComplaints);

        List<Map<String, Object>> priorityWorklist = buildPriorityWorklist(tenantId);
        List<Map<String, Object>> mapMarkers = buildMapMarkers(tenantId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_type", "OPERATIONS_HOME");
        body.put("kpis", kpis);
        body.put("priority_worklist", priorityWorklist);
        body.put("map_marker_count", mapMarkers.size());
        body.put("map_markers", mapMarkers);
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildMapMarkers(UUID tenantId) {
        List<Map<String, Object>> markers = new ArrayList<>();

        for (OutbreakEventEntity o : outbreakEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (o.getLatitude() != null && o.getLongitude() != null) {
                markers.add(marker("outbreak", o.getId(), o.getName(), o.getLatitude(), o.getLongitude(), o.getLifecycleStatus().name()));
            }
        }
        for (FieldTaskEntity t : fieldTaskRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (t.getLatitude() != null && t.getLongitude() != null) {
                markers.add(marker("field_task", t.getId(), t.getTitle(), t.getLatitude(), t.getLongitude(), t.getStatus().name()));
            }
        }
        return markers;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildNompiloSummary(UUID tenantId) {
        Map<String, Object> home = buildHome(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) home.get("kpis");

        String message = String.format(
                "Operations home: %d active signals, %d open cases, %d unacknowledged alerts, %d active outbreaks, %d field tasks open.",
                kpis.getOrDefault("active_signals", 0),
                kpis.getOrDefault("open_cases", 0),
                kpis.getOrDefault("open_alerts", 0),
                kpis.getOrDefault("active_outbreaks", 0),
                kpis.getOrDefault("field_tasks_open", 0));

        List<String> suggestions = new ArrayList<>();
        if (((Number) kpis.getOrDefault("open_alerts", 0)).longValue() > 0) {
            suggestions.add("Review and acknowledge open surveillance alerts in Complaints & Alerts.");
        }
        if (((Number) kpis.getOrDefault("active_outbreaks", 0)).longValue() > 0) {
            suggestions.add("Advance outbreak lifecycle status for events under investigation.");
        }
        if (((Number) kpis.getOrDefault("field_tasks_open", 0)).longValue() > 0) {
            suggestions.add("Assign or complete pending field tasks on the task board.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "DETERMINISTIC_OPERATIONS_HOME");
        result.put("message", message);
        result.put("suggestions", suggestions);
        result.put("kpis", kpis);
        return result;
    }

    private List<Map<String, Object>> buildPriorityWorklist(UUID tenantId) {
        List<Map<String, Object>> items = new ArrayList<>();

        for (AlertEventEntity alert : alertEventRepository.findByTenantIdAndAcknowledgedFalse(tenantId).stream().limit(5).toList()) {
            items.add(workItem("alert", alert.getId(), "Acknowledge alert: " + alert.getSyndromeCode(), "HIGH"));
        }
        for (OutbreakEventEntity o : outbreakEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(ob -> ob.getLifecycleStatus() != OutbreakLifecycleStatus.CLOSED)
                .limit(5)
                .toList()) {
            items.add(workItem("outbreak", o.getId(), "Outbreak: " + o.getName() + " (" + o.getLifecycleStatus() + ")", o.getSeverity().name()));
        }
        for (FieldTaskEntity t : fieldTaskRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(ft -> ft.getStatus() != FieldTaskStatus.COMPLETED && ft.getStatus() != FieldTaskStatus.CANCELLED)
                .limit(5)
                .toList()) {
            items.add(workItem("field_task", t.getId(), "Field task: " + t.getTitle(), t.getStatus().name()));
        }
        for (CaseEntity c : caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(ca -> ca.getStatus() != CaseStatus.CLOSED)
                .limit(3)
                .toList()) {
            items.add(workItem("case", c.getId(), "Case: " + c.getTitle(), c.getStatus().name()));
        }
        return items;
    }

    private Map<String, Object> workItem(String type, Long id, String label, String priority) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("label", label);
        item.put("priority", priority);
        return item;
    }

    private Map<String, Object> marker(String type, Long id, String label, double lat, double lng, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("marker_type", type);
        m.put("id", id);
        m.put("label", label);
        m.put("latitude", lat);
        m.put("longitude", lng);
        m.put("status", status);
        return m;
    }
}
