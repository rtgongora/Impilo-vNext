/**
 * Zimbabwe MoHCC trust catalogue seed source data.
 * This module is consumed by trust catalogue generation scripts.
 */

import {
  ORGANOGRAM_SOURCE,
  ORGANOGRAM_WORKSPACES,
  ORGANOGRAM_ORG_STRUCTURES,
  ORGANOGRAM_ROLE_PROFILES,
  ORGANOGRAM_PERMISSION_BASELINES,
  buildOrganogramRoleSpecs,
  applyOrganogramContextMapping,
} from "./mohcc-organogram-roles.mjs";

export { ORGANOGRAM_SOURCE, ORGANOGRAM_WORKSPACES };

export const CATALOGUE_DATA_VERSION = "1.3.0";
export const VERSION_BUMP_NOTE = "Zimbabwe MoHCC organogram-aware seed data (HSC approved 2025-03-05).";

function titleCase(value) {
  return value
    .replace(/_/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function spec(code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel) {
  return [code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel];
}

function cadreProfile(displayName, clinical, contexts, roles, extra = {}) {
  return { displayName, clinical, contexts, roles, ...extra };
}

function ensureUniqueRoleCodes(specs) {
  const seen = new Set();
  for (const [code] of specs) {
    if (seen.has(code)) {
      throw new Error(`Duplicate role template code in Zimbabwe catalogue data: ${code}`);
    }
    seen.add(code);
  }
  return specs;
}

function roleScope(scopeLevel) {
  return scopeLevel ? { policyScopeLevel: scopeLevel } : {};
}

const DEFAULT_ASSIGNMENT_TYPES = [
  "facility_assignment",
  "department_assignment",
  "unit_assignment",
  "workspace_assignment",
  "programme_assignment",
  "above_site_assignment",
];

const FACILITY_ASSIGNMENT_TYPES = [
  "facility_assignment",
  "department_assignment",
  "unit_assignment",
  "workspace_assignment",
];

const COMMUNITY_ASSIGNMENT_TYPES = [
  "community_outreach_assignment",
  "mobile_clinic_assignment",
  "public_health_place_assignment",
  "workspace_assignment",
];

const MARKETPLACE_ASSIGNMENT_TYPES = [
  "marketplace_organisation_assignment",
  "marketplace_product_assignment",
  "workspace_assignment",
];

const REGISTRY_ASSIGNMENT_TYPES = [
  "registry_governance_assignment",
  "workspace_assignment",
];

const REGULATOR_ASSIGNMENT_TYPES = [
  "regulatory_assignment",
  "workspace_assignment",
];

export const ZW_ADMIN_LEVELS = [
  {
    code: "national",
    displayName: "National",
    description: "National MoHCC policy, standards, governance, and national programmes.",
    parentLevel: null,
    allowedRoleTemplates: [
      "national_medical_director",
      "national_nursing_director",
      "national_health_information_director",
      "national_programme_director",
      "national_platform_administrator",
      "professional_council_regulator",
    ],
    allowedAssignmentTypes: DEFAULT_ASSIGNMENT_TYPES,
    defaultDashboards: ["above_site_dashboard", "programme_dashboard", "system_admin_console"],
    policyScope: "national",
  },
  {
    code: "province",
    displayName: "Province",
    description: "Provincial health executive oversight and support structures.",
    parentLevel: "national",
    allowedRoleTemplates: [
      "provincial_medical_director",
      "provincial_nursing_officer",
      "provincial_health_information_officer",
      "provincial_epidemiology_disease_control_officer",
      "provincial_support_officer",
    ],
    allowedAssignmentTypes: DEFAULT_ASSIGNMENT_TYPES,
    defaultDashboards: ["above_site_dashboard", "programme_dashboard"],
    policyScope: "province",
  },
  {
    code: "district",
    displayName: "District",
    description: "District health executive management and district programme coordination.",
    parentLevel: "province",
    allowedRoleTemplates: [
      "district_medical_officer",
      "district_nursing_officer",
      "district_health_information_officer",
      "district_support_officer",
      "district_programme_coordinator",
    ],
    allowedAssignmentTypes: DEFAULT_ASSIGNMENT_TYPES,
    defaultDashboards: ["above_site_dashboard", "programme_dashboard"],
    policyScope: "district",
  },
  {
    code: "city_local_authority",
    displayName: "City / Local Authority",
    description: "Urban local authority and city health department structures.",
    parentLevel: "district",
    allowedRoleTemplates: ["city_health_director", "city_health_information_officer", "city_environmental_health_officer"],
    allowedAssignmentTypes: DEFAULT_ASSIGNMENT_TYPES,
    defaultDashboards: ["public_health_inspection", "above_site_dashboard"],
    policyScope: "district",
  },
  {
    code: "facility",
    displayName: "Facility",
    description: "Hospital, clinic, health centre, and facility operations scope.",
    parentLevel: "district",
    allowedRoleTemplates: [
      "medical_superintendent",
      "matron",
      "sister_in_charge",
      "general_medical_officer",
      "pharmacist",
      "laboratory_scientist",
      "facility_administrator",
    ],
    allowedAssignmentTypes: FACILITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["facility_dashboard", "facility_admin", "facility_reports"],
    policyScope: "facility",
  },
  {
    code: "department",
    displayName: "Department",
    description: "Facility department-level service delivery and administration.",
    parentLevel: "facility",
    allowedRoleTemplates: ["head_of_department_clinical", "department_administrator", "department_nurse_manager"],
    allowedAssignmentTypes: FACILITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["facility_department_dashboard", "facility_staff_management"],
    policyScope: "facility",
  },
  {
    code: "unit",
    displayName: "Unit",
    description: "Service unit (ward, theatre, lab bench, pharmacy station, records section).",
    parentLevel: "department",
    allowedRoleTemplates: ["unit_clinical_lead", "unit_nursing_lead", "unit_records_lead"],
    allowedAssignmentTypes: FACILITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["work", "facility_staff_management"],
    policyScope: "facility",
  },
  {
    code: "ward",
    displayName: "Ward",
    description: "Inpatient or specialty ward-level clinical and nursing operations.",
    parentLevel: "unit",
    allowedRoleTemplates: ["ward_nurse", "nurse_in_charge", "ward_clerk"],
    allowedAssignmentTypes: FACILITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["inpatient", "work"],
    policyScope: "facility",
  },
  {
    code: "community",
    displayName: "Community",
    description: "Community outreach structures linked to a district and catchment.",
    parentLevel: "district",
    allowedRoleTemplates: [
      "sister_in_charge_community",
      "community_health_worker",
      "community_surveillance_officer",
      "community_data_clerk",
    ],
    allowedAssignmentTypes: COMMUNITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["community_outreach", "programme_support"],
    policyScope: "community",
  },
  {
    code: "village",
    displayName: "Village",
    description: "Village-level community health and engagement structures.",
    parentLevel: "community",
    allowedRoleTemplates: ["village_health_worker", "village_health_committee_chair"],
    allowedAssignmentTypes: COMMUNITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["community_outreach"],
    policyScope: "community",
  },
  {
    code: "catchment_area",
    displayName: "Catchment Area",
    description: "Facility catchment geography and population coverage area.",
    parentLevel: "district",
    allowedRoleTemplates: ["catchment_surveillance_officer", "catchment_outreach_coordinator"],
    allowedAssignmentTypes: COMMUNITY_ASSIGNMENT_TYPES,
    defaultDashboards: ["community_outreach", "programme_dashboard"],
    policyScope: "community",
  },
  {
    code: "virtual",
    displayName: "Virtual",
    description: "Virtual and telemedicine workspace scope.",
    parentLevel: "national",
    allowedRoleTemplates: ["telemedicine_provider", "telemedicine_specialist_reviewer", "virtual_triage_nurse"],
    allowedAssignmentTypes: ["telemedicine_pool_assignment", "workspace_assignment"],
    defaultDashboards: ["telemedicine_pool", "telemedicine_triage", "telemedicine_specialist_review"],
    policyScope: "facility",
  },
  {
    code: "programme",
    displayName: "Programme",
    description: "Programme implementation units across national to community levels.",
    parentLevel: "national",
    allowedRoleTemplates: ["national_programme_director", "provincial_programme_coordinator", "district_programme_coordinator"],
    allowedAssignmentTypes: ["programme_assignment", "workspace_assignment"],
    defaultDashboards: ["programme_dashboard", "programme_support"],
    policyScope: "national",
  },
  {
    code: "marketplace",
    displayName: "Marketplace",
    description: "Marketplace provider, vendor, and compliance access scope.",
    parentLevel: "national",
    allowedRoleTemplates: ["marketplace_organisation_admin", "marketplace_product_manager", "marketplace_compliance_officer"],
    allowedAssignmentTypes: MARKETPLACE_ASSIGNMENT_TYPES,
    defaultDashboards: ["marketplace_vendor_portal", "marketplace_product_catalogue", "marketplace_compliance_monitoring"],
    policyScope: "marketplace",
  },
  {
    code: "registry",
    displayName: "Registry",
    description: "Registry governance and stewardship responsibilities.",
    parentLevel: "national",
    allowedRoleTemplates: ["provider_registry_owner", "client_registry_owner", "facility_registry_owner"],
    allowedAssignmentTypes: REGISTRY_ASSIGNMENT_TYPES,
    defaultDashboards: ["provider_registry_admin", "client_registry_admin", "facility_registry_admin"],
    policyScope: "registry",
  },
  {
    code: "regulator",
    displayName: "Regulator",
    description: "Professional, facility, and product regulatory oversight functions.",
    parentLevel: "national",
    allowedRoleTemplates: ["professional_council_regulator", "facility_licensing_regulator", "medicines_products_regulator"],
    allowedAssignmentTypes: REGULATOR_ASSIGNMENT_TYPES,
    defaultDashboards: ["professional_council_review", "facility_licensing_review", "medicines_product_review"],
    policyScope: "national",
  },
  {
    code: "partner_organisation",
    displayName: "Partner Organisation",
    description: "Development, implementing partner, and contracted programme entities.",
    parentLevel: "national",
    allowedRoleTemplates: ["partner_programme_manager", "partner_data_officer", "partner_outreach_coordinator"],
    allowedAssignmentTypes: ["integration_partner_assignment", "programme_assignment", "workspace_assignment"],
    defaultDashboards: ["programme_support", "integration_console"],
    policyScope: "national",
  },
];

export const ZW_ORG_STRUCTURES = [
  { code: "mohcc_national_headquarters", displayName: "MoHCC National Headquarters", level: "national", description: "National headquarters for Zimbabwe MoHCC governance and policy." },
  { code: "office_of_the_permanent_secretary", displayName: "Office of the Permanent Secretary", level: "national", parentStructure: "mohcc_national_headquarters", description: "Executive administration and strategic oversight." },
  { code: "directorate_clinical_services", displayName: "Directorate of Clinical Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "National clinical governance and standards." },
  { code: "directorate_nursing_services", displayName: "Directorate of Nursing Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "National nursing policy and supervision." },
  { code: "directorate_public_health", displayName: "Directorate of Public Health", level: "national", parentStructure: "mohcc_national_headquarters", description: "Disease prevention, surveillance, and public health operations." },
  { code: "directorate_health_information", displayName: "Directorate of Health Information", level: "national", parentStructure: "mohcc_national_headquarters", description: "National health information and records governance." },
  { code: "directorate_pharmaceutical_services", displayName: "Directorate of Pharmaceutical Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "Medication, dispensing, and pharmacy systems leadership." },
  { code: "directorate_laboratory_services", displayName: "Directorate of Laboratory Services", level: "national", parentStructure: "mohcc_national_headquarters", description: "National laboratory services and quality standards." },
  { code: "directorate_finance_and_administration", displayName: "Directorate of Finance and Administration", level: "national", parentStructure: "mohcc_national_headquarters", description: "Finance, HR, procurement, and administrative control." },
  { code: "directorate_digital_health", displayName: "Directorate of Digital Health", level: "national", parentStructure: "mohcc_national_headquarters", description: "National digital health and platform operations." },
  { code: "directorate_emergency_preparedness", displayName: "Directorate of Emergency Preparedness", level: "national", parentStructure: "mohcc_national_headquarters", description: "Preparedness, incident command, and emergency response." },

  { code: "provincial_health_executive", displayName: "Provincial Health Executive", level: "province", parentStructure: "mohcc_national_headquarters", description: "Provincial executive management for health operations." },
  { code: "provincial_medical_office", displayName: "Provincial Medical Office", level: "province", parentStructure: "provincial_health_executive", description: "Provincial medical leadership and supervision." },
  { code: "provincial_nursing_office", displayName: "Provincial Nursing Office", level: "province", parentStructure: "provincial_health_executive", description: "Provincial nursing oversight and workforce supervision." },
  { code: "provincial_health_information_unit", displayName: "Provincial Health Information Unit", level: "province", parentStructure: "provincial_health_executive", description: "Provincial reporting, records, and data quality supervision." },
  { code: "provincial_public_health_unit", displayName: "Provincial Public Health Unit", level: "province", parentStructure: "provincial_health_executive", description: "Provincial epidemiology, surveillance, and disease control." },
  { code: "provincial_pharmacy_unit", displayName: "Provincial Pharmacy Unit", level: "province", parentStructure: "provincial_health_executive", description: "Provincial pharmaceutical distribution and supervision." },
  { code: "provincial_laboratory_unit", displayName: "Provincial Laboratory Unit", level: "province", parentStructure: "provincial_health_executive", description: "Provincial laboratory quality and referral network supervision." },

  { code: "district_health_executive", displayName: "District Health Executive", level: "district", parentStructure: "provincial_health_executive", description: "District-level health executive management." },
  { code: "district_medical_office", displayName: "District Medical Office", level: "district", parentStructure: "district_health_executive", description: "District medical leadership and district service integration." },
  { code: "district_nursing_office", displayName: "District Nursing Office", level: "district", parentStructure: "district_health_executive", description: "District nursing supervision and quality control." },
  { code: "district_health_information_unit", displayName: "District Health Information Unit", level: "district", parentStructure: "district_health_executive", description: "District records and routine reporting operations." },
  { code: "district_public_health_unit", displayName: "District Public Health Unit", level: "district", parentStructure: "district_health_executive", description: "District surveillance, outbreak response, and prevention operations." },
  { code: "district_programme_support_unit", displayName: "District Programme Support Unit", level: "district", parentStructure: "district_health_executive", description: "District programme implementation support and M&E." },
  { code: "city_health_department", displayName: "City Health Department", level: "city_local_authority", parentStructure: "district_health_executive", description: "Urban local authority health operations." },
  { code: "rural_district_health_office", displayName: "Rural District Health Office", level: "city_local_authority", parentStructure: "district_health_executive", description: "Local authority support for rural health operations." },

  { code: "central_hospital_management", displayName: "Central Hospital Management", level: "facility", parentStructure: "district_health_executive", description: "Tertiary and quaternary hospital leadership structures." },
  { code: "provincial_hospital_management", displayName: "Provincial Hospital Management", level: "facility", parentStructure: "district_health_executive", description: "Provincial referral hospital leadership structures." },
  { code: "district_hospital_management", displayName: "District Hospital Management", level: "facility", parentStructure: "district_health_executive", description: "District hospital leadership and integrated services." },
  { code: "mission_hospital_management", displayName: "Mission Hospital Management", level: "facility", parentStructure: "district_health_executive", description: "Mission and church-run hospital governance linked to MoHCC standards." },
  { code: "urban_polyclinic_management", displayName: "Urban Polyclinic Management", level: "facility", parentStructure: "city_health_department", description: "Urban clinic management and community-linked services." },
  { code: "rural_health_centre_management", displayName: "Rural Health Centre Management", level: "facility", parentStructure: "district_health_executive", description: "Primary care facility operations in rural settings." },
  { code: "clinic_management", displayName: "Clinic Management", level: "facility", parentStructure: "district_health_executive", description: "Primary care clinic operational management." },

  { code: "facility_clinical_department", displayName: "Facility Clinical Department", level: "department", parentStructure: "district_hospital_management", description: "Clinical departments (medicine, surgery, paediatrics, obstetrics)." },
  { code: "facility_nursing_department", displayName: "Facility Nursing Department", level: "department", parentStructure: "district_hospital_management", description: "Nursing operations and ward management." },
  { code: "facility_pharmacy_department", displayName: "Facility Pharmacy Department", level: "department", parentStructure: "district_hospital_management", description: "Pharmacy, dispensing, and stock management." },
  { code: "facility_laboratory_department", displayName: "Facility Laboratory Department", level: "department", parentStructure: "district_hospital_management", description: "Diagnostic and laboratory services." },
  { code: "facility_records_department", displayName: "Facility Records Department", level: "department", parentStructure: "district_hospital_management", description: "Health information and records management." },
  { code: "facility_finance_department", displayName: "Facility Finance Department", level: "department", parentStructure: "district_hospital_management", description: "Billing, claims, and finance operations." },

  { code: "inpatient_ward_unit", displayName: "Inpatient Ward Unit", level: "ward", parentStructure: "facility_nursing_department", description: "General inpatient nursing and ward operations." },
  { code: "maternity_ward_unit", displayName: "Maternity Ward Unit", level: "ward", parentStructure: "facility_nursing_department", description: "Maternity and newborn ward operations." },
  { code: "theatre_unit", displayName: "Theatre Unit", level: "unit", parentStructure: "facility_clinical_department", description: "Surgical theatre operations." },
  { code: "emergency_unit", displayName: "Emergency Unit", level: "unit", parentStructure: "facility_clinical_department", description: "Emergency and triage operations." },

  { code: "community_health_post", displayName: "Community Health Post", level: "community", parentStructure: "district_health_executive", description: "Community-level health outreach structure." },
  { code: "village_health_worker_network", displayName: "Village Health Worker Network", level: "village", parentStructure: "community_health_post", description: "Village health worker and community committee network." },
  { code: "catchment_health_team", displayName: "Catchment Health Team", level: "catchment_area", parentStructure: "district_health_executive", description: "Catchment-focused outreach and surveillance teams." },
  { code: "community_outreach_team", displayName: "Community Outreach Team", level: "community", parentStructure: "community_health_post", description: "Outreach team for household and community services." },

  { code: "virtual_telemedicine_hub", displayName: "Virtual Telemedicine Hub", level: "virtual", parentStructure: "directorate_digital_health", description: "Virtual consultation and telemedicine coordination node." },
  { code: "programme_coordination_unit", displayName: "Programme Coordination Unit", level: "programme", parentStructure: "mohcc_national_headquarters", description: "Cross-cutting programme governance and implementation support." },
  { code: "marketplace_operations_unit", displayName: "Marketplace Operations Unit", level: "marketplace", parentStructure: "directorate_digital_health", description: "Marketplace provider onboarding and operational governance." },
  { code: "registry_governance_unit", displayName: "Registry Governance Unit", level: "registry", parentStructure: "directorate_digital_health", description: "Provider, client, facility, and terminology registry governance." },
  { code: "regulatory_inspectorate_unit", displayName: "Regulatory Inspectorate Unit", level: "regulator", parentStructure: "directorate_public_health", description: "Regulatory inspection, certification, and compliance operations." },
  { code: "partner_coordination_unit", displayName: "Partner Coordination Unit", level: "partner_organisation", parentStructure: "programme_coordination_unit", description: "Coordination for implementing and development partners." },
  ...ORGANOGRAM_ORG_STRUCTURES.filter(
    (o) => !["directorate_public_health"].includes(o.code),
  ).map((o) => ({
    ...o,
    parentStructure: o.parentStructure ?? "mohcc_national_headquarters",
  })),
];

const CADRE_DEFINITIONS = [
  ["general_medical_practitioner", cadreProfile("General Medical Practitioner", "clinical", ["facility_clinical", "virtual_care"], ["general_medical_officer", "admitting_doctor", "discharging_doctor"])],
  ["specialist_physician", cadreProfile("Specialist Physician", "clinical", ["facility_clinical", "virtual_care"], ["specialist_doctor", "consultant_specialist", "telemedicine_specialist_reviewer"])],
  ["specialist_surgeon", cadreProfile("Specialist Surgeon", "clinical", ["facility_clinical"], ["specialist_doctor", "theatre_consultant"])],
  ["obstetrician_gynaecologist", cadreProfile("Obstetrician and Gynaecologist", "clinical", ["facility_clinical"], ["specialist_doctor", "obgyn_specialist"])],
  ["paediatrician", cadreProfile("Paediatrician", "clinical", ["facility_clinical"], ["specialist_doctor", "paediatrics_specialist"])],
  ["psychiatrist", cadreProfile("Psychiatrist", "clinical", ["facility_clinical", "virtual_care"], ["specialist_doctor", "mental_health_specialist"])],
  ["anaesthetist", cadreProfile("Anaesthetist", "clinical", ["facility_clinical"], ["anaesthesia_specialist"])],
  ["radiologist", cadreProfile("Radiologist", "clinical", ["radiology_imaging"], ["imaging_reviewer", "radiology_supervisor"])],
  ["pathologist", cadreProfile("Pathologist", "clinical", ["laboratory_services"], ["laboratory_supervisor", "reference_lab_consultant"])],
  ["public_health_physician", cadreProfile("Public Health Physician", "clinical", ["public_health_inspection", "programme_support"], ["public_health_regulator", "national_public_health_director"])],
  ["dental_practitioner", cadreProfile("Dental Practitioner", "clinical", ["facility_clinical"], ["dental_officer"])],
  ["registered_general_nurse", cadreProfile("Registered General Nurse", "clinical", ["facility_clinical"], ["ward_nurse", "triage_nurse"])],
  ["primary_care_nurse", cadreProfile("Primary Care Nurse", "clinical", ["facility_clinical", "community_outreach"], ["outreach_nurse", "community_primary_care_nurse"])],
  ["midwife", cadreProfile("Midwife", "clinical", ["facility_clinical", "community_outreach"], ["midwife", "maternity_nurse", "community_midwife"])],
  ["theatre_nurse", cadreProfile("Theatre Nurse", "clinical", ["facility_clinical"], ["theatre_nurse", "theatre_nurse_in_charge"])],
  ["psychiatric_nurse", cadreProfile("Psychiatric Nurse", "clinical", ["facility_clinical"], ["mental_health_nurse"])],
  ["nurse_manager", cadreProfile("Nurse Manager", "clinical", ["facility_administration"], ["nursing_manager", "department_nurse_manager"])],
  ["nurse_aide", cadreProfile("Nurse Aide", "clinical", ["facility_clinical"], ["ward_nurse_assistant"])],
  ["clinical_officer", cadreProfile("Clinical Officer", "clinical", ["facility_clinical"], ["clinical_officer"])],
  ["pharmacist", cadreProfile("Pharmacist", "clinical", ["pharmacy_services"], ["pharmacist", "dispensing_pharmacist"])],
  ["chief_pharmacist", cadreProfile("Chief Pharmacist", "clinical", ["pharmacy_services"], ["chief_pharmacist", "provincial_pharmacy_officer"])],
  ["pharmacy_technician", cadreProfile("Pharmacy Technician", "clinical", ["pharmacy_services"], ["pharmacy_technician", "pharmacy_store_controller"])],
  ["dispensary_assistant", cadreProfile("Dispensary Assistant", "clinical", ["pharmacy_services"], ["dispensary_assistant"])],
  ["laboratory_scientist", cadreProfile("Laboratory Scientist", "clinical", ["laboratory_services"], ["laboratory_scientist", "laboratory_supervisor"])],
  ["laboratory_technician", cadreProfile("Laboratory Technician", "clinical", ["laboratory_services"], ["specimen_collector", "laboratory_technician"])],
  ["radiographer", cadreProfile("Radiographer", "clinical", ["radiology_imaging"], ["radiographer"])],
  ["sonographer", cadreProfile("Sonographer", "clinical", ["radiology_imaging"], ["sonographer"])],
  ["physiotherapist", cadreProfile("Physiotherapist", "clinical", ["facility_clinical"], ["physiotherapist"])],
  ["occupational_therapist", cadreProfile("Occupational Therapist", "clinical", ["facility_clinical"], ["occupational_therapist"])],
  ["dietician", cadreProfile("Dietician", "clinical", ["facility_clinical"], ["dietician"])],
  ["nutritionist", cadreProfile("Nutritionist", "clinical", ["facility_clinical", "community_outreach"], ["nutritionist", "community_nutrition_officer"])],
  ["psychologist", cadreProfile("Psychologist", "clinical", ["facility_clinical", "virtual_care"], ["psychologist"])],
  ["counsellor", cadreProfile("Counsellor", "clinical", ["facility_clinical", "community_outreach"], ["counsellor", "hiv_counsellor"])],
  ["social_worker", cadreProfile("Social Worker", "clinical", ["community_outreach"], ["social_worker", "community_social_worker"])],
  ["environmental_health_practitioner", cadreProfile("Environmental Health Practitioner", "clinical", ["public_health_inspection", "community_outreach"], ["environmental_health_officer", "public_health_inspector"])],
  ["community_health_worker", cadreProfile("Community Health Worker", "clinical", ["community_outreach"], ["community_health_worker", "village_health_worker"])],
  ["emergency_medical_technician", cadreProfile("Emergency Medical Technician", "clinical", ["emergency_response"], ["emergency_response_provider"])],
  ["ambulance_technician", cadreProfile("Ambulance Technician", "clinical", ["emergency_response"], ["ambulance_technician"])],
  ["health_information_officer", cadreProfile("Health Information Officer", "non_clinical", ["health_information_records", "above_facility_support"], ["health_information_officer", "records_supervisor"])],
  ["data_capture_clerk", cadreProfile("Data Capture Clerk", "non_clinical", ["health_information_records", "community_outreach"], ["facility_data_clerk", "community_data_clerk"])],
  ["records_clerk", cadreProfile("Records Clerk", "non_clinical", ["health_information_records"], ["records_clerk", "ward_clerk"])],
  ["registry_officer", cadreProfile("Registry Officer", "non_clinical", ["registry_governance"], ["provider_registry_steward", "client_registry_resolution_officer"])],
  ["billing_officer", cadreProfile("Billing Officer", "non_clinical", ["billing_finance"], ["billing_officer", "claims_officer"])],
  ["cashier", cadreProfile("Cashier", "non_clinical", ["billing_finance"], ["cashier"])],
  ["facility_administrator", cadreProfile("Facility Administrator", "non_clinical", ["facility_administration", "facility_operations"], ["facility_administrator", "facility_staff_manager"])],
  ["hospital_manager", cadreProfile("Hospital Manager", "non_clinical", ["facility_administration"], ["hospital_manager"])],
  ["medical_superintendent", cadreProfile("Medical Superintendent", "non_clinical", ["facility_administration"], ["medical_superintendent"], { abbreviation: "MS" })],
  ["matron", cadreProfile("Matron", "clinical", ["facility_administration"], ["matron"], { abbreviation: "Matron" })],
  ["department_administrator", cadreProfile("Department Administrator", "non_clinical", ["facility_administration"], ["department_administrator"])],
  ["monitoring_and_evaluation_officer", cadreProfile("Monitoring and Evaluation Officer", "non_clinical", ["programme_support"], ["programme_m_and_e_officer", "district_monitoring_and_evaluation_officer"])],
  ["helpdesk_agent", cadreProfile("Helpdesk Agent", "non_clinical", ["system_administration"], ["national_helpdesk_agent", "support_agent"])],
  ["department_head", cadreProfile("Department Head", "mixed", ["facility_clinical", "facility_administration"], ["head_of_department_clinical"])],
  ["programme_manager", cadreProfile("Programme Manager", "non_clinical", ["programme_support", "above_facility_support"], ["programme_manager", "district_programme_coordinator", "provincial_programme_coordinator"])],
  ["national_programme_officer", cadreProfile("National Programme Officer", "non_clinical", ["programme_support"], ["national_programme_director", "national_programme_administrator"])],
  ["ict_officer", cadreProfile("ICT Officer", "non_clinical", ["system_administration", "facility_operations"], ["facility_ict_support", "district_digital_health_officer"])],
  ["system_support_officer", cadreProfile("System Support Officer", "non_clinical", ["system_administration"], ["national_platform_support", "support_agent"])],
  ["supply_chain_officer", cadreProfile("Supply Chain Officer", "non_clinical", ["supply_chain_logistics"], ["medicines_store_manager", "district_logistics_officer"])],
  ["procurement_officer", cadreProfile("Procurement Officer", "non_clinical", ["supply_chain_logistics"], ["procurement_officer"])],
  ["warehouse_officer", cadreProfile("Warehouse Officer", "non_clinical", ["supply_chain_logistics"], ["warehouse_officer"])],
  ["logistics_officer", cadreProfile("Logistics Officer", "non_clinical", ["supply_chain_logistics"], ["logistics_officer"])],
  ["trainer", cadreProfile("Trainer", "non_clinical", ["training_mentorship"], ["fundo_trainer", "fundo_course_author"])],
  ["mentor", cadreProfile("Mentor", "non_clinical", ["training_mentorship"], ["mentor_supervisor", "district_mentor"])],
  ["supervisor", cadreProfile("Supervisor", "mixed", ["training_mentorship", "community_outreach"], ["community_supervisor", "emergency_response_coordinator"])],
  ["auditor", cadreProfile("Auditor", "non_clinical", ["audit_oversight"], ["facility_audit_viewer", "regulatory_auditor"])],
  ["inspector", cadreProfile("Inspector", "non_clinical", ["public_health_inspection", "regulatory_oversight"], ["public_health_inspector", "compliance_inspector"])],
  ["district_medical_officer", cadreProfile("District Medical Officer", "non_clinical", ["above_facility_management", "above_facility_support"], ["district_medical_officer"], { abbreviation: "DMO", primaryZimbabweRole: true, aliases: ["district_health_executive_chair"] })],
  ["provincial_medical_director", cadreProfile("Provincial Medical Director", "non_clinical", ["above_facility_management", "above_facility_support"], ["provincial_medical_director"], { abbreviation: "PMD", primaryZimbabweRole: true, aliases: ["provincial_health_executive_lead"] })],
  ["district_nursing_officer", cadreProfile("District Nursing Officer", "clinical", ["above_facility_support", "facility_administration"], ["district_nursing_officer"], { abbreviation: "DNO", primaryZimbabweRole: true })],
  ["provincial_nursing_officer", cadreProfile("Provincial Nursing Officer", "clinical", ["above_facility_support", "facility_administration"], ["provincial_nursing_officer"], { abbreviation: "PNO", primaryZimbabweRole: true })],
  ["district_health_information_officer", cadreProfile("District Health Information Officer", "non_clinical", ["above_facility_support", "health_information_records"], ["district_health_information_officer"], { abbreviation: "DHIO", primaryZimbabweRole: true })],
  ["provincial_health_information_officer", cadreProfile("Provincial Health Information Officer", "non_clinical", ["above_facility_support", "health_information_records"], ["provincial_health_information_officer"], { abbreviation: "PHIO", primaryZimbabweRole: true })],
  ["provincial_epidemiology_disease_control_officer", cadreProfile("Provincial Epidemiology and Disease Control Officer", "non_clinical", ["public_health_inspection", "above_facility_support"], ["provincial_epidemiology_disease_control_officer"], { abbreviation: "PEDCO", primaryZimbabweRole: true })],
  ["sister_in_charge", cadreProfile("Sister In Charge", "clinical", ["facility_clinical", "facility_administration"], ["sister_in_charge"], { abbreviation: "SIC", primaryZimbabweRole: true })],
  ["sister_in_charge_community", cadreProfile("Sister In Charge (Community)", "clinical", ["community_outreach", "facility_administration"], ["sister_in_charge_community"], { abbreviation: "SICC", primaryZimbabweRole: true })],
  ["city_health_director", cadreProfile("City Health Director", "non_clinical", ["above_facility_management", "public_health_inspection"], ["city_health_director"])],
  ["city_health_information_officer", cadreProfile("City Health Information Officer", "non_clinical", ["above_facility_support"], ["city_health_information_officer"])],
  ["city_environmental_health_officer", cadreProfile("City Environmental Health Officer", "non_clinical", ["public_health_inspection"], ["city_environmental_health_officer"])],
  ["district_health_officer", cadreProfile("District Health Officer", "non_clinical", [], [], { status: "deprecated", mapsTo: "district_medical_officer", aliases: ["dho", "district_health_officer_legacy"] })],
  ["provincial_health_officer", cadreProfile("Provincial Health Officer", "non_clinical", [], [], { status: "deprecated", mapsTo: "provincial_medical_director", aliases: ["pho", "provincial_health_officer_legacy"] })],
];

const COMPREHENSIVE_CADRE_CODES = `
general_medical_practitioner junior_resident_medical_officer senior_resident_medical_officer medical_officer
district_medical_officer provincial_medical_director medical_superintendent clinical_director consultant_physician
specialist_physician general_surgeon specialist_surgeon obstetrician_gynaecologist paediatrician psychiatrist anaesthetist
radiologist pathologist public_health_physician epidemiologist dental_practitioner dental_therapist oral_health_technician
registered_general_nurse primary_care_nurse state_certified_nurse senior_nurse sister_in_charge sister_in_charge_community
ward_sister ward_nurse theatre_nurse theatre_nurse_in_charge maternity_nurse midwife psychiatric_nurse community_nurse
school_health_nurse infection_prevention_control_nurse nurse_manager district_nursing_officer provincial_nursing_officer
matron nurse_aide nursing_tutor nurse_mentor pharmacist chief_pharmacist hospital_pharmacist district_pharmacist
provincial_pharmacist pharmacy_technician dispensary_assistant medicines_store_manager medicines_control_officer
supply_chain_pharmacist medicines_logistics_officer laboratory_scientist medical_laboratory_scientist laboratory_technician
laboratory_assistant laboratory_supervisor district_laboratory_focal_person provincial_laboratory_focal_person
specimen_collector phlebotomist microbiology_scientist haematology_scientist chemistry_scientist pathology_scientist
radiographer senior_radiographer radiology_supervisor sonographer imaging_reviewer radiology_assistant radiation_safety_officer
physiotherapist occupational_therapist speech_therapist rehabilitation_technician dietician nutritionist psychologist
counsellor medical_social_worker social_worker mental_health_practitioner dental_assistant environmental_health_officer
environmental_health_technician public_health_inspector health_promotion_officer health_promotion_assistant disease_control_officer
surveillance_officer provincial_epidemiology_disease_control_officer district_epidemiology_disease_control_officer port_health_officer
food_safety_inspector water_sanitation_hygiene_officer vector_control_officer malaria_control_officer outbreak_response_officer
village_health_worker community_health_worker community_based_distributor peer_educator community_health_supervisor outreach_worker
mobile_clinic_worker home_based_care_worker health_information_officer district_health_information_officer
provincial_health_information_officer national_health_information_officer data_capture_clerk data_clerk records_clerk
records_supervisor registry_officer monitoring_and_evaluation_officer data_quality_officer digital_health_officer
ehr_support_officer telemedicine_support_officer ict_officer ict_support_officer systems_administrator helpdesk_agent
helpdesk_supervisor interoperability_officer integration_engineer data_analyst data_manager health_informatics_officer
facility_administrator hospital_administrator hospital_manager department_administrator finance_officer billing_officer
cashier claims_officer exemptions_officer procurement_officer stores_clerk warehouse_officer supply_chain_officer logistics_officer
transport_officer ambulance_driver driver maintenance_officer security_officer cleaning_supervisor food_services_supervisor
hospital_food_services_supervisor programme_manager national_programme_manager provincial_programme_manager district_programme_manager
programme_officer programme_m_and_e_officer district_programme_focal_person provincial_programme_focal_person national_programme_officer
mentor clinical_mentor nurse_mentor pharmacy_mentor laboratory_mentor trainer training_coordinator cpd_coordinator
quality_improvement_officer audit_officer compliance_officer emergency_medical_technician ambulance_technician emergency_care_provider
emergency_response_coordinator disaster_response_officer rapid_response_team_member marketplace_vendor_user marketplace_organisation_admin
supplier_representative manufacturer_representative distributor_representative insurer_representative payment_provider_representative
logistics_partner_user device_vendor_user iot_vendor_user software_integration_partner_user api_developer sandbox_tester
marketplace_compliance_officer regulatory_liaison_officer provider_registry_steward client_registry_steward facility_registry_steward
terminology_registry_steward product_service_registry_steward public_health_place_registry_steward geography_jurisdiction_steward
professional_council_regulator facility_licensing_regulator medicines_product_regulator laboratory_accreditation_regulator
training_cpd_accreditor public_health_regulator marketplace_certification_reviewer regulatory_auditor clinical_officer nurse_aide
`.trim().split(/\s+/);

function inferCadreClinical(code) {
  const nonClinical = [
    "clerk", "officer", "administrator", "manager", "coordinator", "driver", "supervisor", "agent",
    "representative", "developer", "tester", "analyst", "engineer", "auditor", "inspector", "regulator",
    "steward", "trainer", "mentor", "director", "focal_person",
  ];
  if (code.includes("doctor") || code.includes("nurse") || code.includes("midwife") || code.includes("clinical")) return "clinical";
  if (nonClinical.some((k) => code.includes(k)) && !code.includes("medical_officer") && !code.includes("health_worker")) return "non_clinical";
  return "clinical";
}

function inferCadreContexts(code) {
  if (code.includes("district_") || code.includes("provincial_") || code.includes("national_")) {
    return ["above_facility_management", "above_facility_support"];
  }
  if (code.includes("community") || code.includes("village") || code.includes("outreach") || code.includes("mobile_clinic")) {
    return ["community_outreach"];
  }
  if (code.includes("registry") || code.includes("regulator") || code.includes("marketplace")) {
    return ["registry_governance", "regulatory_oversight", "marketplace_operations"];
  }
  if (code.includes("programme")) return ["programme_support"];
  if (code.includes("laboratory") || code.includes("specimen") || code.includes("phlebotom")) return ["laboratory_services"];
  if (code.includes("pharmacy") || code.includes("medicines") || code.includes("dispensary")) return ["pharmacy_services"];
  if (code.includes("radiology") || code.includes("radiographer") || code.includes("imaging") || code.includes("sonographer")) {
    return ["radiology_imaging"];
  }
  if (code.includes("environmental") || code.includes("epidemiology") || code.includes("surveillance") || code.includes("public_health")) {
    return ["public_health_inspection"];
  }
  if (code.includes("records") || code.includes("information") || code.includes("data_")) return ["health_information_records"];
  if (code.includes("billing") || code.includes("cashier") || code.includes("claims") || code.includes("finance")) return ["billing_finance"];
  if (code.includes("ict") || code.includes("digital") || code.includes("helpdesk") || code.includes("integration")) return ["system_administration"];
  return ["facility_clinical"];
}

for (const code of COMPREHENSIVE_CADRE_CODES) {
  if (!CADRE_DEFINITIONS.some(([existing]) => existing === code)) {
    CADRE_DEFINITIONS.push([
      code,
      cadreProfile(titleCase(code), inferCadreClinical(code), inferCadreContexts(code), []),
    ]);
  }
}

export const ZW_CADRE_PROFILES = Object.freeze(Object.fromEntries(CADRE_DEFINITIONS));

export const ROLE_PERMISSION_BASELINES = Object.freeze({
  chief_pharmacist: [
    "professional.profile.view",
    "work.context.enter",
    "facility.dashboard.view",
    "pharmacy.prescriptions.view",
    "pharmacy.dispense",
    "pharmacy.stock.view",
    "pharmacy.stock.adjust",
    "pharmacy.stock.adjust.approve",
    "pharmacy.orders.create",
    "pharmacy.orders.approve",
    "pharmacy.staff.manage",
    "facility.reports.view",
  ],
  pharmacist: [
    "professional.profile.view",
    "work.context.enter",
    "pharmacy.prescriptions.view",
    "pharmacy.dispense",
    "pharmacy.stock.view",
    "pharmacy.orders.create",
  ],
  facility_administrator: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.staff.suspend_local_access",
    "facility.staff.end_assignment",
    "facility.department.view",
    "facility.department.manage",
    "facility.reports.view",
    "facility.audit.view",
    "facility.config.manage",
  ],
  records_clerk: [
    "work.context.enter",
    "facility.client.search",
    "facility.client.register_assisted",
    "facility.queue.manage",
    "professional.profile.view",
  ],
  telemedicine_provider: [
    "work.context.enter",
    "telemedicine.pool.join",
    "telemedicine.consult.accept",
    "telemedicine.encounter.document",
    "clinical.notes.write",
    "clinical.referrals.create",
  ],
  provider_registry_steward: [
    "registry.provider.view",
    "registry.provider.create_draft",
    "registry.provider.link_health_id",
    "audit.logs.view",
  ],
  marketplace_organisation_admin: [
    "marketplace.organisation.register",
    "marketplace.product.submit",
    "marketplace.contract.manage",
    "marketplace.api.request_sandbox",
    "marketplace.compliance.view",
  ],
  national_platform_administrator: [
    "system.config.manage",
    "system.roles.manage",
    "trust.context.inspect",
    "policy.bundle.view",
    "audit.logs.view",
  ],
  district_medical_officer: [
    "work.context.enter",
    "work.context.switch",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.reports.view",
    "regulator.inspect",
    "work.tasks.view",
    "audit.logs.view",
  ],
  provincial_medical_director: [
    "work.context.enter",
    "work.context.switch",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.reports.view",
    "regulator.review",
    "regulator.approve",
    "audit.logs.view",
  ],
  district_nursing_officer: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.reports.view",
    "clinical.notes.write",
    "audit.logs.view",
  ],
  provincial_nursing_officer: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.reports.view",
    "clinical.notes.write",
    "audit.logs.view",
  ],
  district_health_information_officer: [
    "work.context.enter",
    "facility.reports.view",
    "facility.audit.view",
    "audit.logs.view",
  ],
  provincial_health_information_officer: [
    "work.context.enter",
    "facility.reports.view",
    "facility.audit.view",
    "audit.logs.view",
  ],
  provincial_epidemiology_disease_control_officer: [
    "work.context.enter",
    "regulator.inspect",
    "facility.reports.view",
    "audit.logs.view",
  ],
  sister_in_charge: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "clinical.notes.write",
  ],
  sister_in_charge_community: [
    "work.context.enter",
    "facility.dashboard.view",
    "clinical.notes.write",
    "facility.reports.view",
  ],
  medical_superintendent: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "facility.reports.view",
    "facility.audit.view",
  ],
  matron: [
    "work.context.enter",
    "facility.dashboard.view",
    "facility.staff.view",
    "facility.staff.assign",
    "clinical.notes.write",
    "facility.reports.view",
  ],
  ...ORGANOGRAM_PERMISSION_BASELINES,
});

