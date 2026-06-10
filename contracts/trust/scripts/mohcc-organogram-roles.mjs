/**
 * MoHCC Organogram (HSC approved 5 March 2025) — role templates, structures, workspaces.
 * Consumed by zimbabwe-catalogue-data.mjs; do not duplicate generic admin collapse.
 */

export const ORGANOGRAM_SOURCE = {
  title: "MoHCC Organogram",
  approvedBy: "Health Service Commission",
  approvedDate: "2025-03-05",
  version: "2025-03-05",
};

/** Workspaces derived from organogram service delivery and directorate structures */
export const ORGANOGRAM_WORKSPACES = [
  { code: "district_dashboard", displayName: "District Dashboard", level: "district" },
  { code: "provincial_dashboard", displayName: "Provincial Dashboard", level: "province" },
  { code: "district_support", displayName: "District Support", level: "district" },
  { code: "provincial_data_quality", displayName: "Provincial Data Quality", level: "province" },
  { code: "district_data_quality", displayName: "District Data Quality", level: "district" },
  { code: "facility_oversight", displayName: "Facility Oversight", level: "province" },
  { code: "clinical_oversight", displayName: "Clinical Oversight", level: "facility" },
  { code: "nursing_administration", displayName: "Nursing Administration", level: "facility" },
  { code: "ward_or_clinic_dashboard", displayName: "Ward or Clinic Dashboard", level: "unit" },
  { code: "curative_services_console", displayName: "Curative Services Console", level: "national" },
  { code: "public_health_console", displayName: "Public Health Console", level: "national" },
  { code: "health_systems_policy_console", displayName: "Health Systems Policy Console", level: "national" },
  { code: "support_services_console", displayName: "Support Services Console", level: "national" },
  { code: "digital_health_console", displayName: "Digital Health Console", level: "national" },
  { code: "internal_audit_console", displayName: "Internal Audit Console", level: "national" },
  { code: "central_hospital_dashboard", displayName: "Central Hospital Dashboard", level: "facility" },
  { code: "provincial_hospital_dashboard", displayName: "Provincial Hospital Dashboard", level: "facility" },
  { code: "general_hospital_dashboard", displayName: "General Hospital Dashboard", level: "facility" },
  { code: "provincial_medical_office", displayName: "Provincial Medical Office", level: "province" },
  { code: "digital_health_support", displayName: "Digital Health Support", level: "province" },
  { code: "training_compliance", displayName: "Training Compliance", level: "facility" },
  { code: "community_follow_up", displayName: "Community Follow Up", level: "community" },
  { code: "staff_roster", displayName: "Staff Roster", level: "unit" },
  { code: "queue", displayName: "Client Queue", level: "unit" },
  { code: "facility_support", displayName: "Facility Support", level: "facility" },
  { code: "reporting", displayName: "Reporting", level: "district" },
  { code: "devices_user_support", displayName: "Devices and User Support", level: "facility" },
  { code: "impilo_platform_console", displayName: "Impilo Platform Console", level: "national" },
  { code: "nompilo_operations_console", displayName: "Nompilo Operations Console", level: "national" },
];

