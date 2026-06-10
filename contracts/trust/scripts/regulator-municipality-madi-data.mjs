/**
 * Zimbabwe regulatory institutions, municipalities, NBTS/Madi, and scoped management workspaces.
 * Consumed by generate-seed-catalogues.mjs — v1.4.0 domain expansion.
 */

export const DOMAIN_EXPANSION_VERSION_NOTE =
  "Real Zimbabwe health professions councils, municipalities, NBTS/Madi, and scoped management workspaces.";

/** Generic org type only — not a real institution */
export const REGULATOR_ORGANISATION_TYPES = ["professional_council", "health_professions_authority", "statutory_health_council"];

export const DEPRECATED_FAKE_REGULATOR_CODES = [
  "radiographers_council",
  "rehabilitation_practitioners_council",
];

export const DEPRECATED_GENERIC_REGULATOR_ROLES = ["professional_council_regulator"];

export const DEPRECATED_GENERIC_REGULATOR_ROLE_MAPS = {
  professional_council_regulator: "mdpc_reviewer",
};

/** Nine real Zimbabwe health professions regulatory bodies (HPA + eight councils) */
export const ZIMBABWE_REGULATORY_ORGANISATIONS = [
  {
    code: "health_professions_authority",
    officialName: "Health Professions Authority",
    abbreviation: "HPA",
    aliases: ["HPA Zimbabwe", "Health Professions Authority Zimbabwe"],
    organisationType: "health_professions_authority",
    regulatoryDomain: ["health_professions_oversight", "councils_coordination", "regulatory_appeals", "health_professions_governance"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "professional_truth", "regulatory_appeals"],
    defaultWorkspaces: ["hpa_workspace", "hpa_council_coordination_workspace", "hpa_appeals_disputes_workspace", "regulatory_audit_workspace"],
    defaultRoles: ["hpa_administrator", "hpa_regulatory_reviewer", "hpa_appeals_officer", "hpa_registry_steward", "hpa_compliance_officer", "hpa_auditor"],
    scopeType: "national_regulatory",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "no_system_superuser", "varapi_scoped_oversight"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "critical",
  },
  {
    code: "medical_laboratories_clinical_scientists_council",
    officialName: "Medical Laboratories and Clinical Scientists Council",
    abbreviation: "MLCSC",
    aliases: ["Medical Laboratory and Clinical Scientists Council", "Medical Laboratories & Clinical Scientists Council"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["medical_laboratories", "clinical_scientists", "laboratory_practitioner_registration", "laboratory_professional_practice"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "laboratory_practitioner_licence"],
    defaultWorkspaces: ["mlcsc_workspace", "laboratory_practitioner_registration_review", "provider_licence_review", "provider_cpd_compliance_review", "provider_restriction_management"],
    defaultRoles: ["mlcsc_registrar", "mlcsc_administrator", "mlcsc_reviewer", "mlcsc_approver", "mlcsc_licensing_officer", "mlcsc_registry_steward", "mlcsc_disciplinary_officer", "mlcsc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_laboratory_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "medical_dental_practitioners_council",
    officialName: "Medical and Dental Practitioners Council",
    abbreviation: "MDPC",
    aliases: ["Medical and Dental Practitioners Council of Zimbabwe", "Medical and Dental Council", "MDPCZ"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["medical_practitioners", "dental_practitioners", "specialist_registration", "medical_dental_practice_licensing", "accreditation", "cpd_compliance_where_applicable"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "medical_dental_licence", "specialist_register"],
    defaultWorkspaces: ["mdpc_workspace", "medical_dental_registration_review", "provider_licence_review", "provider_specialist_register_review", "provider_cpd_compliance_review", "provider_suspension_review", "provider_restriction_management"],
    defaultRoles: ["mdpc_registrar", "mdpc_administrator", "mdpc_reviewer", "mdpc_approver", "mdpc_licensing_officer", "mdpc_accreditation_officer", "mdpc_cpd_reviewer", "mdpc_disciplinary_officer", "mdpc_registry_steward", "mdpc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_medical_dental_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "allied_health_practitioners_council",
    officialName: "Allied Health Practitioners Council",
    abbreviation: "AHPC",
    aliases: ["Allied Health Practitioners Council of Zimbabwe", "AHPCZ"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["allied_health_professions", "allied_health_registration", "allied_health_training", "allied_health_professional_conduct", "allied_health_cpd_compliance_where_applicable"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "allied_health_licence"],
    defaultWorkspaces: ["ahpc_workspace", "allied_health_registration_review", "allied_health_practitioner_register", "provider_cpd_compliance_review", "provider_restriction_management", "disciplinary_review_workspace"],
    defaultRoles: ["ahpc_registrar", "ahpc_administrator", "ahpc_reviewer", "ahpc_approver", "ahpc_licensing_officer", "ahpc_training_accreditation_officer", "ahpc_cpd_reviewer", "ahpc_disciplinary_officer", "ahpc_registry_steward", "ahpc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_allied_health_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "pharmacists_council",
    officialName: "Pharmacists Council",
    abbreviation: "PC",
    aliases: ["Pharmacists Council of Zimbabwe", "PCZ"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["pharmacists", "pharmacy_practice_registration", "pharmacy_training", "pharmacy_professional_conduct", "pharmacy_cpd_compliance_where_applicable"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "pharmacy_licence"],
    defaultWorkspaces: ["pc_workspace", "pharmacist_registration_review", "provider_licence_review", "provider_cpd_compliance_review", "provider_restriction_management", "disciplinary_review_workspace"],
    defaultRoles: ["pc_registrar", "pc_administrator", "pc_reviewer", "pc_approver", "pc_licensing_officer", "pc_training_reviewer", "pc_cpd_reviewer", "pc_registry_steward", "pc_disciplinary_officer", "pc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_pharmacy_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "nurses_council",
    officialName: "Nurses Council",
    abbreviation: "NC",
    aliases: ["Nurses Council of Zimbabwe", "NCZ"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["nursing_training", "nursing_practice", "nursing_registration", "nursing_professional_conduct", "nursing_cpd_compliance_where_applicable"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "nursing_licence"],
    defaultWorkspaces: ["nc_workspace", "nursing_registration_review", "nursing_training_review", "provider_licence_review", "provider_cpd_compliance_review", "provider_suspension_review", "provider_restriction_management"],
    defaultRoles: ["nc_registrar", "nc_administrator", "nc_reviewer", "nc_approver", "nc_licensing_officer", "nc_training_accreditation_officer", "nc_cpd_reviewer", "nc_disciplinary_officer", "nc_registry_steward", "nc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_nursing_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "environmental_health_practitioners_council",
    officialName: "Environmental Health Practitioners Council",
    abbreviation: "EHPC",
    aliases: ["Environmental Health Practitioners Council of Zimbabwe", "EHPCZ"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["environmental_health_practitioners", "public_health_inspection_professional_registration", "environmental_health_practice"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "environmental_health_licence"],
    defaultWorkspaces: ["ehpc_workspace", "environmental_health_registration_review", "provider_licence_review", "provider_restriction_management", "public_health_practitioner_register"],
    defaultRoles: ["ehpc_registrar", "ehpc_administrator", "ehpc_reviewer", "ehpc_approver", "ehpc_licensing_officer", "ehpc_registry_steward", "ehpc_disciplinary_officer", "ehpc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "varapi_environmental_health_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "medical_rehabilitation_practitioners_council",
    officialName: "Medical Rehabilitation Practitioners Council",
    abbreviation: "MRPC",
    aliases: ["Medical Rehabilitation Practitioners Council of Zimbabwe", "Rehabilitation Practitioners Council"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["rehabilitation_practitioners", "physiotherapy", "occupational_therapy", "speech_therapy", "rehabilitation_technicians", "rehabilitation_professional_practice"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "rehabilitation_licence"],
    defaultWorkspaces: ["mrpc_workspace", "rehabilitation_practitioner_registration_review", "provider_licence_review", "provider_cpd_compliance_review", "provider_restriction_management"],
    defaultRoles: ["mrpc_registrar", "mrpc_administrator", "mrpc_reviewer", "mrpc_approver", "mrpc_licensing_officer", "mrpc_cpd_reviewer", "mrpc_registry_steward", "mrpc_disciplinary_officer", "mrpc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_facility_staff_management", "no_facility_assignment", "varapi_rehabilitation_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
  {
    code: "natural_therapists_council",
    officialName: "Natural Therapists Council",
    abbreviation: "NTC",
    aliases: ["Natural Therapists Council of Zimbabwe"],
    organisationType: "statutory_health_council",
    regulatoryDomain: ["natural_therapists", "complementary_medicine_practitioners_where_applicable", "natural_therapy_practice"],
    registryOrWorkflowImpacted: ["varapi_provider_registry", "natural_therapy_licence"],
    defaultWorkspaces: ["ntc_workspace", "natural_therapist_registration_review", "provider_licence_review", "provider_restriction_management", "disciplinary_review_workspace"],
    defaultRoles: ["ntc_registrar", "ntc_administrator", "ntc_reviewer", "ntc_approver", "ntc_licensing_officer", "ntc_registry_steward", "ntc_disciplinary_officer", "ntc_auditor"],
    scopeType: "assigned_regulator",
    dataAccessLimits: ["no_clinical_records", "no_mainstream_clinical_permissions", "no_facility_staff_management", "varapi_natural_therapy_cadres_only"],
    policyMapping: "impilo.regulator",
    auditSensitivity: "high",
  },
];

export const MUNICIPAL_ORGANISATION_TYPES = [
  "city_health_department",
  "municipal_health_department",
  "local_authority_health_department",
  "urban_local_authority",
  "rural_district_council_health_unit",
  "municipal_clinic",
  "municipal_polyclinic",
  "municipal_hospital",
  "city_infectious_diseases_hospital",
  "municipal_environmental_health_unit",
  "municipal_ambulance_service",
  "municipal_public_health_office",
  "municipal_health_promotion_unit",
];

export const MUNICIPAL_ROLES = [
  "city_health_director",
  "municipal_health_director",
  "local_authority_health_services_director",
  "city_medical_officer",
  "municipal_medical_officer",
  "city_nursing_officer",
  "municipal_nursing_officer",
  "city_health_information_officer",
  "municipal_health_information_officer",
  "city_environmental_health_officer",
  "municipal_environmental_health_officer",
  "city_epidemiology_disease_control_officer",
  "municipal_disease_surveillance_officer",
  "city_health_promotion_officer",
  "municipal_health_promotion_officer",
  "municipal_clinic_manager",
  "municipal_clinic_sister_in_charge",
  "municipal_pharmacist",
  "municipal_laboratory_focal_person",
  "municipal_records_supervisor",
  "municipal_billing_officer",
  "municipal_health_inspector",
  "municipal_food_safety_inspector",
  "municipal_wash_officer",
  "municipal_vector_control_officer",
  "municipal_outreach_coordinator",
  "municipal_ambulance_coordinator",
  "municipal_digital_health_support_officer",
  "municipal_impilo_support_officer",
];

export const MUNICIPAL_WORKSPACES = [
  { code: "city_health_department_dashboard", displayName: "City Health Department Dashboard", level: "local_authority" },
  { code: "municipal_facility_management", displayName: "Municipal Facility Management", level: "local_authority" },
  { code: "municipal_clinic_operations", displayName: "Municipal Clinic Operations", level: "facility" },
  { code: "municipal_public_health_operations", displayName: "Municipal Public Health Operations", level: "local_authority" },
  { code: "municipal_environmental_health_inspections", displayName: "Municipal Environmental Health Inspections", level: "local_authority" },
  { code: "municipal_food_safety_inspections", displayName: "Municipal Food Safety Inspections", level: "local_authority" },
  { code: "municipal_wash_surveillance", displayName: "Municipal WASH Surveillance", level: "local_authority" },
  { code: "municipal_disease_surveillance", displayName: "Municipal Disease Surveillance", level: "local_authority" },
  { code: "municipal_outreach_management", displayName: "Municipal Outreach Management", level: "catchment" },
  { code: "municipal_health_information_reporting", displayName: "Municipal Health Information Reporting", level: "local_authority" },
  { code: "municipal_staff_assignment", displayName: "Municipal Staff Assignment", level: "local_authority" },
  { code: "municipal_impilo_support", displayName: "Municipal Impilo Support", level: "local_authority" },
];

export const MADI_ORGANISATION_TYPES = [
  "national_blood_transfusion_service",
  "central_blood_bank",
  "regional_blood_bank",
  "hospital_blood_bank",
  "facility_blood_bank",
  "blood_collection_site",
  "blood_donation_drive_site",
  "blood_processing_centre",
  "blood_distribution_hub",
  "haemovigilance_unit",
];

export const MADI_ROLES = [
  "national_blood_service_administrator",
  "blood_service_director",
  "blood_bank_manager",
  "central_blood_bank_manager",
  "regional_blood_bank_manager",
  "hospital_blood_bank_manager",
  "blood_donor_recruitment_officer",
  "blood_donation_drive_coordinator",
  "blood_collection_officer",
  "blood_donor_counsellor",
  "phlebotomist_blood_collection",
  "blood_processing_officer",
  "blood_component_preparation_officer",
  "blood_testing_officer",
  "blood_grouping_crossmatch_officer",
  "blood_inventory_manager",
  "blood_distribution_officer",
  "blood_cold_chain_officer",
  "transfusion_safety_officer",
  "haemovigilance_officer",
  "transfusion_committee_member",
  "facility_transfusion_focal_person",
  "ward_transfusion_nurse",
  "blood_ordering_clinician",
  "blood_issue_authorizer",
  "blood_quality_manager",
  "blood_service_auditor",
];

export const MADI_WORKSPACES = [
  { code: "madi_blood_service_dashboard", displayName: "Madi Blood Service Dashboard", level: "national" },
  { code: "blood_donor_registry", displayName: "Blood Donor Registry", level: "blood_service" },
  { code: "blood_donation_drive_management", displayName: "Blood Donation Drive Management", level: "blood_service" },
  { code: "blood_collection_workspace", displayName: "Blood Collection Workspace", level: "blood_service" },
  { code: "blood_processing_workspace", displayName: "Blood Processing Workspace", level: "blood_service" },
  { code: "blood_testing_workspace", displayName: "Blood Testing Workspace", level: "blood_service" },
  { code: "blood_component_inventory", displayName: "Blood Component Inventory", level: "blood_service" },
  { code: "blood_bank_inventory", displayName: "Blood Bank Inventory", level: "facility" },
  { code: "blood_order_management", displayName: "Blood Order Management", level: "blood_service" },
  { code: "blood_issue_and_distribution", displayName: "Blood Issue and Distribution", level: "blood_service" },
  { code: "transfusion_request_workspace", displayName: "Transfusion Request Workspace", level: "facility" },
  { code: "transfusion_administration_workspace", displayName: "Transfusion Administration Workspace", level: "unit" },
  { code: "haemovigilance_workspace", displayName: "Haemovigilance Workspace", level: "blood_service" },
  { code: "blood_cold_chain_monitoring", displayName: "Blood Cold Chain Monitoring", level: "blood_service" },
  { code: "blood_quality_audit", displayName: "Blood Quality Audit", level: "blood_service" },
  { code: "blood_donor_feedback", displayName: "Blood Donor Feedback", level: "blood_service" },
  { code: "blood_service_reporting", displayName: "Blood Service Reporting", level: "national" },
];

export const MADI_PERMISSION_DOMAINS = [
  "madi.donor",
  "madi.collection",
  "madi.processing",
  "madi.testing",
  "madi.inventory",
  "madi.orders",
  "madi.issue",
  "madi.transfusion",
  "madi.haemovigilance",
  "madi.quality",
  "madi.reporting",
];

export const MADI_PERMISSIONS = [
  "madi.donor.view",
  "madi.donor.register",
  "madi.donor.update",
  "madi.collection.perform",
  "madi.processing.perform",
  "madi.testing.perform",
  "madi.inventory.view",
  "madi.inventory.manage",
  "madi.orders.create",
  "madi.orders.approve",
  "madi.issue.authorize",
  "madi.issue.perform",
  "madi.transfusion.request",
  "madi.transfusion.administer",
  "madi.haemovigilance.review",
  "madi.quality.audit",
  "madi.reporting.view",
];

export const REGULATOR_WORKSPACES = [
  { code: "hpa_workspace", displayName: "HPA Workspace", level: "national", category: "regulator_workspace" },
  { code: "hpa_council_coordination_workspace", displayName: "HPA Council Coordination", level: "national", category: "regulator_workspace" },
  { code: "hpa_appeals_disputes_workspace", displayName: "HPA Appeals and Disputes", level: "national", category: "regulator_workspace" },
  { code: "regulatory_audit_workspace", displayName: "Regulatory Audit Workspace", level: "national", category: "regulator_workspace" },
  { code: "mlcsc_workspace", displayName: "MLCSC Workspace", level: "national", category: "regulator_workspace" },
  { code: "laboratory_practitioner_registration_review", displayName: "Laboratory Practitioner Registration Review", level: "national", category: "regulator_workspace" },
  { code: "mdpc_workspace", displayName: "MDPC Workspace", level: "national", category: "regulator_workspace" },
  { code: "medical_dental_registration_review", displayName: "Medical and Dental Registration Review", level: "national", category: "regulator_workspace" },
  { code: "provider_specialist_register_review", displayName: "Provider Specialist Register Review", level: "national", category: "regulator_workspace" },
  { code: "provider_suspension_review", displayName: "Provider Suspension Review", level: "national", category: "regulator_workspace" },
  { code: "ahpc_workspace", displayName: "AHPC Workspace", level: "national", category: "regulator_workspace" },
  { code: "allied_health_registration_review", displayName: "Allied Health Registration Review", level: "national", category: "regulator_workspace" },
  { code: "allied_health_practitioner_register", displayName: "Allied Health Practitioner Register", level: "national", category: "regulator_workspace" },
  { code: "disciplinary_review_workspace", displayName: "Disciplinary Review Workspace", level: "national", category: "regulator_workspace" },
  { code: "pc_workspace", displayName: "Pharmacists Council Workspace", level: "national", category: "regulator_workspace" },
  { code: "pharmacist_registration_review", displayName: "Pharmacist Registration Review", level: "national", category: "regulator_workspace" },
  { code: "nc_workspace", displayName: "Nurses Council Workspace", level: "national", category: "regulator_workspace" },
  { code: "nursing_registration_review", displayName: "Nursing Registration Review", level: "national", category: "regulator_workspace" },
  { code: "nursing_training_review", displayName: "Nursing Training Review", level: "national", category: "regulator_workspace" },
  { code: "ehpc_workspace", displayName: "EHPC Workspace", level: "national", category: "regulator_workspace" },
  { code: "environmental_health_registration_review", displayName: "Environmental Health Registration Review", level: "national", category: "regulator_workspace" },
  { code: "public_health_practitioner_register", displayName: "Public Health Practitioner Register", level: "national", category: "regulator_workspace" },
  { code: "mrpc_workspace", displayName: "MRPC Workspace", level: "national", category: "regulator_workspace" },
  { code: "rehabilitation_practitioner_registration_review", displayName: "Rehabilitation Practitioner Registration Review", level: "national", category: "regulator_workspace" },
  { code: "ntc_workspace", displayName: "Natural Therapists Council Workspace", level: "national", category: "regulator_workspace" },
  { code: "natural_therapist_registration_review", displayName: "Natural Therapist Registration Review", level: "national", category: "regulator_workspace" },
  { code: "provider_licence_review", displayName: "Provider Licence Review", level: "national", category: "regulator_workspace" },
  { code: "provider_cpd_compliance_review", displayName: "Provider CPD Compliance Review", level: "national", category: "regulator_workspace" },
  { code: "provider_restriction_management", displayName: "Provider Restriction Management", level: "national", category: "regulator_workspace" },
  { code: "regulator_organisation_management", displayName: "Regulator Organisation Management", level: "national", category: "management_workspace" },
  { code: "regulator_user_management", displayName: "Regulator User Management", level: "national", category: "management_workspace" },
  { code: "regulator_scope_assignment", displayName: "Regulator Scope Assignment", level: "national", category: "management_workspace" },
  { code: "regulator_case_assignment", displayName: "Regulator Case Assignment", level: "national", category: "management_workspace" },
  { code: "regulator_review_queue", displayName: "Regulator Review Queue", level: "national", category: "regulator_workspace" },
  { code: "council_user_management", displayName: "Council User Management", level: "national", category: "management_workspace" },
  { code: "council_scope_assignment", displayName: "Council Scope Assignment", level: "national", category: "management_workspace" },
];

/** Scoped management workspaces — replaces generic User Management */
export const MANAGEMENT_WORKSPACES = [
  { code: "citizen_self_registration", displayName: "Citizen Self Registration", scope: "citizen", label: "Citizen Registration" },
  { code: "provider_assisted_client_registration", displayName: "Provider-Assisted Client Registration", scope: "facility", label: "Assisted Client Registration" },
  { code: "client_identity_resolution", displayName: "Client Identity Resolution", scope: "registry", label: "Client Identity Resolution" },
  { code: "client_account_support", displayName: "Client Account Support", scope: "support", label: "Client Account Support" },
  { code: "dependant_guardian_management", displayName: "Dependant and Guardian Management", scope: "citizen", label: "Dependant Management" },
  { code: "consent_support", displayName: "Consent Support", scope: "citizen", label: "Consent Support" },
  { code: "duplicate_resolution", displayName: "Duplicate Resolution", scope: "registry", label: "Duplicate Resolution" },
  { code: "client_record_merge_review", displayName: "Client Record Merge Review", scope: "registry", label: "Client Merge Review" },
  { code: "provider_worker_registry_admin", displayName: "Provider/Worker Registry Admin", scope: "registry", label: "Provider Registry Admin" },
  { code: "provider_worker_verification", displayName: "Provider/Worker Verification", scope: "registry", label: "Provider Verification" },
  { code: "provider_worker_credentials_review", displayName: "Provider/Worker Credentials Review", scope: "registry", label: "Credentials Review" },
  { code: "provider_worker_licence_review", displayName: "Provider/Worker Licence Review", scope: "regulator", label: "Licence Review" },
  { code: "provider_worker_cpd_compliance_review", displayName: "Provider/Worker CPD Compliance Review", scope: "regulator", label: "CPD Compliance Review" },
  { code: "provider_worker_restriction_management", displayName: "Provider/Worker Restriction Management", scope: "regulator", label: "Restriction Management" },
  { code: "provider_worker_status_resolution", displayName: "Provider/Worker Status Resolution", scope: "registry", label: "Status Resolution" },
  { code: "provider_worker_health_id_linkage", displayName: "Provider/Worker Health ID Linkage", scope: "registry", label: "Health ID Linkage" },
  { code: "facility_work_assignment", displayName: "Facility Work Assignment", scope: "facility", label: "Workforce Assignment" },
  { code: "facility_assumption_of_duty", displayName: "Facility Assumption of Duty", scope: "facility", label: "Assumption of Duty" },
  { code: "facility_role_assignment", displayName: "Facility Role Assignment", scope: "facility", label: "Role Assignment" },
  { code: "department_unit_assignment", displayName: "Department/Unit Assignment", scope: "facility", label: "Department Assignment" },
  { code: "local_access_suspension", displayName: "Local Access Suspension", scope: "facility", label: "Local Access Suspension" },
  { code: "staff_transfer_assignment", displayName: "Staff Transfer Assignment", scope: "facility", label: "Staff Transfer" },
  { code: "programme_staff_management", displayName: "Programme Staff Management", scope: "programme", label: "Programme Staff" },
  { code: "programme_role_assignment", displayName: "Programme Role Assignment", scope: "programme", label: "Programme Roles" },
  { code: "programme_scope_assignment", displayName: "Programme Scope Assignment", scope: "programme", label: "Programme Scope" },
  { code: "programme_mentor_assignment", displayName: "Programme Mentor Assignment", scope: "programme", label: "Programme Mentors" },
  { code: "programme_m_and_e_assignment", displayName: "Programme M&E Assignment", scope: "programme", label: "Programme M&E" },
  { code: "above_site_workforce_management", displayName: "Above-Site Workforce Management", scope: "above_site", label: "Above-Site Workforce" },
  { code: "district_support_assignment", displayName: "District Support Assignment", scope: "district", label: "District Support" },
  { code: "provincial_support_assignment", displayName: "Provincial Support Assignment", scope: "province", label: "Provincial Support" },
  { code: "national_support_assignment", displayName: "National Support Assignment", scope: "national", label: "National Support" },
  { code: "helpdesk_assignment", displayName: "Helpdesk Assignment", scope: "support", label: "Helpdesk Assignment" },
  { code: "digital_health_support_assignment", displayName: "Digital Health Support Assignment", scope: "support", label: "Digital Health Support" },
  { code: "registry_owner_management", displayName: "Registry Owner Management", scope: "registry", label: "Registry Owner Management" },
  { code: "registry_steward_assignment", displayName: "Registry Steward Assignment", scope: "registry", label: "Registry Steward Assignment" },
  { code: "registry_approver_assignment", displayName: "Registry Approver Assignment", scope: "registry", label: "Registry Approver Assignment" },
  { code: "registry_data_quality_assignment", displayName: "Registry Data Quality Assignment", scope: "registry", label: "Registry Data Quality" },
  { code: "registry_auditor_assignment", displayName: "Registry Auditor Assignment", scope: "registry", label: "Registry Auditor Assignment" },
  { code: "marketplace_organisation_registration", displayName: "Marketplace Organisation Registration", scope: "marketplace", label: "Marketplace Organisation Registration" },
  { code: "marketplace_organisation_user_management", displayName: "Marketplace Organisation Users", scope: "marketplace", label: "Marketplace Organisation Users" },
  { code: "marketplace_authorised_representative_management", displayName: "Marketplace Authorised Representatives", scope: "marketplace", label: "Marketplace Representatives" },
  { code: "marketplace_role_assignment", displayName: "Marketplace Role Assignment", scope: "marketplace", label: "Marketplace Roles" },
  { code: "marketplace_product_team_assignment", displayName: "Marketplace Product Team Assignment", scope: "marketplace", label: "Product Team Assignment" },
  { code: "marketplace_api_developer_assignment", displayName: "Marketplace API Developer Assignment", scope: "marketplace", label: "API Developer Assignment" },
  { code: "marketplace_contract_user_assignment", displayName: "Marketplace Contract User Assignment", scope: "marketplace", label: "Contract User Assignment" },
  { code: "marketplace_compliance_user_assignment", displayName: "Marketplace Compliance User Assignment", scope: "marketplace", label: "Compliance User Assignment" },
  { code: "blood_service_organisation_management", displayName: "Blood Service Organisation Management", scope: "blood_service", label: "Blood Service Organisations" },
  { code: "blood_service_user_management", displayName: "Blood Service User Management", scope: "blood_service", label: "Blood Service Users" },
  { code: "blood_bank_staff_assignment", displayName: "Blood Bank Staff Assignment", scope: "blood_service", label: "Blood Bank Staff" },
  { code: "blood_donation_team_assignment", displayName: "Blood Donation Team Assignment", scope: "blood_service", label: "Donation Team Assignment" },
  { code: "blood_processing_team_assignment", displayName: "Blood Processing Team Assignment", scope: "blood_service", label: "Processing Team Assignment" },
  { code: "haemovigilance_role_assignment", displayName: "Haemovigilance Role Assignment", scope: "blood_service", label: "Haemovigilance Roles" },
  { code: "facility_transfusion_focal_person_assignment", displayName: "Facility Transfusion Focal Person Assignment", scope: "facility", label: "Transfusion Focal Assignment" },
  { code: "scoped_admin_assignment", displayName: "Scoped Admin Assignment", scope: "system", label: "Scoped Administration" },
  { code: "keycloak_admin_mapping", displayName: "Keycloak Admin Mapping", scope: "system", label: "Keycloak Admin Mapping" },
  { code: "tshepo_admin_mapping", displayName: "Tshepo Admin Mapping", scope: "system", label: "Tshepo Admin Mapping" },
  { code: "opa_policy_admin_mapping", displayName: "OPA Policy Admin Mapping", scope: "system", label: "OPA Policy Admin" },
  { code: "audit_admin_mapping", displayName: "Audit Admin Mapping", scope: "system", label: "Audit Admin Mapping" },
  { code: "integration_admin_mapping", displayName: "Integration Admin Mapping", scope: "system", label: "Integration Admin" },
  { code: "support_agent_management", displayName: "Support Agent Management", scope: "support", label: "Support Agent Management" },
  { code: "break_glass_authorizer_management", displayName: "Break-Glass Authorizer Management", scope: "system", label: "Break-Glass Authorizers" },
];

export const DOMAIN_PERMISSION_BASELINES = {
  regulatorCore: [
    "work.context.enter",
    "regulator.review",
    "regulator.approve",
    "regulator.reject",
    "regulator.suspend",
    "regulator.revoke",
    "audit.logs.view",
  ],
  hpaOversightCore: [
    "work.context.enter",
    "regulator.review",
    "regulator.approve",
    "regulator.inspect",
    "audit.logs.view",
    "policy.bundle.view",
  ],
  municipalCore: [
    "work.context.enter",
    "facility.reports.view",
    "regulator.inspect",
    "facility.staff.view",
    "audit.logs.view",
  ],
  madiBloodServiceCore: [
    "work.context.enter",
    "madi.donor.view",
    "madi.inventory.view",
    "madi.reporting.view",
    "audit.logs.view",
  ],
  madiClinicalTransfusionCore: [
    "work.context.enter",
    "madi.orders.create",
    "madi.transfusion.request",
    "madi.transfusion.administer",
  ],
  madiHaemovigilanceCore: [
    "work.context.enter",
    "madi.haemovigilance.review",
    "madi.quality.audit",
    "audit.logs.view",
  ],
};

function spec(code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel) {
  return [code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel];
}

function councilRoleDisplayName(code) {
  return code
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .replace(/\bHpa\b/g, "HPA")
    .replace(/\bMlcsc\b/g, "MLCSC")
    .replace(/\bMdpc\b/g, "MDPC")
    .replace(/\bAhpc\b/g, "AHPC")
    .replace(/\bEhpc\b/g, "EHPC")
    .replace(/\bMrpc\b/g, "MRPC")
    .replace(/\bNtc\b/g, "NTC")
    .replace(/\bPc\b/g, "PC")
    .replace(/\bNc\b/g, "NC");
}

export function buildCouncilRoleSpecs(existingCodes = new Set()) {
  const out = [];
  for (const org of ZIMBABWE_REGULATORY_ORGANISATIONS) {
    const perms = org.code === "health_professions_authority" ? "hpaOversightCore" : "regulatorCore";
    const scope = org.scopeType === "national_regulatory" ? "national" : "assigned_regulator";
    for (const code of org.defaultRoles) {
      if (existingCodes.has(code)) continue;
      out.push(
        spec(
          code,
          "regulatory_oversight",
          ["regulatory_oversight"],
          org.defaultWorkspaces,
          [],
          perms,
          scope,
        ),
      );
      existingCodes.add(code);
    }
  }
  return out;
}

export function buildMunicipalRoleSpecs(existingCodes = new Set()) {
  const contexts = ["facility_administration", "public_health_inspection", "above_facility_support"];
  const workspaces = MUNICIPAL_WORKSPACES.map((w) => w.code);
  return MUNICIPAL_ROLES.filter((code) => !existingCodes.has(code)).map((code) => {
    existingCodes.add(code);
    const scope = code.includes("city_") || code.includes("municipal_") ? "local_authority" : "catchment";
    return spec(code, "municipal_health", contexts, workspaces, [], "municipalCore", scope);
  });
}

function madiRoleCategory(code) {
  if (code.includes("haemovigilance") || code.includes("quality") || code.includes("auditor")) return "madi_haemovigilance";
  if (code.includes("transfusion") || code.includes("ordering_clinician") || code.includes("ward_transfusion")) return "madi_clinical_transfusion";
  return "madi_blood_service";
}

function madiPermissions(code) {
  if (code.includes("haemovigilance") || code === "blood_quality_manager" || code === "blood_service_auditor") return "madiHaemovigilanceCore";
  if (code.includes("transfusion") || code === "blood_ordering_clinician" || code === "ward_transfusion_nurse") return "madiClinicalTransfusionCore";
  if (code.includes("issue_authorizer")) return ["work.context.enter", "madi.issue.authorize", "madi.inventory.manage", "audit.logs.view"];
  if (code.includes("inventory") || code.includes("distribution")) return ["work.context.enter", "madi.inventory.manage", "madi.issue.perform", "audit.logs.view"];
  if (code.includes("donor") || code.includes("phlebotomist") || code.includes("collection")) return ["work.context.enter", "madi.donor.view", "madi.donor.register", "madi.collection.perform", "audit.logs.view"];
  if (code.includes("processing") || code.includes("testing") || code.includes("grouping")) return ["work.context.enter", "madi.processing.perform", "madi.testing.perform", "audit.logs.view"];
  return "madiBloodServiceCore";
}

export function buildMadiRoleSpecs(existingCodes = new Set()) {
  const workspaces = MADI_WORKSPACES.map((w) => w.code);
  return MADI_ROLES.filter((code) => !existingCodes.has(code)).map((code) => {
    existingCodes.add(code);
    const category = madiRoleCategory(code);
    const contexts =
      category === "madi_clinical_transfusion"
        ? ["facility_clinical", "laboratory_services"]
        : category === "madi_haemovigilance"
          ? ["audit_oversight", "laboratory_services"]
          : ["supply_chain_logistics", "laboratory_services"];
    const scope = code.startsWith("national_") || code === "blood_service_director" ? "national" : code.includes("hospital_") || code.includes("facility_") || code.includes("ward_") ? "facility" : "blood_service";
    return spec(code, category, contexts, workspaces, [], madiPermissions(code), scope);
  });
}

export function buildExtendedRoleProfiles(specs) {
  const profiles = {};
  for (const [code] of specs) {
    if (code.startsWith("hpa_") || code.endsWith("_registrar") || code.endsWith("_reviewer")) {
      profiles[code] = { displayName: councilRoleDisplayName(code), isRealZimbabweInstitutionRole: true };
    }
    if (MUNICIPAL_ROLES.includes(code)) {
      profiles[code] = {
        displayName: councilRoleDisplayName(code),
        primaryZimbabweRole: true,
        policyScopeLevel: "local_authority",
      };
    }
    if (MADI_ROLES.includes(code)) {
      profiles[code] = { displayName: councilRoleDisplayName(code), policyScopeLevel: code.includes("national") ? "national" : "blood_service" };
    }
  }
  return profiles;
}

export function buildExtendedRoleTemplateSpecs(existingCodes) {
  const codes = new Set(existingCodes);
  return [
    ...buildCouncilRoleSpecs(codes),
    ...buildMunicipalRoleSpecs(codes),
    ...buildMadiRoleSpecs(codes),
  ];
}