const PERMS = Object.freeze({
  clinicalCore: ["work.context.enter", "work.tasks.view", "clinical.encounter.create", "clinical.notes.write"],
  medicalExtended: ["work.context.enter", "clinical.encounter.create", "clinical.orders.create", "clinical.referrals.create", "clinical.results.review"],
  nursingCore: ["work.context.enter", "clinical.notes.write", "work.tasks.view"],
  pharmacyCore: ["work.context.enter", "pharmacy.prescriptions.view", "pharmacy.dispense"],
  labCore: ["work.context.enter", "lab.orders.view", "lab.specimen.collect", "lab.results.enter"],
  recordsCore: ["work.context.enter", "facility.client.search", "facility.queue.manage"],
  billingCore: ["work.context.enter", "facility.dashboard.view", "facility.reports.view"],
  communityCore: ["work.context.enter", "clinical.encounter.create", "facility.reports.view"],
  programmeCore: ["work.context.enter", "work.tasks.view", "facility.reports.view"],
  governanceCore: ["work.context.enter", "work.context.switch", "facility.reports.view", "audit.logs.view"],
  registryCore: ["registry.provider.view", "registry.facility.view", "audit.logs.view"],
  regulatorCore: ["regulator.review", "regulator.inspect", "audit.logs.view"],
  marketplaceCore: ["marketplace.organisation.register", "marketplace.product.submit", "marketplace.compliance.view"],
  systemCore: ["system.users.view", "system.roles.manage", "audit.logs.view"],
  emergencyCore: ["work.context.enter", "clinical.encounter.create", "facility.reports.view"],
});

