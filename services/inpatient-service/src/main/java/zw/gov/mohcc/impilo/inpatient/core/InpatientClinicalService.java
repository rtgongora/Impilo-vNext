package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class InpatientClinicalService {

    /**
     * The tenant/facility for the current request, taken from the trust context populated by
     * the TrustContextFilter (x-tenant-id / x-facility-id). Replaces the previous hardcoded
     * currentTenant()/currentFacility() that put every tenant's inpatient data in one bucket
     * (a multi-tenant data leak). Fails closed if no trust context is present.
     */
    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private UUID currentFacility() {
        return TrustContextHolder.require().facilityId();
    }

    /** Real actor for clinical-audit provenance — never a fabricated name (G038). */
    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    private final CarePlanRepository carePlanRepository;
    private final CarePlanGoalRepository goalRepository;
    private final CarePlanInterventionRepository interventionRepository;
    private final FluidBalanceRepository fluidBalanceRepository;
    private final ClinicalChartEntryRepository chartEntryRepository;
    private final MarEntryRepository marEntryRepository;
    private final EarlyWarningScoreRepository ewsRepository;
    private final EmergencyActivationRepository emergencyRepository;
    private final EmergencyActionRepository emergencyActionRepository;
    private final ResuscitationRecordRepository resuscitationRecordRepository;
    private final ApgarScoreRepository apgarScoreRepository;
    private final ShiftHandoverRepository handoverRepository;
    private final ShiftHandoverItemRepository handoverItemRepository;
    private final WardPatientAlertRepository wardAlertRepository;
    private final DischargeClearanceRepository dischargeClearanceRepository;
    private final ResuscitationPhaseRepository resuscitationPhaseRepository;
    private final AdmissionRepository admissionRepository;
    private final TransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> CLEARANCE_TYPES = List.of(
            "CLINICAL", "NURSING", "PHARMACY", "LABORATORY", "IMAGING",
            "FINANCIAL", "ADMINISTRATIVE", "RECORDS", "CRVS");

    public InpatientClinicalService(
            CarePlanRepository carePlanRepository,
            CarePlanGoalRepository goalRepository,
            CarePlanInterventionRepository interventionRepository,
            FluidBalanceRepository fluidBalanceRepository,
            ClinicalChartEntryRepository chartEntryRepository,
            MarEntryRepository marEntryRepository,
            EarlyWarningScoreRepository ewsRepository,
            EmergencyActivationRepository emergencyRepository,
            EmergencyActionRepository emergencyActionRepository,
            ResuscitationRecordRepository resuscitationRecordRepository,
            ApgarScoreRepository apgarScoreRepository,
            ShiftHandoverRepository handoverRepository,
            ShiftHandoverItemRepository handoverItemRepository,
            WardPatientAlertRepository wardAlertRepository,
            DischargeClearanceRepository dischargeClearanceRepository,
            ResuscitationPhaseRepository resuscitationPhaseRepository,
            AdmissionRepository admissionRepository,
            TransferRepository transferRepository,
            ObjectMapper objectMapper) {
        this.carePlanRepository = carePlanRepository;
        this.goalRepository = goalRepository;
        this.interventionRepository = interventionRepository;
        this.fluidBalanceRepository = fluidBalanceRepository;
        this.chartEntryRepository = chartEntryRepository;
        this.marEntryRepository = marEntryRepository;
        this.ewsRepository = ewsRepository;
        this.emergencyRepository = emergencyRepository;
        this.emergencyActionRepository = emergencyActionRepository;
        this.resuscitationRecordRepository = resuscitationRecordRepository;
        this.apgarScoreRepository = apgarScoreRepository;
        this.handoverRepository = handoverRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.wardAlertRepository = wardAlertRepository;
        this.dischargeClearanceRepository = dischargeClearanceRepository;
        this.resuscitationPhaseRepository = resuscitationPhaseRepository;
        this.admissionRepository = admissionRepository;
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
    }

    private String patientCpid(Map<String, Object> body) {
        String cpid = ClinicalPayloadMapper.str(body, "subjectCpid", "subject_cpid", "patientId", "patient_id");
        if (cpid == null || cpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId/subjectCpid is required");
        }
        return cpid;
    }

    /**
     * Subject-relationship gate for inpatient clinical writes: the referenced admission must exist
     * (in this tenant) and belong to the write's subject. Waived under EMERGENCY/BREAK_GLASS purpose
     * (emergency care is never blocked; the waiver is audited upstream). Throws 403 otherwise.
     */
    private void requireAdmissionRelationship(String subjectCpid, UUID admissionRef) {
        String purpose = TrustContextHolder.require().purposeOfUse();
        String p = purpose == null ? "" : purpose.toUpperCase(java.util.Locale.ROOT);
        if (p.equals("EMERGENCY") || p.equals("BREAK_GLASS")) {
            // Emergency/break-glass override — never block emergency care; the waiver is audited upstream.
            return;
        }
        if (admissionRef == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No admission context: an inpatient clinical write must reference an admission that "
                            + "belongs to the subject (or be performed under emergency purpose).");
        }
        var admission = admissionRepository.findByAdmissionRef(admissionRef)
                .filter(a -> currentTenant().equals(a.getTenantId()));
        if (admission.isEmpty() || !subjectCpid.equals(admission.get().getSubjectCpid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Referenced admission does not belong to the subject of this clinical write.");
        }
    }

    // ── Care plans ──────────────────────────────────────────────────

    public List<Map<String, Object>> listCarePlans(String patientId) {
        return carePlanRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(currentTenant(), patientId)
                .stream().map(this::toCarePlanMap).toList();
    }

    @Transactional
    public Map<String, Object> createCarePlan(Map<String, Object> body) {
        String cpid = patientCpid(body);
        CarePlanEntity plan = new CarePlanEntity();
        plan.setTenantId(currentTenant());
        plan.setSubjectCpid(cpid);
        plan.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        plan.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        // Subject-relationship gate (dimension 6): ext_authz enforces RBAC but cannot bind the body
        // subject to a care context. The care plan must reference an admission that belongs to the subject.
        requireAdmissionRelationship(cpid, plan.getAdmissionRef());
        plan.setTitle(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "title"), "Care plan"));
        plan.setPlanType(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "planType", "plan_type"), "NURSING"));
        plan.setCreatedBy(ClinicalPayloadMapper.str(body, "createdBy", "created_by"));
        plan = carePlanRepository.save(plan);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = body.get("goals") instanceof List<?> rawGoals
                ? (List<Map<String, Object>>) rawGoals : null;
        if (goals != null) {
            for (Map<String, Object> g : goals) {
                CarePlanGoalEntity goal = new CarePlanGoalEntity();
                goal.setPlanId(plan.getPlanId());
                goal.setCategory(Objects.requireNonNullElse(ClinicalPayloadMapper.str(g, "category"), "CLINICAL"));
                goal.setDescription(Objects.requireNonNullElse(ClinicalPayloadMapper.str(g, "description"), "Goal"));
                goal.setStatus(Objects.requireNonNullElse(ClinicalPayloadMapper.str(g, "status"), "IN_PROGRESS"));
                goalRepository.save(goal);
            }
        }
        return toCarePlanMap(carePlanRepository.findById(plan.getPlanId()).orElse(plan));
    }

    @Transactional
    public Map<String, Object> addGoal(UUID planId, Map<String, Object> body) {
        requirePlan(planId);
        CarePlanGoalEntity goal = new CarePlanGoalEntity();
        goal.setPlanId(planId);
        goal.setCategory(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "category"), "CLINICAL"));
        goal.setDescription(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "description"), "Goal"));
        goal.setStatus(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "status"), "IN_PROGRESS"));
        goal = goalRepository.save(goal);
        return Map.of("id", goal.getGoalId().toString());
    }

    @Transactional
    public void updateGoal(UUID planId, UUID goalId, Map<String, Object> body) {
        CarePlanGoalEntity goal = goalRepository.findById(goalId)
                .filter(g -> g.getPlanId().equals(planId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        String status = ClinicalPayloadMapper.str(body, "status");
        if (status != null) {
            goal.setStatus(status);
            if ("ACHIEVED".equals(status)) goal.setAchievedAt(OffsetDateTime.now());
        }
        String current = ClinicalPayloadMapper.str(body, "currentValue", "current_value");
        if (current != null) goal.setCurrentValue(current);
        goalRepository.save(goal);
    }

    @Transactional
    public Map<String, Object> addIntervention(UUID planId, Map<String, Object> body) {
        requirePlan(planId);
        CarePlanInterventionEntity i = new CarePlanInterventionEntity();
        i.setPlanId(planId);
        i.setGoalId(ClinicalPayloadMapper.uuid(body, "goalId", "goal_id"));
        i.setCategory(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "category"), "ASSESSMENT"));
        i.setDescription(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "description"), "Intervention"));
        i.setFrequency(ClinicalPayloadMapper.str(body, "frequency"));
        i.setResponsibleRole(ClinicalPayloadMapper.str(body, "responsibleRole", "responsible_role"));
        i = interventionRepository.save(i);
        return Map.of("id", i.getInterventionId().toString());
    }

    @Transactional
    public void performIntervention(UUID planId, UUID interventionId) {
        CarePlanInterventionEntity i = interventionRepository.findById(interventionId)
                .filter(x -> x.getPlanId().equals(planId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention not found"));
        i.setLastPerformed(OffsetDateTime.now());
        i.setStatus("PERFORMED");
        interventionRepository.save(i);
    }

    private CarePlanEntity requirePlan(UUID planId) {
        return carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found"));
    }

    private Map<String, Object> toCarePlanMap(CarePlanEntity plan) {
        List<Map<String, Object>> goals = goalRepository.findByPlanIdOrderByCreatedAtAsc(plan.getPlanId())
                .stream().map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getGoalId().toString());
                    m.put("category", g.getCategory());
                    m.put("description", g.getDescription());
                    m.put("status", g.getStatus());
                    return m;
                }).toList();
        List<Map<String, Object>> interventions = interventionRepository.findByPlanIdOrderByCreatedAtAsc(plan.getPlanId())
                .stream().map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getInterventionId().toString());
                    m.put("description", i.getDescription());
                    m.put("status", i.getStatus());
                    m.put("last_performed", i.getLastPerformed());
                    return m;
                }).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", plan.getPlanId().toString());
        m.put("title", plan.getTitle());
        m.put("status", plan.getStatus());
        m.put("plan_type", plan.getPlanType());
        m.put("patient_id", plan.getSubjectCpid());
        m.put("goals", goals);
        m.put("interventions", interventions);
        return m;
    }

    // ── Fluid balance ───────────────────────────────────────────────

    public Map<String, Object> getFluidBalance(String patientId, String date) {
        LocalDate d = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();
        List<FluidBalanceEntity> records = fluidBalanceRepository
                .findByTenantIdAndSubjectCpidAndRecordDateOrderByRecordedAtAsc(currentTenant(), patientId, d);
        int intake = 0, output = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FluidBalanceEntity r : records) {
            if ("INTAKE".equals(r.getEntryType())) intake += r.getVolumeMl();
            else output += r.getVolumeMl();
            rows.add(fluidRow(r));
        }
        return Map.of(
                "data", rows,
                "summary", Map.of("totalIntake", intake, "totalOutput", output, "balance", intake - output));
    }

    @Transactional
    public Map<String, Object> recordFluid(Map<String, Object> body) {
        String cpid = patientCpid(body);
        FluidBalanceEntity r = new FluidBalanceEntity();
        r.setTenantId(currentTenant());
        r.setSubjectCpid(cpid);
        r.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        r.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        r.setEntryType(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "entryType", "entry_type"), "INTAKE"));
        r.setCategory(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "category"), "ORAL"));
        Integer vol = ClinicalPayloadMapper.integer(body, "volumeMl", "volume_ml");
        if (vol == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "volumeMl is required");
        r.setVolumeMl(vol);
        r.setDescription(ClinicalPayloadMapper.str(body, "description"));
        r.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        r = fluidBalanceRepository.save(r);
        return Map.of("id", r.getRecordId().toString());
    }

    private Map<String, Object> fluidRow(FluidBalanceEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getRecordId().toString());
        m.put("entry_type", r.getEntryType());
        m.put("category", r.getCategory());
        m.put("volume_ml", r.getVolumeMl());
        m.put("description", r.getDescription());
        m.put("recorded_at", r.getRecordedAt());
        return m;
    }

    // ── Observations / ward charts ──────────────────────────────────

    @Transactional
    public Map<String, Object> recordObservation(Map<String, Object> body) {
        return recordChartEntry(body, ClinicalPayloadMapper.str(body, "chartType", "chart_type", "chartType") != null
                ? ClinicalPayloadMapper.str(body, "chartType", "chart_type")
                : "VITALS");
    }

    @Transactional
    public Map<String, Object> recordChartEntry(Map<String, Object> body, String chartType) {
        String cpid = patientCpid(body);
        ClinicalChartEntryEntity e = new ClinicalChartEntryEntity();
        e.setTenantId(currentTenant());
        e.setSubjectCpid(cpid);
        e.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        e.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        e.setChartType(chartType);
        Object params = body.get("parameters");
        if (params == null) params = body.get("parameters_json");
        if (params == null) params = body;
        try {
            e.setParametersJson(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parameters");
        }
        e.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        e = chartEntryRepository.save(e);
        return Map.of("id", e.getEntryId().toString());
    }

    public List<Map<String, Object>> getObservations(String patientId) {
        return chartEntryRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(currentTenant(), patientId)
                .stream().map(this::chartRow).toList();
    }

    public List<Map<String, Object>> getChartEntries(String patientId, String chartType) {
        return chartEntryRepository.findByTenantIdAndSubjectCpidAndChartTypeOrderByRecordedAtDesc(
                        currentTenant(), patientId, chartType)
                .stream().map(this::chartRow).toList();
    }

    public Map<String, Object> getChartActivity(String patientId) {
        Map<String, Object> activity = new LinkedHashMap<>();
        for (String type : List.of("obs-chart", "fluid-balance", "drug-chart", "fit-chart", "diet-chart", "turn-chart")) {
            List<ClinicalChartEntryEntity> entries = chartEntryRepository
                    .findByTenantIdAndSubjectCpidAndChartTypeOrderByRecordedAtDesc(currentTenant(), patientId, type);
            if (!entries.isEmpty()) {
                activity.put(type, entries.get(0).getRecordedAt().toString());
            }
        }
        return activity;
    }

    private Map<String, Object> chartRow(ClinicalChartEntryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEntryId().toString());
        m.put("chart_type", e.getChartType());
        m.put("patient_id", e.getSubjectCpid());
        try {
            m.put("parameters", objectMapper.readValue(e.getParametersJson(), Map.class));
        } catch (JsonProcessingException ex) {
            m.put("parameters", e.getParametersJson());
        }
        m.put("recorded_at", e.getRecordedAt());
        m.put("recorded_by", e.getRecordedBy());
        return m;
    }

    // ── MAR ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> listMar(String patientId) {
        return marEntryRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(currentTenant(), patientId)
                .stream().map(this::marRow).toList();
    }

    @Transactional
    public int syncMarSchedule(String patientId, List<Map<String, Object>> prescriptions) {
        if (prescriptions == null || prescriptions.isEmpty()) {
            return 0;
        }
        int synced = 0;
        for (Map<String, Object> rx : prescriptions) {
            String prescriptionId = ClinicalPayloadMapper.str(rx, "prescription_id", "prescriptionId", "id");
            if (prescriptionId == null || prescriptionId.isBlank()) {
                continue;
            }
            Optional<MarEntryEntity> existing = marEntryRepository.findByTenantIdAndSubjectCpidAndPrescriptionId(
                    currentTenant(), patientId, prescriptionId);
            if (existing.isPresent()) {
                continue;
            }
            MarEntryEntity mar = new MarEntryEntity();
            mar.setTenantId(currentTenant());
            mar.setSubjectCpid(patientId);
            mar.setPrescriptionId(prescriptionId);
            mar.setDrugName(Objects.requireNonNullElse(
                    ClinicalPayloadMapper.str(rx, "drug_name", "drugName", "medication_name", "medicationName"),
                    "Medication"));
            mar.setDose(ClinicalPayloadMapper.str(rx, "dose", "dosage"));
            mar.setRoute(ClinicalPayloadMapper.str(rx, "route"));
            mar.setTimeDue(ClinicalPayloadMapper.str(rx, "time_due", "timeDue", "frequency"));
            mar.setStatus("SCHEDULED");
            marEntryRepository.save(mar);
            synced++;
        }
        return synced;
    }

    @Transactional
    public Map<String, Object> administerMedication(Map<String, Object> body) {
        String cpid = ClinicalPayloadMapper.str(body, "patientId", "patient_id");
        String prescriptionId = ClinicalPayloadMapper.str(body, "prescriptionId", "prescription_id");
        MarEntryEntity existing = null;
        if (prescriptionId != null && cpid != null) {
            existing = marEntryRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(currentTenant(), cpid)
                    .stream().filter(m -> prescriptionId.equals(m.getPrescriptionId())).findFirst().orElse(null);
        }
        MarEntryEntity mar = existing != null ? existing : new MarEntryEntity();
        if (existing == null) {
            mar.setTenantId(currentTenant());
            mar.setSubjectCpid(cpid != null ? cpid : "UNKNOWN");
            mar.setPrescriptionId(prescriptionId);
            mar.setDrugName(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "drugName", "drug_name"), "Medication"));
            mar.setDose(ClinicalPayloadMapper.str(body, "dose"));
            mar.setRoute(ClinicalPayloadMapper.str(body, "route"));
        }
        mar.setStatus("ADMINISTERED");
        mar.setTimeGiven(OffsetDateTime.now().toLocalTime().toString().substring(0, 5));
        mar.setAdministeredBy(ClinicalPayloadMapper.str(body, "administeredBy", "administered_by"));
        mar.setNotes(ClinicalPayloadMapper.str(body, "notes"));
        mar = marEntryRepository.save(mar);
        return Map.of("id", mar.getMarId().toString(), "status", "ADMINISTERED");
    }

    private Map<String, Object> marRow(MarEntryEntity m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getMarId().toString());
        row.put("prescription_id", m.getPrescriptionId());
        row.put("drug_name", m.getDrugName());
        row.put("dose", m.getDose());
        row.put("route", m.getRoute());
        row.put("time_due", m.getTimeDue());
        row.put("time_given", m.getTimeGiven());
        row.put("status", m.getStatus());
        row.put("administered_by", m.getAdministeredBy());
        return row;
    }

    // ── EWS ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> recordEws(Map<String, Object> body) {
        String cpid = patientCpid(body);
        Integer total = ClinicalPayloadMapper.integer(body, "totalScore", "total_score");
        if (total == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalScore is required");
        EarlyWarningScoreEntity e = new EarlyWarningScoreEntity();
        e.setTenantId(currentTenant());
        e.setSubjectCpid(cpid);
        e.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        e.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        e.setScoreType(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "scoreType", "score_type"), "NEWS2"));
        e.setTotalScore(total);
        String risk = total >= 7 ? "HIGH" : total >= 5 ? "MEDIUM" : total >= 1 ? "LOW" : "NONE";
        e.setRiskLevel(risk);
        e.setEscalationRequired(total >= 5);
        e.setComponentsJson(ClinicalPayloadMapper.str(body, "components", "components_json"));
        e.setRecordedBy(ClinicalPayloadMapper.str(body, "recordedBy", "recorded_by"));
        e = ewsRepository.save(e);
        return Map.of("id", e.getScoreId().toString(), "riskLevel", risk, "escalationRequired", e.isEscalationRequired());
    }

    public List<Map<String, Object>> getEws(String patientId) {
        return ewsRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(currentTenant(), patientId)
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getScoreId().toString());
                    m.put("total_score", e.getTotalScore());
                    m.put("risk_level", e.getRiskLevel());
                    m.put("escalation_required", e.isEscalationRequired());
                    m.put("recorded_at", e.getRecordedAt());
                    return m;
                }).toList();
    }

    // ── Emergency ───────────────────────────────────────────────────

    public List<Map<String, Object>> listEmergencyActivations() {
        return emergencyRepository.findByTenantIdOrderByActivationTimeDesc(currentTenant())
                .stream().map(this::emergencyRow).toList();
    }

    @Transactional
    public Map<String, Object> activateEmergency(Map<String, Object> body) {
        EmergencyActivationEntity a = new EmergencyActivationEntity();
        a.setTenantId(currentTenant());
        a.setSubjectCpid(ClinicalPayloadMapper.str(body, "patientId", "patient_id", "subjectCpid"));
        a.setAdmissionRef(ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref"));
        a.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        a.setProtocolType(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "protocolType", "protocol_type"), "CODE_BLUE"));
        a.setLocation(ClinicalPayloadMapper.str(body, "location"));
        a.setTeamLeader(ClinicalPayloadMapper.str(body, "teamLeader", "team_leader"));
        a = emergencyRepository.save(a);
        return emergencyRow(a);
    }

    @Transactional
    public Map<String, Object> endEmergency(UUID activationId, Map<String, Object> body) {
        EmergencyActivationEntity a = emergencyRepository.findById(activationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activation not found"));
        a.setStatus("ENDED");
        a.setEndedAt(OffsetDateTime.now());
        a.setOutcome(ClinicalPayloadMapper.str(body, "outcome"));
        emergencyRepository.save(a);
        return emergencyRow(a);
    }

    @Transactional
    public Map<String, Object> logEmergencyAction(UUID activationId, Map<String, Object> body) {
        requireActivation(activationId);
        EmergencyActionEntity action = new EmergencyActionEntity();
        action.setActivationId(activationId);
        action.setActionType(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "actionType", "action_type"), "GENERAL"));
        action.setDescription(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "description"), "Emergency action recorded"));
        action.setPerformedBy(ClinicalPayloadMapper.str(body, "performedBy", "performed_by"));
        action = emergencyActionRepository.save(action);
        return Map.of("id", action.getActionId().toString(), "action_type", action.getActionType());
    }

    public List<Map<String, Object>> listEmergencyActions(UUID activationId, String actionType) {
        requireActivation(activationId);
        List<EmergencyActionEntity> actions = actionType != null && !actionType.isBlank()
                ? emergencyActionRepository.findByActivationIdAndActionTypeOrderByPerformedAtDesc(activationId, actionType)
                : emergencyActionRepository.findByActivationIdOrderByPerformedAtDesc(activationId);
        return actions.stream().map(this::emergencyActionRow).toList();
    }

    @Transactional
    public Map<String, Object> recordResuscitation(UUID activationId, Map<String, Object> body) {
        requireActivation(activationId);
        ResuscitationRecordEntity record = resuscitationRecordRepository
                .findFirstByActivationIdOrderByCreatedAtDesc(activationId)
                .orElseGet(ResuscitationRecordEntity::new);
        record.setActivationId(activationId);
        Integer cprCycles = ClinicalPayloadMapper.integer(body, "cprCycles", "cpr_cycles", "cycleNumber", "cycle_number");
        if (cprCycles != null) record.setCprCycles(cprCycles);
        Integer defibrillations = ClinicalPayloadMapper.integer(body, "defibrillations", "defibCount", "defib_count");
        if (defibrillations != null) record.setDefibrillations(defibrillations);
        String initialRhythm = ClinicalPayloadMapper.str(body, "initialRhythm", "initial_rhythm", "rhythm");
        if (initialRhythm != null) record.setInitialRhythm(initialRhythm);
        String finalRhythm = ClinicalPayloadMapper.str(body, "finalRhythm", "final_rhythm");
        if (finalRhythm != null) record.setFinalRhythm(finalRhythm);
        Boolean rosc = body.get("roscAchieved") instanceof Boolean b ? b
                : Boolean.parseBoolean(String.valueOf(body.getOrDefault("rosc_achieved", "false")));
        record.setRoscAchieved(rosc);
        if (Boolean.TRUE.equals(rosc)) {
            record.setRoscTime(OffsetDateTime.now());
        }
        String medsJson = ClinicalPayloadMapper.str(body, "medicationsJson", "medications_json", "medications");
        if (medsJson != null) record.setMedicationsJson(medsJson);
        record = resuscitationRecordRepository.save(record);
        return Map.of("id", record.getResusId().toString(), "activation_id", activationId.toString());
    }

    public Map<String, Object> getResuscitation(UUID activationId) {
        requireActivation(activationId);
        return resuscitationRecordRepository.findFirstByActivationIdOrderByCreatedAtDesc(activationId)
                .map(this::resuscitationRow)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> recordApgar(Map<String, Object> body) {
        String cpid = patientCpid(body);
        Integer minuteMark = ClinicalPayloadMapper.integer(body, "minuteMark", "minute_mark");
        if (minuteMark == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minuteMark is required");
        }
        ApgarScoreEntity score = new ApgarScoreEntity();
        score.setTenantId(currentTenant());
        score.setSubjectCpid(cpid);
        score.setEncounterId(ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id"));
        score.setMinuteMark(minuteMark);
        score.setAppearance(requireApgarComponent(body, "appearance"));
        score.setPulse(requireApgarComponent(body, "pulse"));
        score.setGrimace(requireApgarComponent(body, "grimace"));
        score.setActivity(requireApgarComponent(body, "activity"));
        score.setRespiration(requireApgarComponent(body, "respiration"));
        score = apgarScoreRepository.save(score);
        return Map.of(
                "id", score.getApgarId().toString(),
                "minute_mark", score.getMinuteMark(),
                "total_score", score.totalScore());
    }

    public List<Map<String, Object>> listApgar(String patientId) {
        return apgarScoreRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(currentTenant(), patientId)
                .stream().map(this::apgarRow).toList();
    }

    private void requireActivation(UUID activationId) {
        if (!emergencyRepository.existsById(activationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activation not found");
        }
    }

    private static int requireApgarComponent(Map<String, Object> body, String key) {
        Integer value = ClinicalPayloadMapper.integer(body, key);
        if (value == null || value < 0 || value > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be between 0 and 2");
        }
        return value;
    }

    private Map<String, Object> emergencyActionRow(EmergencyActionEntity action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", action.getActionId().toString());
        m.put("activation_id", action.getActivationId().toString());
        m.put("action_type", action.getActionType());
        m.put("description", action.getDescription());
        m.put("performed_by", action.getPerformedBy());
        m.put("performed_at", action.getPerformedAt());
        return m;
    }

    private Map<String, Object> resuscitationRow(ResuscitationRecordEntity record) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", record.getResusId().toString());
        m.put("activation_id", record.getActivationId().toString());
        m.put("cpr_cycles", record.getCprCycles());
        m.put("defibrillations", record.getDefibrillations());
        m.put("initial_rhythm", record.getInitialRhythm());
        m.put("final_rhythm", record.getFinalRhythm());
        m.put("rosc_achieved", record.getRoscAchieved());
        m.put("rosc_time", record.getRoscTime());
        m.put("medications_json", record.getMedicationsJson());
        return m;
    }

    private Map<String, Object> apgarRow(ApgarScoreEntity score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", score.getApgarId().toString());
        m.put("minute_mark", score.getMinuteMark());
        m.put("appearance", score.getAppearance());
        m.put("pulse", score.getPulse());
        m.put("grimace", score.getGrimace());
        m.put("activity", score.getActivity());
        m.put("respiration", score.getRespiration());
        m.put("total_score", score.totalScore());
        m.put("recorded_at", score.getRecordedAt());
        return m;
    }

    private Map<String, Object> emergencyRow(EmergencyActivationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getActivationId().toString());
        m.put("protocol_type", a.getProtocolType());
        m.put("status", a.getStatus());
        m.put("activation_time", a.getActivationTime());
        return m;
    }

    // ── Handover / takeover ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> submitHandover(Map<String, Object> body) {
        ShiftHandoverEntity h = new ShiftHandoverEntity();
        h.setTenantId(currentTenant());
        h.setFacilityId(ClinicalPayloadMapper.uuid(body, "facilityId", "facility_id") != null
                ? ClinicalPayloadMapper.uuid(body, "facilityId", "facility_id") : currentFacility());
        h.setWardId(ClinicalPayloadMapper.uuid(body, "wardId", "ward_id"));
        h.setShiftId(ClinicalPayloadMapper.str(body, "shiftId", "shift_id"));
        h.setOutgoingStaff(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "outgoingStaff", "outgoing_staff"), currentActor()));
        h.setSituation(ClinicalPayloadMapper.str(body, "situation"));
        h.setBackground(ClinicalPayloadMapper.str(body, "background"));
        h.setAssessment(ClinicalPayloadMapper.str(body, "assessment"));
        h.setRecommendation(ClinicalPayloadMapper.str(body, "recommendation"));
        h.setGeneralNotes(ClinicalPayloadMapper.str(body, "generalNotes", "general_notes", "notes"));
        ShiftHandoverEntity savedHandover = handoverRepository.save(h);
        final UUID savedHandoverId = savedHandover.getHandoverId();

        @SuppressWarnings("unchecked")
        List<String> admissionRefs = (List<String>) body.get("admissionRefs");
        if (admissionRefs != null) {
            for (String ref : admissionRefs) {
                admissionRepository.findByAdmissionRef(UUID.fromString(ref)).ifPresent(admission -> {
                    ShiftHandoverItemEntity item = new ShiftHandoverItemEntity();
                    item.setHandoverId(savedHandoverId);
                    item.setAdmissionRef(admission.getAdmissionRef());
                    item.setSubjectCpid(admission.getSubjectCpid());
                    item.setDiagnosis(admission.getAdmittingDiagnosis());
                    handoverItemRepository.save(item);
                });
            }
        }
        return Map.of("id", savedHandoverId.toString(), "status", savedHandover.getStatus());
    }

    @Transactional
    public Map<String, Object> acceptTakeover(UUID handoverId, Map<String, Object> body) {
        ShiftHandoverEntity h = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover not found"));
        h.setStatus("TAKEN_OVER");
        h.setTakenOverAt(OffsetDateTime.now());
        h.setTakenOverBy(ClinicalPayloadMapper.str(body, "takenOverBy", "taken_over_by", "incomingStaff", "incoming_staff"));
        h.setIncomingStaff(h.getTakenOverBy());
        handoverRepository.save(h);
        return Map.of("id", h.getHandoverId().toString(), "status", h.getStatus());
    }

    public List<Map<String, Object>> listHandovers(UUID facilityId, String status) {
        return handoverRepository.findByTenantIdAndFacilityIdAndStatusOrderBySubmittedAtDesc(
                        currentTenant(), facilityId, status != null ? status : "PENDING")
                .stream().map(h -> Map.<String, Object>of(
                        "id", h.getHandoverId().toString(),
                        "status", h.getStatus(),
                        "outgoing_staff", h.getOutgoingStaff(),
                        "submitted_at", h.getSubmittedAt()))
                .toList();
    }

    // ── Ward alerts ─────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createWardAlert(Map<String, Object> body) {
        String cpid = patientCpid(body);
        WardPatientAlertEntity alert = new WardPatientAlertEntity();
        alert.setTenantId(currentTenant());
        alert.setSubjectCpid(cpid);
        alert.setAlertType(Objects.requireNonNullElse(ClinicalPayloadMapper.str(body, "alertType", "alert_type"), "CALL_NURSE"));
        alert.setMessage(ClinicalPayloadMapper.str(body, "message"));
        UUID admissionRef = ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref");
        Optional<AdmissionEntity> linkedAdmission = admissionRef != null
                ? admissionRepository.findByAdmissionRef(admissionRef)
                : admissionRepository.findBySubjectCpidAndStatus(cpid, "ADMITTED").stream().findFirst();
        if (linkedAdmission.isPresent()) {
            AdmissionEntity a = linkedAdmission.get();
            alert.setAdmissionRef(a.getAdmissionRef());
            alert.setWardId(a.getWardId());
            alert.setBedId(a.getBedId());
        }
        WardPatientAlertEntity savedAlert = wardAlertRepository.save(alert);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", savedAlert.getAlertId().toString());
        m.put("status", savedAlert.getStatus());
        m.put("message", "Ward staff have been alerted");
        m.put("ward_id", savedAlert.getWardId() != null ? savedAlert.getWardId().toString() : null);
        return m;
    }

    public List<Map<String, Object>> listWardAlerts(UUID wardId, String status) {
        return wardAlertRepository.findByTenantIdAndWardIdAndStatusOrderByCreatedAtDesc(
                        currentTenant(), wardId, status != null ? status : "ACTIVE")
                .stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getAlertId().toString());
                    m.put("subject_cpid", a.getSubjectCpid());
                    m.put("alert_type", a.getAlertType());
                    m.put("message", a.getMessage());
                    m.put("status", a.getStatus());
                    m.put("created_at", a.getCreatedAt());
                    m.put("bed_id", a.getBedId() != null ? a.getBedId().toString() : null);
                    return m;
                }).toList();
    }

    @Transactional
    public Map<String, Object> acknowledgeWardAlert(UUID alertId, Map<String, Object> body) {
        WardPatientAlertEntity a = wardAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        a.setStatus("ACKNOWLEDGED");
        a.setAcknowledgedAt(OffsetDateTime.now());
        a.setAcknowledgedBy(ClinicalPayloadMapper.str(body, "acknowledgedBy", "acknowledged_by"));
        wardAlertRepository.save(a);
        return Map.of("id", a.getAlertId().toString(), "status", a.getStatus());
    }

    // ── Transfers workflow ──────────────────────────────────────────

    @Transactional
    public Map<String, Object> requestTransfer(Map<String, Object> body) {
        UUID admissionRef = ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref");
        if (admissionRef == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "admissionRef is required");
        }
        AdmissionEntity admission = admissionRepository.findByAdmissionRef(admissionRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        TransferEntity t = new TransferEntity();
        t.setTenantId(admission.getTenantId());
        t.setAdmissionRef(admissionRef);
        t.setFromWardId(admission.getWardId());
        t.setToWardId(Objects.requireNonNull(ClinicalPayloadMapper.uuid(body, "toWardId", "to_ward_id")));
        t.setToBedId(ClinicalPayloadMapper.uuid(body, "toBedId", "to_bed_id"));
        t.setStatus("REQUESTED");
        t.setReason(ClinicalPayloadMapper.str(body, "reason"));
        t.setClinicalNotes(ClinicalPayloadMapper.str(body, "clinicalNotes", "clinical_notes"));
        t.setRequestedBy(ClinicalPayloadMapper.str(body, "requestedBy", "requested_by"));
        t = transferRepository.save(t);
        return transferRow(t);
    }

    @Transactional
    public Map<String, Object> acceptTransfer(Long transferId, Map<String, Object> body) {
        TransferEntity t = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
        if (!"REQUESTED".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer is not pending acceptance");
        }
        AdmissionEntity admission = admissionRepository.findByAdmissionRef(t.getAdmissionRef())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        admission.setWardId(t.getToWardId());
        admission.setBedId(t.getToBedId());
        admissionRepository.save(admission);
        t.setStatus("ACCEPTED");
        t.setAcceptedBy(ClinicalPayloadMapper.str(body, "acceptedBy", "accepted_by"));
        t.setTransferredAt(OffsetDateTime.now());
        transferRepository.save(t);
        return transferRow(t);
    }

    public List<Map<String, Object>> listTransfers(String patientId) {
        List<TransferEntity> transfers;
        if (patientId != null && !patientId.isBlank()) {
            transfers = admissionRepository.findBySubjectCpid(patientId).stream()
                    .flatMap(a -> transferRepository.findByAdmissionRefOrderByTransferredAtDesc(a.getAdmissionRef()).stream())
                    .toList();
        } else {
            transfers = transferRepository.findAll();
        }
        return transfers.stream().map(this::transferRow).toList();
    }

    private Map<String, Object> transferRow(TransferEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("admission_ref", t.getAdmissionRef().toString());
        m.put("from_ward_id", t.getFromWardId() != null ? t.getFromWardId().toString() : null);
        m.put("to_ward_id", t.getToWardId().toString());
        m.put("to_bed_id", t.getToBedId() != null ? t.getToBedId().toString() : null);
        m.put("status", t.getStatus());
        m.put("requested_at", t.getCreatedAt());
        return m;
    }

    // ── Discharge clearances ────────────────────────────────────────

    @Transactional
    public List<Map<String, Object>> initDischargeClearances(Map<String, Object> body) {
        UUID encounterId = ClinicalPayloadMapper.uuid(body, "encounterId", "encounter_id");
        if (encounterId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "encounterId is required");
        }
        String cpid = ClinicalPayloadMapper.str(body, "patientId", "patient_id", "subjectCpid", "subject_cpid");
        UUID admissionRef = ClinicalPayloadMapper.uuid(body, "admissionRef", "admission_ref");
        List<DischargeClearanceEntity> existing = dischargeClearanceRepository
                .findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(currentTenant(), encounterId);
        if (!existing.isEmpty()) {
            return existing.stream().map(this::clearanceRow).toList();
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (String type : CLEARANCE_TYPES) {
            DischargeClearanceEntity row = new DischargeClearanceEntity();
            row.setTenantId(currentTenant());
            row.setEncounterId(encounterId);
            row.setSubjectCpid(cpid);
            row.setAdmissionRef(admissionRef);
            row.setClearanceType(type);
            row.setStatus("PENDING");
            created.add(clearanceRow(dischargeClearanceRepository.save(row)));
        }
        return created;
    }

    public Map<String, Object> getDischargeClearances(UUID encounterId) {
        List<Map<String, Object>> rows = dischargeClearanceRepository
                .findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(currentTenant(), encounterId)
                .stream().map(this::clearanceRow).toList();
        int total = rows.size();
        long cleared = rows.stream()
                .filter(r -> "CLEARED".equals(r.get("status")) || "WAIVED".equals(r.get("status")))
                .count();
        int percent = total == 0 ? 0 : (int) Math.round((cleared * 100.0) / total);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("total", total);
        progress.put("cleared", cleared);
        progress.put("percent", percent);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", rows);
        result.put("progress", progress);
        return result;
    }

    @Transactional
    public Map<String, Object> clearDischargeClearance(UUID clearanceId, Map<String, Object> body) {
        DischargeClearanceEntity row = dischargeClearanceRepository.findById(clearanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clearance not found"));
        if (!"PENDING".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Clearance is not pending");
        }
        row.setStatus("CLEARED");
        row.setClearedBy(ClinicalPayloadMapper.str(body, "clearedBy", "cleared_by"));
        row.setClearedAt(OffsetDateTime.now());
        dischargeClearanceRepository.save(row);
        return Map.of("id", row.getClearanceId().toString(), "status", row.getStatus());
    }

    @Transactional
    public Map<String, Object> waiveDischargeClearance(UUID clearanceId, Map<String, Object> body) {
        DischargeClearanceEntity row = dischargeClearanceRepository.findById(clearanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clearance not found"));
        if (!"PENDING".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Clearance is not pending");
        }
        String reason = ClinicalPayloadMapper.str(body, "reason", "waivedReason", "waived_reason");
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
        }
        row.setStatus("WAIVED");
        row.setWaived(true);
        row.setWaivedReason(reason);
        row.setClearedBy(ClinicalPayloadMapper.str(body, "clearedBy", "cleared_by"));
        row.setClearedAt(OffsetDateTime.now());
        dischargeClearanceRepository.save(row);
        return Map.of("id", row.getClearanceId().toString(), "status", row.getStatus());
    }

    private Map<String, Object> clearanceRow(DischargeClearanceEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getClearanceId().toString());
        m.put("encounter_id", row.getEncounterId().toString());
        m.put("clearance_type", row.getClearanceType());
        m.put("status", row.getStatus());
        m.put("cleared_by", row.getClearedBy());
        m.put("cleared_at", row.getClearedAt());
        m.put("waived", row.isWaived());
        m.put("waived_reason", row.getWaivedReason());
        return m;
    }

    // ── Resuscitation phases ────────────────────────────────────────

    @Transactional
    public Map<String, Object> startResuscitationPhase(UUID activationId, Map<String, Object> body) {
        requireActivation(activationId);
        ResuscitationPhaseEntity phase = new ResuscitationPhaseEntity();
        phase.setActivationId(activationId);
        phase.setPhaseType(Objects.requireNonNullElse(
                ClinicalPayloadMapper.str(body, "phase", "phaseType", "phase_type"), "RESUSCITATION"));
        phase.setRhythm(ClinicalPayloadMapper.str(body, "rhythm"));
        phase.setStatus("ACTIVE");
        phase = resuscitationPhaseRepository.save(phase);
        return Map.of("id", phase.getPhaseId().toString(), "phase_type", phase.getPhaseType(), "status", phase.getStatus());
    }

    @Transactional
    public Map<String, Object> endResuscitationPhase(UUID activationId, UUID phaseId, Map<String, Object> body) {
        requireActivation(activationId);
        ResuscitationPhaseEntity phase = resuscitationPhaseRepository.findById(phaseId)
                .filter(p -> p.getActivationId().equals(activationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found"));
        if (!"ACTIVE".equals(phase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phase is not active");
        }
        phase.setStatus("ENDED");
        phase.setEndedAt(OffsetDateTime.now());
        phase.setNotes(ClinicalPayloadMapper.str(body, "notes", "outcome"));
        resuscitationPhaseRepository.save(phase);
        return Map.of("id", phase.getPhaseId().toString(), "ended", true);
    }

    public List<Map<String, Object>> listResuscitationPhases(UUID activationId) {
        requireActivation(activationId);
        return resuscitationPhaseRepository.findByActivationIdOrderByStartedAtAsc(activationId)
                .stream().map(this::phaseRow).toList();
    }

    private Map<String, Object> phaseRow(ResuscitationPhaseEntity phase) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", phase.getPhaseId().toString());
        m.put("activation_id", phase.getActivationId().toString());
        m.put("phase_type", phase.getPhaseType());
        m.put("phase", phase.getPhaseType());
        m.put("rhythm", phase.getRhythm());
        m.put("status", phase.getStatus());
        m.put("started_at", phase.getStartedAt());
        m.put("ended_at", phase.getEndedAt());
        m.put("notes", phase.getNotes());
        return m;
    }
}