/** Organogram organisational structures (supplements ZW_ORG_STRUCTURES) */
export const ORGANOGRAM_ORG_STRUCTURES = [
  { code: "ministry_of_health_and_child_care", displayName: "Ministry of Health and Child Care", level: "national", parentStructure: null, description: "MoHCC apex organogram structure." },
  { code: "directorate_curative_services", displayName: "Directorate of Curative Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "Curative clinical services, nursing, pharmacy, medical/dental, rehabilitation, EMS." },
  { code: "directorate_health_systems_policy_compliance_coordination", displayName: "Directorate of Health Systems, Policy, Compliance and Coordination", level: "national", parentStructure: "mohcc_national_headquarters", description: "Policy, M&E, programme planning, quality assurance, risk and compliance." },
  { code: "directorate_support_services", displayName: "Directorate of Support Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "HR, training, administration, logistics, finance." },
  { code: "directorate_ict_digital_health_information", displayName: "ICT, Digital Health and Information", level: "national", parentStructure: "mohcc_national_headquarters", description: "Digital health, HIS, surveillance analytics, Impilo platform." },
  { code: "directorate_internal_audit", displayName: "Internal Audit", level: "national", parentStructure: "mohcc_national_headquarters", description: "Internal audit, risk and compliance assurance." },
  { code: "departments_reporting_to_permanent_secretary", displayName: "Departments Reporting to Permanent Secretary", level: "national", parentStructure: "office_of_the_permanent_secretary", description: "Direct PS reporting units." },
  { code: "provincial_medical_directorate", displayName: "Provincial Medical Directorate", level: "province", parentStructure: "provincial_health_executive", description: "Provincial Medical Office organogram structure." },
  { code: "central_hospital_executive", displayName: "Central Hospital Executive", level: "facility", parentStructure: "district_health_executive", description: "Central hospital leadership and service lines." },
  { code: "provincial_hospital_management", displayName: "Provincial Hospital Management", level: "facility", parentStructure: "provincial_medical_office", description: "Provincial hospital operations." },
  { code: "general_hospital_management", displayName: "General Hospital Management", level: "facility", parentStructure: "district_medical_office", description: "General/district hospital operations." },
  { code: "rural_health_centre_clinic", displayName: "Rural Health Centre / Clinic", level: "facility", parentStructure: "district_medical_office", description: "Primary care clinic and RHC organogram." },
  { code: "health_centre_committee", displayName: "Health Centre Committee", level: "community", parentStructure: "rural_health_centre_clinic", description: "Community facility governance." },
];

/** Zimbabwe-native display names and aliases (generic terms are aliases only) */
export const ORGANOGRAM_ROLE_PROFILES = {
  district_medical_officer: { displayName: "District Medical Officer", abbreviation: "DMO", aliases: ["District Health Officer", "District Health Manager"], primaryZimbabweRole: true, directorate: "district_medical_office" },
  provincial_medical_director: { displayName: "Provincial Medical Director", abbreviation: "PMD", aliases: ["Provincial Health Officer", "Provincial Health Director", "Provincial Medical Officer"], primaryZimbabweRole: true, directorate: "provincial_medical_directorate" },
  provincial_epidemiology_disease_control_officer: { displayName: "Provincial Epidemiology and Disease Control Officer", abbreviation: "PEDCO", aliases: ["Provincial EDC Officer", "Provincial Disease Control Officer"], primaryZimbabweRole: true },
  pedco_pmcho: { displayName: "Provincial Epidemiology and Disease Control Officer (PMCHO)", abbreviation: "PEDCO", aliases: ["PMCHO PEDCO"], primaryZimbabweRole: true },
  sister_in_charge_community: { displayName: "Sister-in-Charge Community", abbreviation: "SICC", aliases: ["Community Sister-in-Charge"], primaryZimbabweRole: true },
  health_information_officer: { displayName: "Health Information Officer", abbreviation: "HIO", aliases: ["HIS Officer", "Information Officer"], primaryZimbabweRole: true },
  provincial_ict_digital_health_officer: { displayName: "ICT & Digital Health Officer", abbreviation: "ICT-DH", aliases: ["Digital Health Officer", "ICT Officer"], primaryZimbabweRole: true },
  district_ict_digital_health_support_officer: { displayName: "District ICT & Digital Health Support Officer", abbreviation: "ICT-DH", aliases: ["Digital Health Support Officer"], primaryZimbabweRole: true },
  chief_director_curative_services: { displayName: "Chief Director — Curative Services", directorate: "directorate_curative_services" },
  chief_director_public_health: { displayName: "Chief Director — Public Health", directorate: "directorate_public_health" },
  chief_director_health_systems_policy_compliance_coordination: { displayName: "Chief Director — Health Systems, Policy, Compliance and Coordination", directorate: "directorate_health_systems_policy_compliance_coordination" },
  chief_director_support_services: { displayName: "Chief Director — Support Services", directorate: "directorate_support_services" },
  director_ict_digital_health_information: { displayName: "Director — ICT, Digital Health and Information", directorate: "directorate_ict_digital_health_information" },
  permanent_secretary: { displayName: "Permanent Secretary", directorate: "office_of_the_permanent_secretary" },
  minister_of_health: { displayName: "Minister of Health and Child Care", directorate: "ministry_of_health_and_child_care" },
  deputy_minister_of_health: { displayName: "Deputy Minister of Health and Child Care", directorate: "ministry_of_health_and_child_care" },
  chief_executive_officer: { displayName: "Chief Executive Officer (Central Hospital)", scopeNote: "Central hospital only" },
  government_medical_officer: { displayName: "Government Medical Officer", aliases: ["GMO", "Medical Officer"] },
  specialist_consultant: { displayName: "Specialist Consultant", aliases: ["Consultant Specialist"] },
  matron_i: { displayName: "Matron I", aliases: ["Matron Grade I"] },
  matron_ii: { displayName: "Matron II", aliases: ["Matron Grade II"] },
  pno: { displayName: "Provincial Nursing Officer", abbreviation: "PNO", primaryZimbabweRole: true },
};

/** Default context/workspace mappings for key Zimbabwe roles (section 13) */
export const ORGANOGRAM_CONTEXT_MAPPINGS = {
  district_medical_officer: { contextTypes: ["above_facility_management", "programme_support", "emergency_response", "audit_oversight"], scopeLevel: "district", defaultWorkspaces: ["district_dashboard", "programme_dashboard", "emergency_response", "district_support"] },
  provincial_medical_director: { contextTypes: ["above_facility_management", "programme_support", "audit_oversight"], scopeLevel: "province", defaultWorkspaces: ["provincial_dashboard", "programme_dashboard", "facility_oversight"] },
  provincial_health_information_officer: { contextTypes: ["above_facility_support", "health_information_records", "system_administration"], scopeLevel: "province", defaultWorkspaces: ["provincial_data_quality", "reporting", "digital_health_support"] },
  district_health_information_officer: { contextTypes: ["above_facility_support", "health_information_records", "system_administration"], scopeLevel: "district", defaultWorkspaces: ["district_data_quality", "reporting", "district_support"] },
  medical_superintendent: { contextTypes: ["facility_administration", "facility_clinical", "audit_oversight"], scopeLevel: "facility", defaultWorkspaces: ["facility_dashboard", "facility_reports", "facility_staff_management", "clinical_oversight"] },
  matron: { contextTypes: ["facility_administration", "nursing", "training_mentorship"], scopeLevel: "facility", defaultWorkspaces: ["nursing_administration", "facility_staff_management", "training_compliance"] },
  sister_in_charge: { contextTypes: ["facility_clinical", "facility_operations"], scopeLevel: "unit_or_facility", defaultWorkspaces: ["ward_or_clinic_dashboard", "queue", "staff_roster", "client_registration"] },
  sister_in_charge_community: { contextTypes: ["community_outreach", "public_health", "programme_support"], scopeLevel: "catchment_or_district", defaultWorkspaces: ["community_outreach", "mobile_clinic", "community_follow_up"] },
  facility_ict_support: { contextTypes: ["facility_operations", "system_administration"], scopeLevel: "facility", defaultWorkspaces: ["facility_support", "devices_user_support", "facility_admin"], restrictedClinical: true },
  provider_registry_steward: { contextTypes: ["registry_governance"], scopeLevel: "assigned_registry", defaultWorkspaces: ["provider_registry_admin"] },
  marketplace_organisation_admin: { contextTypes: ["marketplace_operations"], scopeLevel: "organisation", defaultWorkspaces: ["marketplace_vendor_portal"], restrictedClinical: true },
};

function sectionRoles(section, codes, overrides = {}) {
  return codes.map((code) => ({ code, section, ...overrides, ...(typeof code === "object" ? code : {}) }));
}

const NATIONAL_LEADERSHIP = [
  "minister_of_health", "deputy_minister_of_health", "permanent_secretary", "staffing_officer",
  "executive_assistant_to_minister", "executive_assistant_to_deputy_minister", "executive_assistant_to_permanent_secretary",
  "chief_director_curative_services", "chief_director_public_health",
  "chief_director_health_systems_policy_compliance_coordination", "chief_director_support_services",
  "chief_director_officer", "director_head_office", "head_office_user", "personal_assistant",
  "executive_assistant", "protocol_officer", "public_relations_officer", "communications_media_liaison_officer",
  "camera_operator", "legal_services_officer", "health_legislative_officer", "gender_mainstreaming_inclusivity_officer",
  "wellness_officer", "risk_management_compliance_officer",
];

const CURATIVE_LEADERSHIP = [
  "director_nursing_services", "director_pharmacy_services", "director_medical_dental_services",
  "deputy_director_nursing_administration_technical_services", "deputy_director_community_nursing",
  "deputy_director_pharmacy_clinical_services_research", "deputy_director_supply_chain_management",
  "deputy_director_traditional_medicine_policy_practice_control", "deputy_director_rehabilitation",
  "deputy_director_mental_health", "deputy_director_radiation_protection", "deputy_director_emergency_medical_services",
  "nurse_administration_manager", "nurse_administration_officer", "infection_prevention_control_manager",
  "infection_prevention_control_officer", "safety_officer", "vaccination_manager", "vaccination_officer",
  "community_nursing_services_manager", "community_nursing_services_officer",
  "pharmacist_senior_training_manager", "pharmacist_senior_training_officer", "pharmacy_research_manager",
  "pharmacy_research_officer", "pharmacy_logistics_manager", "pharmacy_logistics_officer",
  "pharmacy_clinical_services_research_officer", "supply_chain_management_officer", "pharmacy_supply_chain_officer",
  "traditional_medicine_policy_practice_control_officer", "traditional_medicine_research_information_officer",
  "occupational_health_injury_manager", "chief_provincial_therapist", "speech_language_therapist", "audiologist",
  "prevention_disability_community_based_rehabilitation_manager", "clinical_rehabilitation_programs_manager",
  "mental_health_services_manager", "mental_health_services_officer", "mental_health_research_officer",
  "radiation_protection_officer", "senior_dental_services_officer", "dental_health_services_officer",
  "dental_research_information_officer", "emergency_medical_services_manager", "emergency_medical_services_training_officer",
  "emergency_medical_services_technician",
];

const PUBLIC_HEALTH = [
  "director_environmental_health_services", "director_government_analytical_laboratory_services",
  "director_family_health", "director_communicable_diseases", "director_non_communicable_diseases",
  "director_aids_tb_sti_malaria", "director_health_promotion",
  "deputy_director_wash", "waste_management_manager", "water_sanitation_manager",
  "occupational_health_chemical_safety_air_monitoring_manager", "safety_environmental_health_surveillance_contact_tracing_officer",
  "chemical_safety_air_quality_monitoring_officer", "deputy_director_food_safety_port_health",
  "port_health_manager", "port_health_officer", "food_safety_manager", "food_safety_officer",
  "deputy_director_food_water_quality_control", "food_water_quality_control_officer", "toxicology_officer",
  "deputy_director_child_health", "newborn_manager", "newborn_officer", "integrated_management_childhood_illness_manager",
  "imci_officer", "epi_manager", "epi_officer", "epi_logistics_officer", "cold_chain_technician",
  "epi_stores_officer", "epi_technician", "epi_stores_assistant", "deputy_director_nutrition_dietetics",
  "nutrition_intervention_manager", "nutrition_logistics_officer", "nutrition_emergency_preparedness_surveillance_manager",
  "nutrition_surveillance_officer", "hospital_food_services_coordinator", "hospital_food_services_training_officer",
  "deputy_director_reproductive_health", "adolescent_health_services_manager", "adolescent_health_services_officer",
  "reproductive_health_services_manager", "reproductive_health_services_officer",
  "deputy_director_epidemiology", "disease_surveillance_manager", "disease_surveillance_officer",
  "zoonotic_diseases_manager", "zoonotic_diseases_officer", "elimination_hepatitis_b_officer",
  "deputy_director_epr", "epr_manager", "epr_officer", "public_health_emergency_disaster_services_manager",
  "disaster_services_officer", "public_health_emergency_officer", "outbreak_response_officer",
  "deputy_director_ncd_integrated_management_multisectoral_coordination", "national_cancer_control_manager",
  "national_cancer_control_officer", "integrated_management_ncds_manager", "integrated_management_ncds_officer",
  "risk_factor_control_officer", "rehabilitation_ncd_officer",
  "deputy_director_hiv_aids_sti", "pmtct_paediatric_care_treatment_coordinator", "pmtct_art_mnch_officer",
  "pmtct_male_mobiliser_community_engagement_officer", "pmtct_training_mentorship_officer", "national_art_coordinator",
  "hiv_paediatric_officer", "hiv_sti_capacity_building_mentorship_officer", "tb_hiv_dsd_officer",
  "hiv_prevention_coordinator", "vmmc_program_officer", "hts_capacity_building_mentorship_testing_officer",
  "sti_condom_program_officer", "deputy_director_nmcp_case_management_focal_point", "vector_control_officer",
  "assistant_vector_control_officer", "elimination_coordinator", "deputy_director_tb_control",
  "tb_control_manager", "dots_training_officer", "community_tb_care_officer", "tb_logistics_manager",
  "programmatic_management_drug_resistant_tb_officer",
];

const HEALTH_SYSTEMS = [
  "director_health_policy_strategic_planning_health_economics", "director_health_performance_monitoring_evaluation",
  "director_program_planning_management", "director_quality_assurance_patient_safety", "director_risk_management_compliance",
  "deputy_director_policy_strategy_development_coordination", "health_policy_strategy_development_officer",
  "ppp_training_mentorship_officer", "health_reform_coordination_officer", "deputy_director_health_economics",
  "health_economist", "global_health_liaison_officer",
  "deputy_director_performance_monitoring_evaluation_public_health_research_development",
  "manager_monitoring_evaluation_public_health_research_development", "monitoring_evaluation_officer", "m_and_e_officer",
  "deputy_director_performance_monitoring_evaluation_service_delivery_enabling_environment",
  "manager_monitoring_evaluation_service_delivery_enabling_environment",
  "deputy_director_program_implementation", "program_implementation_manager",
  "deputy_director_program_management", "program_management_manager",
  "deputy_director_quality_assurance", "standard_setting_coordinator", "clinical_audit_officer",
  "benchmarking_officer", "sustainability_quality_assurance_officer", "quality_assurance_officer",
  "deputy_director_health_regulatory_compliance_services", "regulatory_compliance_officer",
  "deputy_director_risk_management", "risk_management_officer",
];

const SUPPORT_SERVICES = [
  "director_human_resources", "director_education_training_performance_management",
  "director_administration_logistics", "director_finance",
  "deputy_director_human_resources_management_recruitment", "principal_human_resources_officer",
  "human_resources_officer_executive_officers_personnel_management", "human_resources_assistant",
  "deputy_director_discipline_industrial_relations", "human_resources_officer_discipline_industrial_relations",
  "deputy_director_establishment_control", "human_resources_officer_establishment_control",
  "deputy_director_education_training", "training_development_officer",
  "human_resources_development_officer", "performance_management_officer",
  "deputy_director_administration", "administration_officer", "administration_executive_officer",
  "administrative_assistant", "deputy_director_logistics", "logistics_assistant",
  "deputy_director_records_information_registry", "records_information_officer", "records_information_assistant",
  "office_orderly", "librarian", "assistant_librarian",
  "chief_accountant_expenditure", "chief_accountant_revenue", "chief_accountant_suspense",
  "accountant_principal", "accountant_senior_principal", "accountant_senior", "accounting_assistant",
  "accounting_clerk", "finance_administration_director", "finance_administration_officer",
];

const DIGITAL_HEALTH = [
  "deputy_director_digital_health_information", "deputy_director_ict",
  "health_information_manager", "health_information_assistant", "data_science_digital_health_manager",
  "data_scientist", "digital_health_officer", "surveillance_manager", "gis_officer", "gis_technician",
  "software_program_analyst", "hardware_maintenance_officer", "technician", "telehealth_officer",
  "procurement_officer_digital_health", "data_manager", "interoperability_officer", "api_integration_engineer",
  "ehr_support_officer",
  "impilo_product_owner", "impilo_platform_engineer", "impilo_bff_engineer", "impilo_mobile_engineer",
  "impilo_security_engineer", "impilo_quality_assurance_engineer", "impilo_devops_engineer",
  "impilo_data_engineer", "impilo_support_engineer", "nompilo_prompt_orchestration_admin",
];

const INTERNAL_AUDIT = [
  "director_internal_audit", "chief_internal_auditor_government_programs", "chief_internal_auditor_special_projects",
  "internal_auditor", "auditor", "audit_assistant", "risk_management_compliance_manager", "risk_management_compliance_assistant",
  "quality_audit_officer", "registry_auditor", "marketplace_auditor", "access_review_auditor",
];

const PROVINCIAL_MEDICAL_OFFICE = [
  "pmd_executive_assistant", "provincial_health_services_director", "pedco_pmcho",
  "provincial_hiv_aids_sti_officer", "provincial_tb_control_officer", "provincial_malaria_disease_control_officer",
  "provincial_reproductive_health_officer", "provincial_environmental_health_officer",
  "provincial_environmental_health_officer_wash", "principal_environmental_health_officer_food_safety_port_health",
  "provincial_pharmacist", "provincial_child_welfare_officer", "provincial_reproductive_maternal_child_health_officer",
  "provincial_nutritionist", "senior_principal_nutrition_officer", "provincial_health_communication_advocacy_officer",
  "health_promotion_communication_advocacy_assistant", "provincial_m_and_e_officer",
  "provincial_accountant", "accounting_assistant_expenditure_revenue", "provincial_human_resources_officer",
  "human_resources_assistant", "administration_officer_executive_officer", "administration_assistant_clerk",
  "health_information_assistant", "procurement_officer", "procurement_assistant", "telehealth_officer",
  "mainstreaming_inclusivity_officer",
];

const CENTRAL_HOSPITAL = [
  "chief_executive_officer", "director_clinical_services", "director_operations", "director_finance",
  "principal_tutor", "chief_nursing_officer", "chief_radiographer", "clinical_psychologist",
  "principal_accountant", "deputy_director_procurement", "chief_biomedical_engineer", "chief_laboratory_scientist",
  "chief_medical_social_worker", "chief_rehabilitation_technician", "chief_transport_officer",
  "chief_procurement_officer", "chief_internal_auditor", "chief_security_officer", "chief_prosecutor",
  "chief_works_superintendent", "health_promotion_manager", "communication_advocacy_manager",
  "specialist_consultant", "consultant", "senior_registrar", "junior_registrar", "government_medical_officer",
  "senior_resident_medical_officer", "junior_resident_medical_officer", "intern", "dental_surgeon",
  "dental_therapist", "rehabilitation_technician", "medical_social_worker", "senior_tutor", "junior_tutor",
  "clinical_instructor", "student_nurse", "ward_manager", "health_services_administrator", "hospital_matrons",
  "domestic_supervisor", "housekeeper", "stores_supervisor", "linen_controller", "canteen_caterer", "tailor",
  "sewing_supervisor", "general_hand", "secretary", "security_guard", "casd_booker", "service_assistant",
  "clerk", "accountant", "account_clerk", "records_information_supervisor", "mortuary_attendant", "mortuary_hand",
  "security_assistant", "telephone_operator", "ict_assistant",
];

const PROVINCIAL_GENERAL_HOSPITAL = [
  "head_of_department_clinical_services", "matron_i", "matron_ii", "hospital_matron_i", "hospital_matron_ii",
  "hospital_matron_iii", "administrative_officer", "linen_officer", "accounts_clerk", "x_ray_operator",
  "dental_technician", "dental_surgical_assistant", "dark_room_assistant", "communication_advocacy_officer",
  "health_promotion_officer", "cook", "cashier",
];

const DISTRICT_CLINIC = [
  "district_environmental_health_officer", "district_pharmacist", "district_laboratory_focal_person",
  "district_health_promotion_officer", "district_programme_focal_person", "district_surveillance_officer",
  "district_m_and_e_officer", "district_training_coordinator", "district_outreach_coordinator",
  "district_registry_steward", "district_impilo_support_officer", "district_health_executive_member",
  "environmental_health_technician", "village_health_worker_supervisor", "data_clerk", "health_promotion_assistant",
  "outreach_worker", "facility_ehr_champion", "facility_telemedicine_focal_person", "facility_immunisation_focal_person",
  "facility_tb_focal_person", "facility_hiv_focal_person", "facility_malaria_focal_person", "facility_mch_focal_person",
];

/** Section defaults for programmatic role spec generation */
export const ORGANOGRAM_SECTIONS = [
  { id: "national_leadership", codes: NATIONAL_LEADERSHIP, category: "governance", scope: "national", contexts: ["above_facility_management", "audit_oversight", "system_administration"], workspaces: ["above_site_dashboard", "programme_dashboard"], perms: "executiveCore", directorate: "office_of_the_permanent_secretary" },
  { id: "curative_services", codes: CURATIVE_LEADERSHIP, category: "curative_services", scope: "national", contexts: ["programme_support", "facility_clinical", "training_mentorship"], workspaces: ["curative_services_console", "programme_dashboard"], perms: "curativeDirectorateCore", directorate: "directorate_curative_services" },
  { id: "public_health", codes: PUBLIC_HEALTH, category: "public_health", scope: "national", contexts: ["public_health_inspection", "programme_support", "community_outreach", "emergency_response"], workspaces: ["public_health_console", "programme_dashboard", "public_health_inspection"], perms: "publicHealthCore", directorate: "directorate_public_health" },
  { id: "health_systems", codes: HEALTH_SYSTEMS, category: "governance", scope: "national", contexts: ["above_facility_management", "programme_support", "audit_oversight"], workspaces: ["health_systems_policy_console", "programme_dashboard"], perms: "governanceCore", directorate: "directorate_health_systems_policy_compliance_coordination" },
  { id: "support_services", codes: SUPPORT_SERVICES, category: "support_services", scope: "national", contexts: ["facility_administration", "billing_finance", "supply_chain_logistics"], workspaces: ["support_services_console", "facility_admin"], perms: "supportServicesCore", directorate: "directorate_support_services" },
  { id: "digital_health", codes: DIGITAL_HEALTH, category: "digital_health", scope: "national", contexts: ["system_administration", "health_information_records", "integration_partner_operations"], workspaces: ["digital_health_console", "impilo_platform_console", "integration_console"], perms: "digitalHealthCore", directorate: "directorate_ict_digital_health_information" },
  { id: "internal_audit", codes: INTERNAL_AUDIT, category: "audit_compliance", scope: "national", contexts: ["audit_oversight", "regulatory_oversight"], workspaces: ["internal_audit_console", "audit_console"], perms: "auditReadOnly", directorate: "directorate_internal_audit" },
  { id: "provincial_medical_office", codes: PROVINCIAL_MEDICAL_OFFICE, category: "above_site_management", scope: "province", contexts: ["above_facility_management", "above_facility_support", "programme_support", "public_health_inspection"], workspaces: ["provincial_medical_office", "provincial_dashboard"], perms: "provincialOfficeCore", directorate: "provincial_medical_directorate" },
  { id: "central_hospital", codes: CENTRAL_HOSPITAL, category: "facility_management", scope: "facility", contexts: ["facility_administration", "facility_clinical", "facility_operations", "audit_oversight"], workspaces: ["central_hospital_dashboard", "facility_dashboard", "facility_staff_management"], perms: "facilityLeadershipCore", directorate: "central_hospital_executive", workplaceType: "central_hospital" },
  { id: "provincial_general_hospital", codes: PROVINCIAL_GENERAL_HOSPITAL, category: "facility_management", scope: "facility", contexts: ["facility_administration", "facility_clinical"], workspaces: ["provincial_hospital_dashboard", "general_hospital_dashboard", "facility_dashboard"], perms: "facilityLeadershipCore", directorate: "provincial_hospital_management", workplaceType: "provincial_hospital" },
  { id: "district_clinic", codes: DISTRICT_CLINIC, category: "above_site_management", scope: "district", contexts: ["above_facility_management", "above_facility_support", "community_outreach", "programme_support"], workspaces: ["district_dashboard", "district_support", "community_outreach"], perms: "districtTeamCore", directorate: "district_medical_office" },
];

/** Permission baseline keys referenced by organogram sections */
export const ORGANOGRAM_PERMISSION_BASELINES = {
  executiveCore: ["work.context.enter", "facility.reports.view", "audit.logs.view", "policy.bundle.view"],
  curativeDirectorateCore: ["work.context.enter", "facility.reports.view", "professional.profile.view", "audit.logs.view"],
  publicHealthCore: ["work.context.enter", "facility.reports.view", "regulator.inspect", "audit.logs.view"],
  supportServicesCore: ["work.context.enter", "facility.reports.view", "facility.config.manage"],
  digitalHealthCore: ["work.context.enter", "trust.context.inspect", "integration.api_keys.manage", "audit.logs.view"],
  auditReadOnly: ["audit.logs.view", "facility.audit.view"],
  provincialOfficeCore: ["work.context.enter", "facility.reports.view", "facility.staff.view", "audit.logs.view"],
  facilityLeadershipCore: ["work.context.enter", "facility.dashboard.view", "facility.staff.view", "facility.reports.view", "facility.audit.view"],
  districtTeamCore: ["work.context.enter", "facility.reports.view", "work.tasks.view", "audit.logs.view"],
};

/**
 * Build role template specs for organogram roles not already present.
 * @param {Function} spec - spec(code, category, contexts, workspaces, cadres, perms, scope)
 * @param {Set<string>} existingCodes
 */
export function buildOrganogramRoleSpecs(spec, existingCodes = new Set()) {
  const out = [];
  for (const section of ORGANOGRAM_SECTIONS) {
    for (const code of section.codes) {
      if (existingCodes.has(code)) continue;
      const mapping = ORGANOGRAM_CONTEXT_MAPPINGS[code];
      const contexts = mapping?.contextTypes ?? section.contexts;
      const workspaces = mapping?.defaultWorkspaces ?? section.workspaces;
      const scope = mapping?.scopeLevel ?? section.scope;
      const perms = section.perms;
      out.push(spec(code, section.category, contexts, workspaces, [], perms, scope));
      existingCodes.add(code);
    }
  }
  return out;
}

export function mergeOrganogramRoleProfiles(baseProfiles) {
  return { ...baseProfiles, ...ORGANOGRAM_ROLE_PROFILES };
}

export function applyOrganogramContextMapping(code, specTuple) {
  const mapping = ORGANOGRAM_CONTEXT_MAPPINGS[code];
  if (!mapping) return specTuple;
  const [c, cat, , , cadres, perms, scope] = specTuple;
  return [c, cat, mapping.contextTypes, mapping.defaultWorkspaces, cadres, perms, mapping.scopeLevel ?? scope];
}