const CORE_ZW_ROLE_TEMPLATE_SPECS = [
  spec("district_medical_officer", "above_site_management", ["above_facility_management", "above_facility_support"], ["above_site_dashboard"], ["district_medical_officer"], "district_medical_officer", "district"),
  spec("provincial_medical_director", "above_site_management", ["above_facility_management", "above_facility_support"], ["above_site_dashboard"], ["provincial_medical_director"], "provincial_medical_director", "province"),
  spec("district_nursing_officer", "above_site_management", ["above_facility_support", "facility_administration"], ["above_site_support"], ["district_nursing_officer"], "district_nursing_officer", "district"),
  spec("provincial_nursing_officer", "above_site_management", ["above_facility_support", "facility_administration"], ["above_site_support"], ["provincial_nursing_officer"], "provincial_nursing_officer", "province"),
  spec("district_health_information_officer", "above_site_management", ["above_facility_support", "health_information_records"], ["above_site_support"], ["district_health_information_officer"], "district_health_information_officer", "district"),
  spec("provincial_health_information_officer", "above_site_management", ["above_facility_support", "health_information_records"], ["above_site_support"], ["provincial_health_information_officer"], "provincial_health_information_officer", "province"),
  spec("provincial_epidemiology_disease_control_officer", "public_health", ["public_health_inspection", "above_facility_support"], ["above_site_support"], ["provincial_epidemiology_disease_control_officer"], "provincial_epidemiology_disease_control_officer", "province"),
  spec("sister_in_charge", "nursing", ["facility_clinical", "facility_administration"], ["inpatient", "facility_staff_management"], ["sister_in_charge"], "sister_in_charge", "facility"),
  spec("sister_in_charge_community", "community_outreach", ["community_outreach", "facility_administration"], ["community_outreach"], ["sister_in_charge_community"], "sister_in_charge_community", "community"),
  spec("medical_superintendent", "facility_management", ["facility_administration"], ["facility_admin"], ["medical_superintendent"], "medical_superintendent", "facility"),
  spec("matron", "nursing", ["facility_administration"], ["facility_staff_management"], ["matron"], "matron", "facility"),

  spec("general_medical_officer", "medical", ["facility_clinical", "virtual_care"], ["outpatient", "emergency"], ["general_medical_practitioner"], PERMS.medicalExtended, "facility"),
  spec("specialist_doctor", "medical", ["facility_clinical"], ["outpatient"], ["specialist_physician"], PERMS.medicalExtended, "facility"),
  spec("consultant_specialist", "medical", ["facility_clinical"], ["outpatient"], ["specialist_physician"], PERMS.medicalExtended, "facility"),
  spec("head_of_department_clinical", "medical", ["facility_clinical", "facility_administration"], ["outpatient", "facility_department_dashboard"], ["department_head"], ["work.context.enter", "clinical.encounter.create", "facility.department.manage"], "facility"),
  spec("admitting_doctor", "medical", ["facility_clinical"], ["inpatient"], ["general_medical_practitioner"], ["work.context.enter", "clinical.admissions.manage"], "facility"),
  spec("discharging_doctor", "medical", ["facility_clinical"], ["inpatient"], ["general_medical_practitioner"], ["work.context.enter", "clinical.discharge.manage"], "facility"),
  spec("emergency_clinician", "medical", ["facility_clinical", "emergency_response"], ["emergency"], ["emergency_medical_technician"], ["work.context.enter", "clinical.encounter.create"], "facility"),
  spec("telemedicine_provider", "telemedicine", ["virtual_care", "telemedicine_triage"], ["telemedicine_pool"], ["general_medical_practitioner"], "telemedicine_provider", "facility"),
  spec("telemedicine_specialist_reviewer", "telemedicine", ["virtual_care"], ["telemedicine_specialist_review"], ["specialist_physician"], ["work.context.enter", "telemedicine.specialist.review"], "facility"),
  spec("obgyn_specialist", "medical", ["facility_clinical"], ["maternity"], ["obstetrician_gynaecologist"], PERMS.medicalExtended, "facility"),
  spec("paediatrics_specialist", "medical", ["facility_clinical"], ["outpatient", "inpatient"], ["paediatrician"], PERMS.medicalExtended, "facility"),
  spec("mental_health_specialist", "medical", ["facility_clinical", "virtual_care"], ["mental_health"], ["psychiatrist"], PERMS.medicalExtended, "facility"),
  spec("anaesthesia_specialist", "medical", ["facility_clinical"], ["theatre"], ["anaesthetist"], ["work.context.enter", "clinical.encounter.create", "clinical.notes.write"], "facility"),
  spec("theatre_consultant", "medical", ["facility_clinical"], ["theatre"], ["specialist_surgeon"], PERMS.medicalExtended, "facility"),
  spec("dental_officer", "medical", ["facility_clinical"], ["dental"], ["dental_practitioner"], ["work.context.enter", "clinical.encounter.create", "clinical.notes.write"], "facility"),
  spec("clinical_officer", "medical", ["facility_clinical"], ["outpatient"], ["clinical_officer"], PERMS.clinicalCore, "facility"),

  spec("ward_nurse", "nursing", ["facility_clinical"], ["inpatient"], ["registered_general_nurse"], PERMS.nursingCore, "facility"),
  spec("nurse_in_charge", "nursing", ["facility_clinical"], ["inpatient"], ["nurse_manager"], ["work.context.enter", "clinical.notes.write", "facility.staff.view"], "facility"),
  spec("theatre_nurse", "nursing", ["facility_clinical"], ["theatre"], ["theatre_nurse"], PERMS.nursingCore, "facility"),
  spec("theatre_nurse_in_charge", "nursing", ["facility_clinical"], ["theatre"], ["theatre_nurse"], ["work.context.enter", "clinical.notes.write", "facility.staff.view"], "facility"),
  spec("maternity_nurse", "nursing", ["facility_clinical"], ["maternity"], ["midwife"], PERMS.nursingCore, "facility"),
  spec("midwife", "nursing", ["facility_clinical"], ["maternity", "antenatal_care"], ["midwife"], ["work.context.enter", "clinical.encounter.create", "clinical.notes.write"], "facility"),
  spec("triage_nurse", "nursing", ["facility_clinical"], ["emergency"], ["registered_general_nurse"], ["work.context.enter", "clinical.encounter.create", "clinical.notes.write"], "facility"),
  spec("virtual_triage_nurse", "telemedicine", ["telemedicine_triage"], ["telemedicine_triage"], ["registered_general_nurse"], ["work.context.enter", "telemedicine.triage.perform"], "facility"),
  spec("nursing_manager", "nursing", ["facility_administration"], ["facility_staff_management"], ["nurse_manager"], ["work.context.enter", "facility.staff.assign", "facility.staff.view"], "facility"),
  spec("department_nurse_manager", "nursing", ["facility_administration"], ["facility_staff_management"], ["nurse_manager"], ["work.context.enter", "facility.staff.assign", "facility.department.view"], "facility"),
  spec("ward_nurse_assistant", "nursing", ["facility_clinical"], ["inpatient"], ["nurse_aide"], PERMS.nursingCore, "facility"),
  spec("mental_health_nurse", "nursing", ["facility_clinical"], ["mental_health"], ["psychiatric_nurse"], PERMS.nursingCore, "facility"),
  spec("community_primary_care_nurse", "community_outreach", ["community_outreach"], ["community_outreach"], ["primary_care_nurse"], PERMS.communityCore, "community"),
  spec("community_midwife", "community_outreach", ["community_outreach"], ["community_outreach"], ["midwife"], PERMS.communityCore, "community"),

  spec("pharmacist", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["pharmacist"], "pharmacist", "facility"),
  spec("chief_pharmacist", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["chief_pharmacist"], "chief_pharmacist", "facility"),
  spec("dispensing_pharmacist", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["pharmacist"], PERMS.pharmacyCore, "facility"),
  spec("pharmacy_technician", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["pharmacy_technician"], PERMS.pharmacyCore, "facility"),
  spec("dispensary_assistant", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["dispensary_assistant"], PERMS.pharmacyCore, "facility"),
  spec("pharmacy_store_controller", "pharmacy", ["supply_chain_logistics"], ["stock_inventory"], ["pharmacy_technician"], ["work.context.enter", "pharmacy.stock.view", "pharmacy.stock.adjust"], "facility"),
  spec("medicines_store_manager", "pharmacy", ["supply_chain_logistics"], ["stock_inventory"], ["supply_chain_officer"], ["work.context.enter", "pharmacy.stock.adjust", "pharmacy.orders.create"], "facility"),
  spec("pharmacy_stock_approver", "pharmacy", ["pharmacy_services"], ["pharmacy"], ["chief_pharmacist"], ["work.context.enter", "pharmacy.stock.adjust.approve", "pharmacy.orders.approve"], "facility"),
  spec("district_pharmacy_officer", "pharmacy", ["above_facility_support"], ["above_site_support"], ["supply_chain_officer"], ["work.context.enter", "pharmacy.stock.view", "facility.reports.view"], "district"),
  spec("provincial_pharmacy_officer", "pharmacy", ["above_facility_support"], ["above_site_support"], ["chief_pharmacist"], ["work.context.enter", "pharmacy.stock.view", "facility.reports.view"], "province"),

  spec("laboratory_scientist", "laboratory", ["laboratory_services"], ["laboratory"], ["laboratory_scientist"], PERMS.labCore, "facility"),
  spec("laboratory_supervisor", "laboratory", ["laboratory_services"], ["laboratory"], ["laboratory_scientist"], ["work.context.enter", "lab.results.validate", "lab.results.release"], "facility"),
  spec("reference_lab_consultant", "laboratory", ["laboratory_services"], ["laboratory"], ["pathologist"], ["work.context.enter", "lab.results.validate", "lab.results.release"], "facility"),
  spec("specimen_collector", "laboratory", ["laboratory_services"], ["laboratory"], ["laboratory_technician"], ["work.context.enter", "lab.specimen.collect"], "facility"),
  spec("laboratory_technician", "laboratory", ["laboratory_services"], ["laboratory"], ["laboratory_technician"], PERMS.labCore, "facility"),
  spec("district_laboratory_officer", "laboratory", ["above_facility_support"], ["above_site_support"], ["laboratory_scientist"], ["work.context.enter", "lab.results.validate", "facility.reports.view"], "district"),
  spec("provincial_laboratory_officer", "laboratory", ["above_facility_support"], ["above_site_support"], ["laboratory_scientist"], ["work.context.enter", "lab.results.release", "facility.reports.view"], "province"),

  spec("radiographer", "radiology", ["radiology_imaging"], ["radiology"], ["radiographer"], ["work.context.enter", "clinical.orders.create", "clinical.results.review"], "facility"),
  spec("sonographer", "radiology", ["radiology_imaging"], ["radiology"], ["sonographer"], ["work.context.enter", "clinical.orders.create", "clinical.results.review"], "facility"),
  spec("radiology_supervisor", "radiology", ["radiology_imaging"], ["radiology"], ["radiographer"], ["work.context.enter", "clinical.results.review", "facility.reports.view"], "facility"),
  spec("imaging_reviewer", "radiology", ["radiology_imaging"], ["radiology"], ["radiologist"], ["work.context.enter", "clinical.results.review"], "facility"),

  spec("records_clerk", "records_information", ["health_information_records"], ["records"], ["records_clerk"], "records_clerk", "facility"),
  spec("ward_clerk", "records_information", ["health_information_records"], ["inpatient"], ["records_clerk"], PERMS.recordsCore, "facility"),
  spec("client_registration_clerk", "records_information", ["health_information_records"], ["client_registration"], ["records_clerk"], ["work.context.enter", "facility.client.register_assisted"], "facility"),
  spec("records_supervisor", "records_information", ["health_information_records"], ["records"], ["health_information_officer"], ["work.context.enter", "facility.queue.manage", "facility.reports.view"], "facility"),
  spec("health_information_officer", "records_information", ["health_information_records"], ["records"], ["health_information_officer"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "facility"),
  spec("facility_data_clerk", "records_information", ["health_information_records"], ["records"], ["data_capture_clerk"], ["work.context.enter", "facility.client.search"], "facility"),
  spec("community_data_clerk", "records_information", ["community_outreach"], ["community_outreach"], ["data_capture_clerk"], ["work.context.enter", "facility.reports.view"], "community"),

  spec("billing_officer", "billing_finance", ["billing_finance"], ["billing"], ["billing_officer"], PERMS.billingCore, "facility"),
  spec("cashier", "billing_finance", ["billing_finance"], ["billing"], ["cashier"], PERMS.billingCore, "facility"),
  spec("claims_officer", "billing_finance", ["billing_finance"], ["claims"], ["billing_officer"], PERMS.billingCore, "facility"),
  spec("exemptions_officer", "billing_finance", ["billing_finance"], ["billing"], ["billing_officer"], PERMS.billingCore, "facility"),
  spec("facility_finance_supervisor", "billing_finance", ["billing_finance"], ["billing"], ["billing_officer"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "facility"),

  spec("facility_administrator", "facility_management", ["facility_administration"], ["facility_admin"], ["facility_administrator"], "facility_administrator", "facility"),
  spec("facility_staff_manager", "facility_management", ["facility_administration"], ["facility_staff_management"], ["facility_administrator"], ["work.context.enter", "facility.staff.assign", "facility.staff.view"], "facility"),
  spec("hospital_manager", "facility_management", ["facility_administration"], ["facility_dashboard"], ["hospital_manager"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "facility"),
  spec("department_administrator", "facility_administration", ["facility_administration"], ["facility_admin"], ["department_administrator"], ["work.context.enter", "facility.department.manage"], "facility"),
  spec("facility_ict_support", "facility_administration", ["facility_operations"], ["facility_admin"], ["ict_officer"], ["work.context.enter", "facility.config.manage"], "facility"),
  spec("facility_quality_improvement_officer", "facility_administration", ["facility_operations"], ["facility_reports"], ["facility_administrator"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "facility"),
  spec("facility_audit_viewer", "audit_compliance", ["audit_oversight"], ["facility_reports"], ["auditor"], ["work.context.enter", "facility.audit.view"], "facility"),
  spec("unit_clinical_lead", "medical", ["facility_clinical"], ["outpatient", "inpatient"], ["department_head"], ["work.context.enter", "clinical.encounter.create", "facility.department.view"], "facility"),
  spec("unit_nursing_lead", "nursing", ["facility_clinical"], ["inpatient"], ["nurse_manager"], ["work.context.enter", "clinical.notes.write", "facility.staff.view"], "facility"),
  spec("unit_records_lead", "records_information", ["health_information_records"], ["records"], ["health_information_officer"], ["work.context.enter", "facility.reports.view"], "facility"),

  spec("community_health_worker", "community_outreach", ["community_outreach"], ["community_outreach"], ["community_health_worker"], PERMS.communityCore, "community"),
  spec("outreach_nurse", "community_outreach", ["community_outreach"], ["community_outreach"], ["primary_care_nurse"], PERMS.communityCore, "community"),
  spec("mobile_clinic_team_member", "community_outreach", ["mobile_clinic"], ["mobile_clinic"], ["community_health_worker"], PERMS.communityCore, "community"),
  spec("outreach_team_lead", "community_outreach", ["community_outreach"], ["community_outreach"], ["supervisor"], ["work.context.enter", "facility.staff.view", "facility.reports.view"], "community"),
  spec("community_supervisor", "community_outreach", ["community_outreach"], ["community_outreach"], ["supervisor"], ["work.context.enter", "facility.staff.assign", "facility.reports.view"], "community"),
  spec("environmental_health_officer", "public_health", ["public_health_inspection"], ["public_health_inspection"], ["environmental_health_practitioner"], ["work.context.enter", "regulator.inspect"], "community"),
  spec("public_health_inspector", "public_health", ["public_health_inspection"], ["public_health_inspection"], ["inspector"], ["work.context.enter", "regulator.inspect"], "district"),
  spec("village_health_worker", "community_outreach", ["community_outreach"], ["community_outreach"], ["community_health_worker"], PERMS.communityCore, "community"),
  spec("village_health_committee_chair", "community_outreach", ["community_outreach"], ["community_outreach"], ["supervisor"], ["work.context.enter", "facility.reports.view"], "community"),
  spec("community_surveillance_officer", "public_health", ["community_outreach", "public_health_inspection"], ["community_outreach"], ["environmental_health_practitioner"], ["work.context.enter", "facility.reports.view", "regulator.inspect"], "community"),
  spec("community_nutrition_officer", "community_outreach", ["community_outreach"], ["community_outreach"], ["nutritionist"], PERMS.communityCore, "community"),
  spec("community_social_worker", "community_outreach", ["community_outreach"], ["community_outreach"], ["social_worker"], PERMS.communityCore, "community"),
  spec("hiv_counsellor", "community_outreach", ["community_outreach", "facility_clinical"], ["community_outreach", "outpatient"], ["counsellor"], ["work.context.enter", "clinical.notes.write"], "community"),
  spec("catchment_surveillance_officer", "public_health", ["community_outreach", "public_health_inspection"], ["programme_dashboard"], ["environmental_health_practitioner"], ["work.context.enter", "facility.reports.view", "regulator.inspect"], "community"),
  spec("catchment_outreach_coordinator", "community_outreach", ["community_outreach"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view"], "community"),

  spec("programme_officer", "programme_management", ["programme_support"], ["programme_support"], ["programme_manager"], PERMS.programmeCore, "district"),
  spec("programme_manager", "programme_management", ["programme_support"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "work.tasks.view"], "province"),
  spec("district_programme_coordinator", "programme_management", ["programme_support", "above_facility_support"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "work.tasks.view"], "district"),
  spec("provincial_programme_coordinator", "programme_management", ["programme_support", "above_facility_support"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "work.tasks.view"], "province"),
  spec("national_programme_director", "programme_management", ["programme_support", "above_facility_management"], ["programme_dashboard"], ["national_programme_officer"], ["work.context.enter", "facility.reports.view", "system.config.manage", "audit.logs.view"], "national"),
  spec("national_programme_administrator", "programme_management", ["programme_support"], ["programme_dashboard"], ["national_programme_officer"], ["work.context.enter", "system.config.manage", "facility.reports.view"], "national"),
  spec("programme_m_and_e_officer", "programme_management", ["programme_support"], ["programme_support"], ["monitoring_and_evaluation_officer"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "district"),
  spec("district_monitoring_and_evaluation_officer", "programme_management", ["programme_support", "above_facility_support"], ["programme_support"], ["monitoring_and_evaluation_officer"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "district"),
  spec("provincial_monitoring_and_evaluation_officer", "programme_management", ["programme_support", "above_facility_support"], ["programme_support"], ["monitoring_and_evaluation_officer"], ["work.context.enter", "facility.reports.view", "facility.audit.view"], "province"),
  spec("national_monitoring_and_evaluation_officer", "programme_management", ["programme_support", "above_facility_support"], ["programme_support"], ["monitoring_and_evaluation_officer"], ["work.context.enter", "facility.reports.view", "audit.logs.view"], "national"),

  spec("mentor_supervisor", "training_mentorship", ["training_mentorship"], ["mentorship_supervision"], ["mentor"], ["work.context.enter", "professional.fundo.access"], "district"),
  spec("district_mentor", "training_mentorship", ["training_mentorship"], ["mentorship_supervision"], ["mentor"], ["work.context.enter", "professional.fundo.access"], "district"),
  spec("provincial_mentor", "training_mentorship", ["training_mentorship"], ["mentorship_supervision"], ["mentor"], ["work.context.enter", "professional.fundo.access"], "province"),
  spec("fundo_learner", "training_mentorship", ["training_mentorship"], ["fundo_professional_learning"], [], ["professional.fundo.access"], "facility"),
  spec("fundo_trainer", "training_mentorship", ["training_mentorship"], ["training_delivery"], ["trainer"], ["work.context.enter", "professional.fundo.access"], "province"),
  spec("fundo_course_author", "training_mentorship", ["training_mentorship"], ["training_delivery"], ["trainer"], ["work.context.enter", "professional.fundo.access"], "national"),
  spec("fundo_assessor", "training_mentorship", ["training_mentorship"], ["training_delivery"], ["trainer"], ["work.context.enter", "professional.fundo.access"], "national"),
  spec("fundo_training_administrator", "training_mentorship", ["training_mentorship"], ["training_delivery"], ["trainer"], ["work.context.enter", "system.config.manage", "professional.fundo.access"], "national"),

  spec("provider_registry_owner", "registry_governance", ["registry_governance"], ["provider_registry_admin"], [], ["registry.provider.approve", "audit.logs.view"], "registry"),
  spec("provider_registry_steward", "registry_governance", ["registry_governance"], ["provider_registry_admin"], ["registry_officer"], "provider_registry_steward", "registry"),
  spec("provider_registry_approver", "registry_governance", ["registry_governance"], ["provider_registry_admin"], [], ["registry.provider.approve", "audit.logs.view"], "registry"),
  spec("provider_registry_data_quality_officer", "registry_governance", ["registry_governance"], ["provider_registry_admin"], [], ["registry.provider.view", "audit.logs.view"], "registry"),
  spec("client_registry_owner", "registry_governance", ["registry_governance"], ["client_registry_admin"], [], ["registry.client.resolve_duplicate", "audit.logs.view"], "registry"),
  spec("client_registry_steward", "registry_governance", ["registry_governance"], ["client_registry_admin"], [], ["registry.client.resolve_duplicate", "audit.logs.view"], "registry"),
  spec("client_registry_resolution_officer", "registry_governance", ["registry_governance"], ["client_registry_admin"], ["registry_officer"], ["registry.client.resolve_duplicate", "audit.logs.view"], "registry"),
  spec("facility_registry_owner", "registry_governance", ["registry_governance"], ["facility_registry_admin"], [], ["registry.facility.approve", "audit.logs.view"], "registry"),
  spec("facility_registry_steward", "registry_governance", ["registry_governance"], ["facility_registry_admin"], [], ["registry.facility.update", "audit.logs.view"], "registry"),
  spec("facility_registry_approver", "registry_governance", ["registry_governance"], ["facility_registry_admin"], [], ["registry.facility.approve", "audit.logs.view"], "registry"),
  spec("terminology_registry_owner", "registry_governance", ["registry_governance"], ["terminology_registry_admin"], [], ["registry.terminology.manage", "audit.logs.view"], "registry"),
  spec("terminology_registry_steward", "registry_governance", ["registry_governance"], ["terminology_registry_admin"], [], ["registry.terminology.manage", "audit.logs.view"], "registry"),
  spec("product_service_registry_owner", "registry_governance", ["registry_governance"], ["product_service_registry_admin"], [], ["registry.product_service.manage", "audit.logs.view"], "registry"),
  spec("product_service_registry_steward", "registry_governance", ["registry_governance"], ["product_service_registry_admin"], [], ["registry.product_service.manage", "audit.logs.view"], "registry"),
  spec("public_health_place_registry_steward", "registry_governance", ["registry_governance"], ["public_health_place_registry_admin"], [], ["registry.public_health_place.manage", "audit.logs.view"], "registry"),
  spec("geography_jurisdiction_steward", "registry_governance", ["registry_governance"], ["geography_jurisdiction_admin"], [], ["registry.geography.manage", "audit.logs.view"], "registry"),

  spec("professional_council_regulator", "regulatory_oversight", ["regulatory_oversight"], ["professional_council_review"], [], ["regulator.review", "regulator.approve", "audit.logs.view"], "national"),
  spec("facility_licensing_regulator", "regulatory_oversight", ["regulatory_oversight"], ["facility_licensing_review"], [], ["regulator.review", "regulator.certify", "audit.logs.view"], "national"),
  spec("medicines_products_regulator", "regulatory_oversight", ["regulatory_oversight"], ["medicines_product_review"], [], ["regulator.review", "regulator.approve", "audit.logs.view"], "national"),
  spec("laboratory_accreditation_regulator", "regulatory_oversight", ["regulatory_oversight"], ["laboratory_accreditation_review"], [], ["regulator.review", "regulator.certify", "audit.logs.view"], "national"),
  spec("training_cpd_accreditor", "regulatory_oversight", ["regulatory_oversight"], ["training_cpd_accreditation"], [], ["regulator.review", "regulator.renew", "audit.logs.view"], "national"),
  spec("public_health_regulator", "regulatory_oversight", ["regulatory_oversight"], ["public_health_inspection_review"], [], ["regulator.inspect", "regulator.approve", "audit.logs.view"], "national"),
  spec("marketplace_certification_reviewer", "regulatory_oversight", ["regulatory_oversight"], ["marketplace_certification_review"], [], ["regulator.review", "marketplace.product.review", "audit.logs.view"], "national"),
  spec("compliance_inspector", "regulatory_oversight", ["regulatory_oversight"], ["public_health_inspection_review"], ["inspector"], ["regulator.inspect", "audit.logs.view"], "province"),
  spec("regulatory_auditor", "audit_compliance", ["audit_oversight"], ["audit_console"], ["auditor"], ["regulator.audit", "audit.logs.view"], "national"),

  spec("marketplace_organisation_admin", "marketplace_operations", ["marketplace_operations"], ["marketplace_vendor_portal"], [], "marketplace_organisation_admin", "marketplace"),
  spec("marketplace_vendor_user", "marketplace_operations", ["marketplace_operations"], ["marketplace_vendor_portal"], [], PERMS.marketplaceCore, "marketplace"),
  spec("marketplace_product_manager", "marketplace_operations", ["marketplace_operations"], ["marketplace_product_catalogue"], [], ["marketplace.product.submit", "marketplace.compliance.view"], "marketplace"),
  spec("marketplace_contract_manager", "marketplace_operations", ["marketplace_operations"], ["marketplace_contract_management"], [], ["marketplace.contract.manage", "marketplace.compliance.view"], "marketplace"),
  spec("marketplace_api_developer", "marketplace_operations", ["marketplace_operations"], ["marketplace_api_management"], [], ["marketplace.api.request_sandbox"], "marketplace"),
  spec("marketplace_sandbox_tester", "marketplace_operations", ["marketplace_operations"], ["marketplace_sandbox_certification"], [], ["marketplace.api.request_sandbox"], "marketplace"),
  spec("marketplace_compliance_officer", "marketplace_operations", ["marketplace_operations"], ["marketplace_compliance_monitoring"], [], ["marketplace.compliance.view", "marketplace.compliance.audit"], "marketplace"),
  spec("marketplace_support_user", "marketplace_operations", ["marketplace_operations"], ["marketplace_vendor_portal"], [], ["marketplace.compliance.view"], "marketplace"),
  spec("marketplace_finance_user", "marketplace_operations", ["marketplace_operations"], ["marketplace_contract_management"], [], ["marketplace.contract.manage"], "marketplace"),
  spec("marketplace_regulatory_liaison", "marketplace_operations", ["marketplace_operations"], ["marketplace_regulator_review"], [], ["marketplace.compliance.view"], "marketplace"),

  spec("national_platform_administrator", "system_administration", ["system_administration"], ["system_admin_console"], ["system_support_officer"], "national_platform_administrator", "national"),
  spec("provincial_platform_administrator", "system_administration", ["system_administration"], ["system_admin_console"], ["system_support_officer"], ["system.config.manage", "system.users.view", "audit.logs.view"], "province"),
  spec("district_platform_administrator", "system_administration", ["system_administration"], ["system_admin_console"], ["system_support_officer"], ["system.config.manage", "system.users.view", "audit.logs.view"], "district"),
  spec("facility_system_administrator", "system_administration", ["system_administration"], ["system_admin_console"], ["ict_officer"], ["facility.config.manage", "system.users.view", "audit.logs.view"], "facility"),
  spec("security_administrator", "security_trust", ["system_administration"], ["trust_console"], [], ["security.sessions.view", "security.mfa.manage", "audit.logs.view"], "national"),
  spec("trust_administrator", "security_trust", ["system_administration"], ["trust_console"], [], ["trust.context.inspect", "audit.logs.view"], "national"),
  spec("policy_administrator", "security_trust", ["system_administration"], ["policy_console"], [], ["policy.bundle.view", "policy.bundle.publish", "audit.logs.view"], "national"),
  spec("opa_policy_publisher", "security_trust", ["system_administration"], ["policy_console"], [], ["policy.bundle.publish", "audit.logs.view"], "national"),
  spec("audit_administrator", "audit_compliance", ["audit_oversight"], ["audit_console"], ["auditor"], ["audit.logs.view"], "national"),
  spec("integration_administrator", "integration_support", ["integration_partner_operations"], ["integration_console"], [], ["integration.api_keys.manage", "audit.logs.view"], "national"),
  spec("api_gateway_administrator", "integration_support", ["system_administration"], ["integration_console"], [], ["integration.api_keys.manage", "audit.logs.view"], "national"),
  spec("keycloak_administrator", "system_administration", ["system_administration"], ["system_admin_console"], [], ["system.users.view", "audit.logs.view"], "national"),
  spec("tshepo_administrator", "security_trust", ["system_administration"], ["trust_console"], [], ["trust.context.inspect", "audit.logs.view"], "national"),
  spec("nompilo_administrator", "nompilo_operations", ["system_administration"], ["system_admin_console"], [], ["nompilo.knowledge.manage", "audit.logs.view"], "national"),
  spec("nompilo_knowledge_manager", "nompilo_operations", ["system_administration"], ["system_admin_console"], [], ["nompilo.knowledge.manage", "audit.logs.view"], "national"),
  spec("helpdesk_supervisor", "system_administration", ["system_administration"], ["system_admin_console"], ["helpdesk_agent"], ["system.users.view", "audit.logs.view"], "national"),
  spec("support_agent", "system_administration", ["system_administration"], ["system_admin_console"], ["system_support_officer"], ["system.users.view"], "national"),
  spec("national_helpdesk_agent", "system_administration", ["system_administration"], ["system_admin_console"], ["helpdesk_agent"], ["system.users.view"], "national"),
  spec("national_platform_support", "system_administration", ["system_administration"], ["system_admin_console"], ["system_support_officer"], ["system.users.view"], "national"),

  spec("emergency_response_provider", "emergency_response", ["emergency_response"], ["emergency_response"], ["emergency_medical_technician"], PERMS.emergencyCore, "facility"),
  spec("emergency_response_coordinator", "emergency_response", ["emergency_response"], ["emergency_response"], ["supervisor"], ["work.context.enter", "facility.staff.view", "facility.reports.view"], "district"),
  spec("ambulance_technician", "emergency_response", ["emergency_response"], ["emergency_response"], ["ambulance_technician"], PERMS.emergencyCore, "facility"),
  spec("district_emergency_preparedness_officer", "emergency_response", ["emergency_response", "above_facility_support"], ["emergency_response"], ["programme_manager"], ["work.context.enter", "facility.reports.view"], "district"),
  spec("provincial_emergency_preparedness_officer", "emergency_response", ["emergency_response", "above_facility_support"], ["emergency_response"], ["programme_manager"], ["work.context.enter", "facility.reports.view"], "province"),
  spec("national_emergency_preparedness_director", "emergency_response", ["emergency_response", "above_facility_management"], ["emergency_response"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "audit.logs.view"], "national"),
  spec("break_glass_authorizer", "security_trust", ["system_administration"], ["trust_console"], [], ["break_glass.authorize", "audit.logs.view"], "national"),
  spec("break_glass_auditor", "audit_compliance", ["audit_oversight"], ["audit_console"], [], ["break_glass.audit", "audit.logs.view"], "national"),
];

const NATIONAL_LEADERSHIP_CODES = [
  "minister_of_health_and_child_care",
  "deputy_minister_of_health_and_child_care",
  "permanent_secretary_health",
  "chief_director_health_services",
  "national_medical_director",
  "national_nursing_director",
  "national_health_information_director",
  "national_public_health_director",
  "national_pharmacy_director",
  "national_laboratory_director",
  "national_finance_director",
  "national_hr_director",
  "national_logistics_director",
  "national_digital_health_director",
  "national_quality_assurance_director",
  "national_training_director",
  "national_surveillance_director",
  "national_disease_control_director",
];

const PROVINCIAL_SUPPORT_CODES = [
  "provincial_support_officer",
  "provincial_public_health_officer",
  "provincial_surveillance_officer",
  "provincial_disease_control_officer",
  "provincial_finance_officer",
  "provincial_hr_officer",
  "provincial_logistics_officer",
  "provincial_quality_assurance_officer",
  "provincial_digital_health_officer",
  "provincial_training_officer",
  "provincial_reproductive_health_officer",
  "provincial_immunisation_officer",
];

const DISTRICT_SUPPORT_CODES = [
  "district_support_officer",
  "district_public_health_officer",
  "district_surveillance_officer",
  "district_disease_control_officer",
  "district_finance_officer",
  "district_hr_officer",
  "district_logistics_officer",
  "district_quality_assurance_officer",
  "district_digital_health_officer",
  "district_training_officer",
  "district_reproductive_health_officer",
  "district_immunisation_officer",
  "district_vector_control_officer",
  "district_health_promotion_officer",
];

const CITY_LOCAL_AUTHORITY_CODES = [
  "city_health_director",
  "city_health_information_officer",
  "city_environmental_health_officer",
  "city_clinical_services_manager",
  "city_primary_healthcare_coordinator",
  "city_disease_surveillance_officer",
];

const FUNDO_AND_PARTNER_CODES = [
  "partner_programme_manager",
  "partner_data_officer",
  "partner_outreach_coordinator",
  "partner_supply_chain_officer",
  "partner_monitoring_and_evaluation_officer",
  "partner_quality_assurance_officer",
];

const PROGRAMME_DOMAINS = [
  "hiv_aids",
  "tb_leprosy",
  "malaria",
  "maternal_newborn_child_health",
  "sexual_reproductive_health",
  "adolescent_health",
  "immunisation",
  "nutrition",
  "mental_health",
  "non_communicable_disease",
  "epidemic_preparedness",
  "school_health",
  "disability_rehabilitation",
  "water_sanitation_hygiene",
  "blood_safety",
];

const GENERATED_ZW_ROLE_TEMPLATE_SPECS = [
  ...NATIONAL_LEADERSHIP_CODES.map((code) =>
    spec(code, "governance", ["above_facility_management"], ["above_site_dashboard"], [], PERMS.governanceCore, "national"),
  ),
  ...PROVINCIAL_SUPPORT_CODES.map((code) =>
    spec(code, "above_site_management", ["above_facility_support"], ["above_site_support"], [], PERMS.governanceCore, "province"),
  ),
  ...DISTRICT_SUPPORT_CODES.map((code) =>
    spec(code, "above_site_management", ["above_facility_support"], ["above_site_support"], [], PERMS.governanceCore, "district"),
  ),
  ...CITY_LOCAL_AUTHORITY_CODES.map((code) =>
    spec(code, "public_health", ["above_facility_support", "public_health_inspection"], ["public_health_inspection"], [], ["work.context.enter", "facility.reports.view", "regulator.inspect"], "district"),
  ),
  ...FUNDO_AND_PARTNER_CODES.map((code) =>
    spec(code, "programme_management", ["programme_support", "integration_partner_operations"], ["programme_support", "integration_console"], [], PERMS.programmeCore, "national"),
  ),

  ...PROGRAMME_DOMAINS.map((domain) =>
    spec(`national_${domain}_programme_manager`, "programme_management", ["programme_support"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "work.tasks.view"], "national"),
  ),
  ...PROGRAMME_DOMAINS.map((domain) =>
    spec(`provincial_${domain}_programme_coordinator`, "programme_management", ["programme_support"], ["programme_dashboard"], ["programme_manager"], ["work.context.enter", "facility.reports.view", "work.tasks.view"], "province"),
  ),
  ...PROGRAMME_DOMAINS.map((domain) =>
    spec(`district_${domain}_programme_officer`, "programme_management", ["programme_support"], ["programme_support"], ["programme_manager"], PERMS.programmeCore, "district"),
  ),
  ...PROGRAMME_DOMAINS.map((domain) =>
    spec(`community_${domain}_programme_focal_person`, "programme_management", ["community_outreach", "programme_support"], ["community_outreach", "programme_support"], [], PERMS.programmeCore, "community"),
  ),

  ...[
    "provider_registry_audit_officer",
    "provider_registry_reconciliation_officer",
    "client_registry_audit_officer",
    "client_registry_data_quality_officer",
    "facility_registry_data_quality_officer",
    "terminology_registry_reviewer",
    "product_service_registry_reviewer",
    "geography_registry_reviewer",
    "public_health_place_registry_reviewer",
  ].map((code) =>
    spec(code, "registry_governance", ["registry_governance"], ["provider_registry_admin", "client_registry_admin", "facility_registry_admin"], [], PERMS.registryCore, "registry"),
  ),

  ...[
    "provincial_professional_council_regulator",
    "provincial_facility_licensing_regulator",
    "provincial_medicines_products_regulator",
    "provincial_public_health_regulator",
    "district_public_health_regulator",
    "district_compliance_inspector",
    "district_regulatory_auditor",
  ].map((code) =>
    spec(code, "regulatory_oversight", ["regulatory_oversight", "public_health_inspection"], ["public_health_inspection_review"], [], PERMS.regulatorCore, code.startsWith("district_") ? "district" : "province"),
  ),

  ...[
    "marketplace_vendor_onboarding_officer",
    "marketplace_vendor_kyc_officer",
    "marketplace_product_qa_officer",
    "marketplace_contract_reviewer",
    "marketplace_api_compliance_officer",
    "marketplace_dispute_resolution_officer",
    "marketplace_revenue_analyst",
    "marketplace_partner_success_officer",
    "marketplace_security_compliance_officer",
    "marketplace_catalogue_curator",
    "marketplace_certification_coordinator",
  ].map((code) =>
    spec(code, "marketplace_operations", ["marketplace_operations"], ["marketplace_vendor_portal", "marketplace_compliance_monitoring"], [], PERMS.marketplaceCore, "marketplace"),
  ),

  ...[
    "district_system_administrator",
    "district_identity_support_officer",
    "district_security_support_officer",
    "provincial_system_support_officer",
    "provincial_identity_support_officer",
    "national_identity_assurance_officer",
    "national_policy_support_officer",
    "national_audit_operations_officer",
    "national_integration_support_officer",
    "national_service_desk_manager",
    "national_incident_response_manager",
  ].map((code) =>
    spec(code, "system_administration", ["system_administration"], ["system_admin_console", "trust_console", "integration_console"], [], PERMS.systemCore, code.startsWith("district_") ? "district" : code.startsWith("provincial_") ? "province" : "national"),
  ),

  ...[
    "district_rapid_response_team_lead",
    "district_emergency_operations_officer",
    "district_ambulance_dispatch_coordinator",
    "provincial_rapid_response_team_lead",
    "provincial_emergency_operations_officer",
    "national_rapid_response_coordinator",
    "national_emergency_operations_manager",
    "public_health_emergency_intelligence_officer",
  ].map((code) =>
    spec(code, "emergency_response", ["emergency_response", "public_health_inspection"], ["emergency_response", "public_health_inspection"], [], PERMS.emergencyCore, code.startsWith("district_") ? "district" : code.startsWith("provincial_") ? "province" : "national"),
  ),
];

export const ZW_ROLE_TEMPLATE_SPECS = ensureUniqueRoleCodes([
  ...CORE_ZW_ROLE_TEMPLATE_SPECS.map((s) => applyOrganogramContextMapping(s[0], s)),
  ...buildOrganogramRoleSpecs(spec, new Set(CORE_ZW_ROLE_TEMPLATE_SPECS.map((s) => s[0]))),
]);

const ROLE_PROFILE_OVERRIDES = {
  district_medical_officer: {
    displayName: "District Medical Officer",
    abbreviation: "DMO",
    aliases: ["district_health_executive_member", "district_health_officer_legacy"],
    primaryZimbabweRole: true,
    policyScopeLevel: "district",
  },
  provincial_medical_director: {
    displayName: "Provincial Medical Director",
    abbreviation: "PMD",
    aliases: ["provincial_health_officer_legacy"],
    primaryZimbabweRole: true,
    policyScopeLevel: "province",
  },
  district_nursing_officer: {
    displayName: "District Nursing Officer",
    abbreviation: "DNO",
    primaryZimbabweRole: true,
    policyScopeLevel: "district",
  },
  provincial_nursing_officer: {
    displayName: "Provincial Nursing Officer",
    abbreviation: "PNO",
    primaryZimbabweRole: true,
    policyScopeLevel: "province",
  },
  district_health_information_officer: {
    displayName: "District Health Information Officer",
    abbreviation: "DHIO",
    primaryZimbabweRole: true,
    policyScopeLevel: "district",
  },
  provincial_health_information_officer: {
    displayName: "Provincial Health Information Officer",
    abbreviation: "PHIO",
    primaryZimbabweRole: true,
    policyScopeLevel: "province",
  },
  provincial_epidemiology_disease_control_officer: {
    displayName: "Provincial Epidemiology and Disease Control Officer",
    abbreviation: "PEDCO",
    primaryZimbabweRole: true,
    policyScopeLevel: "province",
  },
  sister_in_charge: {
    displayName: "Sister In Charge",
    abbreviation: "SIC",
    primaryZimbabweRole: true,
    policyScopeLevel: "facility",
  },
  sister_in_charge_community: {
    displayName: "Sister In Charge (Community)",
    abbreviation: "SICC",
    primaryZimbabweRole: true,
    policyScopeLevel: "community",
  },
  medical_superintendent: {
    displayName: "Medical Superintendent",
    abbreviation: "MS",
    primaryZimbabweRole: true,
    policyScopeLevel: "facility",
  },
  matron: {
    displayName: "Matron",
    abbreviation: "Matron",
    primaryZimbabweRole: true,
    policyScopeLevel: "facility",
  },
  ...ORGANOGRAM_ROLE_PROFILES,
};

export const ZW_ROLE_PROFILES = Object.freeze(
  Object.fromEntries(
    ZW_ROLE_TEMPLATE_SPECS.map(([code, , , , , , scopeLevel]) => {
      const base = {
        displayName: titleCase(code),
        ...roleScope(scopeLevel),
      };
      return [code, { ...base, ...(ROLE_PROFILE_OVERRIDES[code] ?? {}) }];
    }),
  ),
);

export const DEPRECATED_ROLE_TEMPLATES = [];

export default {
  CATALOGUE_DATA_VERSION,
  VERSION_BUMP_NOTE,
  ZW_ADMIN_LEVELS,
  ZW_ORG_STRUCTURES,
  ZW_CADRE_PROFILES,
  ZW_ROLE_PROFILES,
  ZW_ROLE_TEMPLATE_SPECS,
  DEPRECATED_ROLE_TEMPLATES,
  ROLE_PERMISSION_BASELINES,
};
