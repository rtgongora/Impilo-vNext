/**
 * Generates canonical registry template stubs under registry-templates/.
 * Run from repo root: node scripts/registry/bootstrap-registry-templates.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..", "..");
const base = path.join(root, "registry-templates");

const field = (name, label, rest = {}) => ({
  name,
  label,
  description: rest.description ?? "",
  dataType: rest.dataType ?? "string",
  requirement: rest.requirement ?? "optional",
  conditionalWhen: rest.conditionalWhen ?? null,
  validation: rest.validation ?? {},
  valueSetRef: rest.valueSetRef ?? null,
  parentField: rest.parentField ?? null,
  workflowApplicability: rest.workflowApplicability ?? ["*"],
  privacyClass: rest.privacyClass ?? "PII",
  pii: rest.pii !== false,
  auditProvenance: rest.auditProvenance !== false,
  offline: rest.offline ?? "cache_lists",
  sync: rest.sync ?? "queue_reconcile",
  tshepoImpact: rest.tshepoImpact ?? "purpose_of_use_and_consent_evaluated",
  fhirConceptual: rest.fhirConceptual ?? [],
  sourceAuthority: rest.sourceAuthority ?? "Impilo Registry Doctrine",
  version: rest.version ?? "1.0.0",
  active: rest.active !== false,
});

const templateMeta = (id, owner, title, fhir = []) => ({
  id,
  owner,
  title,
  fhirConceptualMappings: fhir,
  templateVersion: "1.0.0",
  active: true,
});

function write(rel, obj) {
  const p = path.join(base, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, JSON.stringify(obj, null, 2), "utf8");
  console.log("wrote", rel);
}

fs.mkdirSync(path.join(base, "terminology", "data"), { recursive: true });

write("terminology/zimbabwe-provinces.seed.json", {
  sourceAuthority:
    "Authoritative Admin-1 boundaries must be verified against Zimbabwe COD-AB / ZIMSTAT before production cut-over.",
  version: "1.0.0",
  notes:
    "Stable internal codes are Impilo placeholders until COD-AB PCODEs are imported. Province NAMES match the 10 official provinces.",
  provinces: [
    { code: "ZW-ADM1-BU", name: "Bulawayo" },
    { code: "ZW-ADM1-HA", name: "Harare" },
    { code: "ZW-ADM1-MA", name: "Manicaland" },
    { code: "ZW-ADM1-MC", name: "Mashonaland Central" },
    { code: "ZW-ADM1-ME", name: "Mashonaland East" },
    { code: "ZW-ADM1-MW", name: "Mashonaland West" },
    { code: "ZW-ADM1-MV", name: "Masvingo" },
    { code: "ZW-ADM1-MN", name: "Matabeleland North" },
    { code: "ZW-ADM1-MS", name: "Matabeleland South" },
    { code: "ZW-ADM1-MI", name: "Midlands" },
  ],
});

write("terminology/data/zimbabwe-districts.seed.json", {
  _comment:
    "INTENTIONALLY EMPTY: districts (Admin-2) must be seeded from COD-AB / ZIMSTAT — do not invent district polygons or names here.",
  districts: [],
});

write("terminology/data/zimbabwe-wards.seed.json", {
  _comment:
    "INTENTIONALLY EMPTY: wards (Admin-3, ~1961 per COD-AB) must be bulk-imported from an authoritative boundary dataset.",
  wards: [],
});

write("terminology/SOURCE-ZIM-ADMIN.md", `# Zimbabwe administrative seeding

## Doctrine

- **Admin 1–3** (province, district, ward): seed from **COD-AB** and/or **ZIMSTAT** census boundary products — **never** hand-invent ward lists.
- **Locality / village / suburb / growth point**: governed by **Tuso Locality Gazetteer** APIs — UI loads suggestions from Tuso, not static JSON in the web bundle.

## Files

| File | Status |
|------|--------|
| \`zimbabwe-provinces.seed.json\` | Ten provinces — **verify PCODEs** against COD-AB before production. |
| \`data/zimbabwe-districts.seed.json\` | Empty until import pipeline runs. |
| \`data/zimbabwe-wards.seed.json\` | Empty until import pipeline runs. |

## Zimbabwe ISO country row

Canonical: **Zimbabwe** — alpha-2 \`ZW\`, alpha-3 \`ZWE\`, numeric **716** (see \`countries.iso3166-1.json\`).
`);

write("client/client-core.schema.json", {
  ...templateMeta(
    "impilo.registry.client.core.v1",
    "VITO",
    "Client core registration",
    ["Patient", "RelatedPerson", "Consent", "Coverage", "Provenance", "AuditEvent"],
  ),
  sections: [
    {
      id: "registry_identifiers",
      title: "Registry identifiers",
      fields: [
        field("vitoRegistryHealthId", "Vito registry Health ID", {
          dataType: "uuid_string",
          requirement: "conditional",
          description: "Issued by Vito after successful registration.",
          fhirConceptual: ["Patient.identifier"],
        }),
        field("impiloIdAlias", "Impilo ID alias", { fhirConceptual: ["Patient.identifier"] }),
        field("healthIdLinkage", "Health ID linkage state", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/linkageStatus" }),
        field("cridCpidLinkageStatus", "CRID/CPID linkage status", {}),
        field("temporaryProvisionalFlag", "Temporary / provisional", { dataType: "boolean", valueSetRef: "#/boolean" }),
        field("duplicateMergeStatus", "Duplicate / merge status", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/duplicateStatus" }),
      ],
    },
    {
      id: "names",
      title: "Names",
      fields: [
        field("familyName", "Surname", { requirement: "required", fhirConceptual: ["Patient.name.family"] }),
        field("givenName", "First name", { requirement: "required", fhirConceptual: ["Patient.name.given"] }),
        field("middleName", "Middle name", { fhirConceptual: ["Patient.name.given"] }),
        field("preferredName", "Preferred name", {}),
        field("previousAliasNames", "Previous / alias names", { dataType: "string_list" }),
        field("unknownNameFlag", "Unknown name (emergency)", { dataType: "boolean" }),
      ],
    },
    {
      id: "dob_age",
      title: "Date of birth / age",
      fields: [
        field("dateOfBirth", "Date of birth", { dataType: "date", fhirConceptual: ["Patient.birthDate"] }),
        field("estimatedDobFlag", "Estimated DOB", { dataType: "boolean" }),
        field("approximateAgeYears", "Approximate age (years)", { dataType: "integer", conditionalWhen: "dob_unknown" }),
        field("dobCertainty", "DOB certainty", {
          requirement: "required",
          valueSetRef: "terminology/client-registration-value-sets.schema.json#/dobCertainty",
          fhirConceptual: ["Patient.birthDate.extension"],
        }),
      ],
    },
    {
      id: "sex_gender",
      title: "Sex / gender",
      fields: [
        field("administrativeSex", "Administrative sex", {
          requirement: "required",
          valueSetRef: "terminology/client-registration-value-sets.schema.json#/administrativeSex",
          fhirConceptual: ["Patient.gender"],
        }),
        field("genderIdentity", "Gender identity", {
          requirement: "conditional",
          description: "Only where national policy allows capture.",
        }),
      ],
    },
    {
      id: "national_identifiers",
      title: "National or other identifiers",
      fields: [
        field("nationalId", "National ID", { privacyClass: "SENSITIVE_IDENTIFIER" }),
        field("birthEntryNumber", "Birth entry number", {}),
        field("passportNumber", "Passport number", {}),
        field("refugeeAsylumDocumentNumber", "Refugee / asylum document number", {}),
        field("otherIdentifierType", "Other identifier type", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/idDocumentType" }),
        field("noDocumentFlag", "No document on file", { dataType: "boolean" }),
      ],
    },
    {
      id: "contact",
      title: "Contact",
      fields: [
        field("primaryPhone", "Primary phone", { fhirConceptual: ["Patient.telecom"] }),
        field("alternatePhone", "Alternate phone", { fhirConceptual: ["Patient.telecom"] }),
        field("email", "Email", { fhirConceptual: ["Patient.telecom"] }),
        field("preferredCommunicationChannel", "Preferred channel", {
          valueSetRef: "terminology/client-registration-value-sets.schema.json#/communicationChannel",
        }),
      ],
    },
    {
      id: "address_location",
      title: "Address / location",
      fields: [
        field("countryAlpha2", "Country (ISO 3166-1 alpha-2)", {
          requirement: "required",
          valueSetRef: "terminology/countries.iso3166-1.json",
          fhirConceptual: ["Patient.address.country"],
        }),
        field("provinceCode", "Province / Admin 1 code", {
          valueSetRef: "terminology/zimbabwe-provinces.seed.json",
          conditionalWhen: "country=ZW",
        }),
        field("districtCode", "District / Admin 2 code", { conditionalWhen: "country=ZW" }),
        field("wardCode", "Ward / Admin 3 code", { conditionalWhen: "country=ZW" }),
        field("localityGazetteerId", "Locality (Tuso gazetteer)", {
          description: "Resolved from Tuso Locality Gazetteer — not free-text village lists.",
        }),
        field("addressLineDescription", "Address description", {
          dataType: "text",
          requirement: "optional",
          description: "Narrative address line — allowed free text per doctrine.",
        }),
        field("gpsCoordinates", "GPS coordinates", { dataType: "geo_point" }),
        field("usualFacilityId", "Usual / catchment facility (Tuso)", { fhirConceptual: ["Patient.managingOrganization"] }),
      ],
    },
    {
      id: "guardian",
      title: "Guardian / next of kin",
      fields: [
        field("guardianName", "Guardian name", { fhirConceptual: ["Patient.contact.name"] }),
        field("guardianRelationship", "Relationship", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/relationshipToClient" }),
        field("guardianPhone", "Guardian phone", {}),
        field("guardianConsentAuthority", "Consent authority (minor)", {}),
      ],
    },
    {
      id: "language_accessibility",
      title: "Language / accessibility",
      fields: [
        field("preferredLanguage", "Preferred language", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/language" }),
        field("literacySupportNeed", "Literacy support need", { dataType: "boolean" }),
        field("disabilityAccommodationNeed", "Disability / accommodation", {
          valueSetRef: "terminology/client-registration-value-sets.schema.json#/disabilityNeed",
        }),
      ],
    },
    {
      id: "consent_privacy",
      title: "Consent and privacy",
      fields: [
        field("consentStatus", "Consent status", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/consentStatus" }),
        field("consentType", "Consent type", {}),
        field("consentCapturedAt", "Consent date/time", { dataType: "datetime" }),
        field("consentCapturedByActorId", "Consent captured by", {}),
        field("dataSharingPreference", "Data sharing preference", {}),
        field("consentDeferredReason", "Consent deferred reason", { conditionalWhen: "consent_deferred" }),
      ],
    },
    {
      id: "verification_assurance",
      title: "Verification and assurance",
      fields: [
        field("verificationLevel", "Verification level", {}),
        field("verificationMethod", "Verification method", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/verificationMethod" }),
        field("documentSeenFlag", "Document seen", { dataType: "boolean" }),
        field("biometricCapturedFlag", "Biometric captured", { dataType: "boolean" }),
        field("biometricCaptureStatus", "Biometric capture status", {}),
        field("verifiedByActorId", "Verified by", {}),
        field("verifiedAt", "Verified at", { dataType: "datetime" }),
      ],
    },
    {
      id: "provenance_audit",
      title: "Provenance / audit",
      fields: [
        field("createdByActorId", "Created by", { auditProvenance: true }),
        field("createdAt", "Created at", { dataType: "datetime" }),
        field("facilityId", "Facility / workspace (Tuso)", {}),
        field("deviceId", "Device", {}),
        field("onlineOfflineFlag", "Online/offline", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/onlineState" }),
        field("syncStatus", "Sync status", {}),
        field("sourceWorkflow", "Source workflow code", {}),
      ],
    },
  ],
});

write("client/client-registration-mode.schema.json", {
  ...templateMeta("impilo.registry.client.registration_mode.v1", "VITO", "Registration mode and assurance", ["Provenance", "AuditEvent"]),
  fields: [
    field("registrationMode", "Registration mode", {
      requirement: "required",
      valueSetRef: "terminology/client-registration-value-sets.schema.json#/registrationMode",
    }),
    field("initiatingActor", "Initiating actor", {
      requirement: "required",
      valueSetRef: "terminology/client-registration-value-sets.schema.json#/initiatingActor",
    }),
    field("initiatingContext", "Initiating context", {
      requirement: "required",
      valueSetRef: "terminology/client-registration-value-sets.schema.json#/initiatingContext",
    }),
    field("assuranceLevel", "Assurance level", {
      requirement: "required",
      valueSetRef: "terminology/client-registration-value-sets.schema.json#/assuranceLevel",
    }),
    field("identityState", "Identity state", {
      requirement: "required",
      valueSetRef: "terminology/client-registration-value-sets.schema.json#/identityState",
    }),
    field("followUpAction", "Follow-up action", { valueSetRef: "terminology/client-registration-value-sets.schema.json#/followUpAction" }),
    field("tshepoDecisionRef", "Tshepo decision reference", { dataType: "string" }),
    field("purposeOfUse", "Purpose of use", {}),
    field("consentState", "Consent state summary", {}),
    field("breakGlassFlag", "Break-glass", { dataType: "boolean" }),
    field("offlineSyncState", "Offline / sync state", {}),
  ],
});

write("client/client-ehr-registration.schema.json", {
  ...templateMeta("impilo.registry.client.ehr_registration.v1", "VITO", "Health-worker EHR registration", ["Patient", "Provenance"]),
  description:
    "Minimum safe dataset + duplicate search + Vito issuance. Extends client-core + registration-mode.",
  extendsTemplates: ["client/client-core.schema.json", "client/client-registration-mode.schema.json"],
  uiHints: {
    duplicateSearchRequired: true,
    allowEmergencyUnknownName: true,
    allowOfflineProvisional: true,
    encounterHandoffField: "vitoRegistryHealthId",
  },
});

write("client/client-self-registration.schema.json", {
  ...templateMeta("impilo.registry.client.self_registration.v1", "VITO", "Citizen self-registration", ["Patient", "Consent"]),
  extendsTemplates: ["client/client-core.schema.json", "client/client-registration-mode.schema.json"],
  uiHints: { defaultRegistrationMode: "SELF_REGISTRATION", initiatingActor: "CLIENT" },
});

write("client/client-assisted-registration.schema.json", {
  ...templateMeta("impilo.registry.client.assisted_registration.v1", "VITO", "Assisted registration", ["Patient", "Consent", "Provenance"]),
  extendsTemplates: ["client/client-core.schema.json", "client/client-registration-mode.schema.json"],
  uiHints: { defaultRegistrationMode: "ASSISTED_REGISTRATION" },
});

write("client/client-emergency-registration.schema.json", {
  ...templateMeta("impilo.registry.client.emergency_registration.v1", "VITO", "Emergency registration", ["Patient", "Provenance"]),
  extendsTemplates: ["client/client-core.schema.json", "client/client-registration-mode.schema.json"],
  uiHints: {
    defaultRegistrationMode: "EMERGENCY_REGISTRATION",
    allowUnknownName: true,
    defaultAssuranceLevel: "UNVERIFIED",
  },
});

write("client/client-coverage.schema.json", {
  ...templateMeta("impilo.registry.client.coverage.v1", "VITO", "Client coverage / medical aid", ["Coverage", "Consent", "Provenance"]),
  description:
    "Canonical coverage is not owned by the EHR. Capture forwards to Vito summary + MusheX eligibility + COSTA billing rules.",
  downstream: { vito: "coverage_summary", mushex: "eligibility_claims_cards", costa: "billing_rules" },
  fields: [
    field("coverageStatus", "Coverage status", {
      requirement: "required",
      valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/coverageStatus",
      fhirConceptual: ["Coverage.status"],
    }),
    field("payerType", "Payer type", { valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/payerType" }),
    field("schemeOrInsurerName", "Medical aid / insurer / scheme", { fhirConceptual: ["Coverage.payor"] }),
    field("planName", "Scheme or plan", { fhirConceptual: ["Coverage.class"] }),
    field("membershipNumber", "Membership number", { privacyClass: "SENSITIVE_FINANCIAL", fhirConceptual: ["Coverage.subscriberId"] }),
    field("principalMemberRelationship", "Principal member relationship", {
      valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/principalMemberRelationship",
    }),
    field("principalMemberName", "Principal member name", {}),
    field("principalMemberIdNumber", "Principal member ID number", { privacyClass: "SENSITIVE_IDENTIFIER" }),
    field("principalMemberPhone", "Principal member phone", {}),
    field("dependentStatus", "Dependent status", { valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/dependentStatus" }),
    field("relationshipToSubscriber", "Relationship to principal member", { fhirConceptual: ["Coverage.relationship"] }),
    field("coverageStart", "Coverage start", { dataType: "date", fhirConceptual: ["Coverage.period.start"] }),
    field("coverageEnd", "Coverage end", { dataType: "date", fhirConceptual: ["Coverage.period.end"] }),
    field("coverageVerificationStatus", "Verification status", {
      valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/coverageVerificationStatus",
    }),
    field("benefitClass", "Benefit class", { valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/benefitClass" }),
    field("coPaymentRule", "Co-payment rule", { valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/coPaymentRule" }),
    field("claimsDestination", "Claims destination", { valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/claimsDestination" }),
    field("eligibilityCheckConsent", "Consent for eligibility check", {
      valueSetRef: "terminology/coverage-payment-value-sets.schema.json#/eligibilityCheckConsent",
    }),
    field("capturedByActorId", "Captured by", {}),
    field("capturedAtFacilityId", "Captured at facility/workspace", {}),
    field("capturedAt", "Captured date/time", { dataType: "datetime" }),
    field("sourceWorkflow", "Source workflow", {}),
  ],
});

const provFields = (extra) => ({
  ...templateMeta(`impilo.registry.provider.${extra.id}.v1`, "VARAPI", extra.title, extra.fhir),
  fields: extra.fields,
});

write(
  "provider/provider-person.schema.json",
  provFields({
    id: "person",
    title: "Provider person",
    fhir: ["Practitioner"],
    fields: [
      field("personIdentifier", "Person / staff identifier", {}),
      field("impiloHealthIdAnchor", "Impilo Health ID anchor (Vito)", {}),
      field("familyName", "Surname", { requirement: "required" }),
      field("givenName", "Given name", { requirement: "required" }),
      field("dateOfBirth", "Date of birth", { dataType: "date" }),
      field("nationalId", "National ID", { privacyClass: "SENSITIVE_IDENTIFIER" }),
      field("primaryPhone", "Phone", {}),
      field("email", "Email", {}),
      field("countryAlpha2", "Country", { valueSetRef: "terminology/countries.iso3166-1.json" }),
    ],
  }),
);

write(
  "provider/provider-professional-profile.schema.json",
  provFields({
    id: "professional",
    title: "Professional profile",
    fhir: ["Practitioner", "PractitionerRole"],
    fields: [
      field("providerRegistryId", "Provider registry ID", {}),
      field("professionalCouncil", "Professional council", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/council" }),
      field("licenceNumber", "Licence / registration number", {}),
      field("cadre", "Cadre", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/cadre" }),
      field("profession", "Profession", {}),
      field("specialty", "Specialty", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/specialty" }),
      field("verifiedStatus", "Verified status", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/verificationStatus" }),
      field("licenceStatus", "Licence status", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/licenceStatus" }),
    ],
  }),
);

write(
  "provider/provider-facility-affiliation.schema.json",
  provFields({
    id: "affiliation",
    title: "Facility affiliation",
    fhir: ["PractitionerRole", "Organization"],
    fields: [
      field("tusoFacilityId", "Facility (Tuso)", { requirement: "required" }),
      field("departmentUnit", "Department / unit", {}),
      field("roleCode", "Role", { valueSetRef: "terminology/provider-registration-value-sets.schema.json#/facilityRole" }),
      field("startDate", "Start date", { dataType: "date" }),
      field("endDate", "End date", { dataType: "date" }),
      field("affiliationType", "Employment / affiliation type", {
        valueSetRef: "terminology/provider-registration-value-sets.schema.json#/affiliationType",
      }),
      field("supervisorProviderId", "Supervisor", {}),
      field("verificationStatus", "Verification status", {}),
    ],
  }),
);

write(
  "provider/provider-work-context.schema.json",
  provFields({
    id: "work_context",
    title: "Work context",
    fhir: ["PractitionerRole"],
    fields: [
      field("defaultFacilityId", "Default facility", {}),
      field("selectedFacilityId", "Selected facility", { requirement: "required" }),
      field("selectedWorkspaceId", "Selected workspace", {}),
      field("shiftContext", "Shift / roster context", {}),
      field("roleInContext", "Role in current context", {}),
      field("entitlementsSummary", "Entitlements (Tshepo)", {}),
      field("purposeOfUse", "Purpose of use", {}),
      field("tshepoDecisionRef", "Tshepo decision reference", {}),
    ],
  }),
);

const fac = (name, title, fhir, fields) =>
  write(name, { ...templateMeta(`impilo.registry.facility.${path.basename(name, ".schema.json")}.v1`, "TUSO", title, fhir), fields });

fac(
  "facility/facility-core.schema.json",
  "Facility core",
  ["Organization"],
  [
    field("tusoFacilityId", "Tuso facility ID", {}),
    field("nationalFacilityCode", "National facility code", {}),
    field("dhis2OrgUnitCode", "DHIS2 org unit code", {}),
    field("facilityName", "Facility name", { requirement: "required" }),
    field("aliases", "Aliases", { dataType: "string_list" }),
    field("facilityType", "Facility type", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/facilityType" }),
    field("ownership", "Ownership", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/ownership" }),
    field("operationalStatus", "Operational status", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/operationalStatus" }),
    field("managingAuthority", "Managing authority", {}),
  ],
);

fac(
  "facility/facility-location.schema.json",
  "Facility location",
  ["Location"],
  [
    field("countryAlpha2", "Country", { valueSetRef: "terminology/countries.iso3166-1.json" }),
    field("provinceCode", "Province", {}),
    field("districtCode", "District", {}),
    field("wardCode", "Ward", {}),
    field("localityGazetteerId", "Locality (gazetteer)", {}),
    field("physicalAddress", "Physical address", { dataType: "text" }),
    field("gpsCoordinates", "GPS", { dataType: "geo_point" }),
    field("catchmentAreaCode", "Catchment area", {}),
    field("nearestReferralFacilityId", "Nearest referral facility", {}),
  ],
);

fac(
  "facility/facility-services.schema.json",
  "Healthcare services",
  ["HealthcareService"],
  [
    field("serviceCategory", "Service category", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/serviceCategory" }),
    field("serviceAvailability", "Availability status", {}),
    field("serviceScheduleNote", "Schedule note", { dataType: "text" }),
  ],
);

fac(
  "facility/facility-licensing.schema.json",
  "Licensing / inspection",
  ["Organization"],
  [
    field("licenceStatus", "Licence status", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/licenceStatus" }),
    field("licenceNumber", "Licence number", {}),
    field("issuingAuthority", "Issuing authority", {}),
    field("inspectionStatus", "Inspection status", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/inspectionStatus" }),
    field("inspectionDate", "Inspection date", { dataType: "date" }),
    field("renewalDate", "Renewal date", { dataType: "date" }),
    field("findingsSummary", "Findings summary", { dataType: "text" }),
  ],
);

fac(
  "facility/facility-digital-readiness.schema.json",
  "Digital readiness",
  ["Organization"],
  [
    field("impiloEhrDeployed", "Impilo EHR deployed", { dataType: "boolean" }),
    field("ehrVersion", "EHR version", {}),
    field("connectivityStatus", "Connectivity status", {}),
    field("connectivityType", "Connectivity type", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/connectivityType" }),
    field("powerBackupType", "Power backup", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/powerBackupType" }),
    field("deviceAvailabilityNote", "Device availability", { dataType: "text" }),
    field("supportFocalPerson", "Support focal person", {}),
    field("deploymentStatus", "Deployment status", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/deploymentStatus" }),
    field("lastSupportVisit", "Last support visit", { dataType: "date" }),
    field("readinessStatus", "Readiness status", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/readinessStatus" }),
  ],
);

fac(
  "facility/facility-locality-gazetteer.schema.json",
  "Locality gazetteer entry",
  ["Location"],
  [
    field("localityId", "Locality ID", {}),
    field("localityName", "Locality name", { requirement: "required" }),
    field("localityType", "Locality type", { valueSetRef: "terminology/facility-registration-value-sets.schema.json#/localityType" }),
    field("wardCode", "Ward", {}),
    field("districtCode", "District", {}),
    field("provinceCode", "Province", {}),
    field("gpsCoordinates", "GPS", { dataType: "geo_point" }),
    field("nearestFacilityId", "Nearest facility", {}),
    field("catchmentFacilityId", "Catchment facility", {}),
    field("active", "Active", { dataType: "boolean" }),
    field("governanceState", "Proposed / pending / approved / retired", {}),
    field("sourceAuthority", "Source authority", {}),
    field("approvedByActorId", "Approved by", {}),
    field("approvedAt", "Approved at", { dataType: "datetime" }),
    field("replacedByLocalityId", "Replaced by", {}),
  ],
);

const coded = (code, display, extra = {}) => ({ code, display, ...extra });

write("terminology/client-registration-value-sets.schema.json", {
  id: "impilo.value_sets.client_registration.v1",
  entries: {
    registrationMode: [
      coded("SELF_REGISTRATION", "Self-registration"),
      coded("HEALTH_WORKER_INITIATED", "Health-worker initiated"),
      coded("ASSISTED_REGISTRATION", "Assisted registration"),
      coded("EMERGENCY_REGISTRATION", "Emergency registration"),
      coded("OUTREACH_REGISTRATION", "Outreach registration"),
      coded("OFFLINE_REGISTRATION", "Offline registration"),
      coded("SYSTEM_IMPORT", "System import"),
    ],
    initiatingActor: [
      coded("CLIENT", "Client"),
      coded("PROVIDER", "Provider"),
      coded("CLERK", "Clerk"),
      coded("CHW", "Community health worker"),
      coded("CALL_CENTRE_AGENT", "Call centre agent"),
      coded("OUTREACH_TEAM", "Outreach team"),
      coded("SYSTEM_IMPORT", "System import"),
    ],
    initiatingContext: [
      coded("FACILITY", "Facility"),
      coded("COMMUNITY", "Community"),
      coded("HOME_VISIT", "Home visit"),
      coded("TELEMEDICINE", "Telemedicine"),
      coded("MOBILE_APP", "Mobile app"),
      coded("WEB_PORTAL", "Web portal"),
      coded("USSD_SMS", "USSD / SMS / messaging"),
      coded("OFFLINE_DEVICE", "Offline device"),
    ],
    assuranceLevel: [
      coded("UNVERIFIED", "Unverified"),
      coded("SELF_DECLARED", "Self-declared"),
      coded("ASSISTED_UNVERIFIED", "Assisted — unverified"),
      coded("DOCUMENT_SEEN", "Document seen"),
      coded("PROVIDER_ATTESTED", "Provider attested"),
      coded("BIOMETRIC_CONFIRMED", "Biometric confirmed"),
      coded("CIVIL_REGISTRY_CONFIRMED", "Civil registry confirmed"),
    ],
    identityState: [
      coded("TEMPORARY", "Temporary"),
      coded("PROVISIONAL", "Provisional"),
      coded("ACTIVE", "Active"),
      coded("VERIFIED", "Verified"),
      coded("DUPLICATE_SUSPECTED", "Duplicate suspected"),
      coded("MERGED", "Merged"),
      coded("DEACTIVATED", "Deactivated"),
    ],
    followUpAction: [
      coded("NEEDS_DOCUMENT_VERIFICATION", "Needs document verification"),
      coded("NEEDS_BIOMETRIC", "Needs biometric capture"),
      coded("NEEDS_DUPLICATE_RESOLUTION", "Needs duplicate resolution"),
      coded("NEEDS_GUARDIAN_CONFIRMATION", "Needs guardian confirmation"),
      coded("NEEDS_FACILITY_VERIFICATION", "Needs facility verification"),
      coded("NONE", "None"),
    ],
    administrativeSex: [
      coded("male", "Male"),
      coded("female", "Female"),
      coded("other", "Other"),
      coded("unknown", "Unknown"),
    ],
    dobCertainty: [
      coded("EXACT", "Exact"),
      coded("ESTIMATED", "Estimated"),
      coded("UNKNOWN", "Unknown"),
    ],
    idDocumentType: [
      coded("NATIONAL_ID", "National ID"),
      coded("PASSPORT", "Passport"),
      coded("BIRTH_CERTIFICATE", "Birth certificate"),
      coded("REFUGEE_ASYLUM", "Refugee / asylum document"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    relationshipToClient: [
      coded("MOTHER", "Mother"),
      coded("FATHER", "Father"),
      coded("GUARDIAN", "Guardian"),
      coded("SPOUSE", "Spouse"),
      coded("SIBLING", "Sibling"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    communicationChannel: [
      coded("SMS", "SMS"),
      coded("VOICE", "Voice call"),
      coded("EMAIL", "Email"),
      coded("WHATSAPP", "WhatsApp"),
      coded("IN_APP", "In-app"),
    ],
    consentStatus: [
      coded("OBTAINED", "Obtained"),
      coded("DEFERRED", "Deferred"),
      coded("REFUSED", "Refused"),
      coded("NOT_APPLICABLE", "Not applicable"),
    ],
    disabilityNeed: [
      coded("NONE", "None reported"),
      coded("MOBILITY", "Mobility"),
      coded("VISION", "Vision"),
      coded("HEARING", "Hearing"),
      coded("COGNITIVE", "Cognitive"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    language: [
      coded("en", "English"),
      coded("sn", "Shona"),
      coded("nd", "Ndebele"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    verificationMethod: [
      coded("IN_PERSON_DOCUMENT", "In-person document"),
      coded("REMOTE_VIDEO", "Remote video"),
      coded("BANK_KYC", "Bank KYC"),
      coded("CIVIL_REGISTRY", "Civil registry"),
    ],
    onlineState: [
      coded("ONLINE", "Online"),
      coded("OFFLINE", "Offline"),
    ],
    linkageStatus: [
      coded("LINKED", "Linked"),
      coded("PENDING", "Pending"),
      coded("FAILED", "Failed"),
    ],
    duplicateStatus: [
      coded("NONE", "None"),
      coded("SUSPECTED", "Suspected"),
      coded("CONFIRMED_DUPLICATE", "Confirmed duplicate"),
      coded("MERGED", "Merged"),
    ],
  },
});

write("terminology/provider-registration-value-sets.schema.json", {
  id: "impilo.value_sets.provider_registration.v1",
  entries: {
    council: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    cadre: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    specialty: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    verificationStatus: [
      coded("UNVERIFIED", "Unverified"),
      coded("VERIFIED", "Verified"),
      coded("PENDING", "Pending"),
    ],
    licenceStatus: [
      coded("ACTIVE", "Active"),
      coded("PENDING", "Pending"),
      coded("EXPIRED", "Expired"),
      coded("SUSPENDED", "Suspended"),
      coded("REVOKED", "Revoked"),
      coded("RETIRED", "Retired"),
    ],
    facilityRole: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    affiliationType: [
      coded("PERMANENT", "Permanent"),
      coded("CONTRACT", "Contract"),
      coded("LOCUM", "Locum"),
      coded("VOLUNTEER", "Volunteer"),
      coded("STUDENT", "Student"),
      coded("SECONDED", "Seconded"),
      coded("VISITING", "Visiting"),
      coded("PRIVATE_PROVIDER", "Private provider"),
    ],
  },
});

write("terminology/facility-registration-value-sets.schema.json", {
  id: "impilo.value_sets.facility_registration.v1",
  entries: {
    facilityType: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    ownership: [{ code: "OTHER", display: "Other / specify", requiresSpecifyText: true, terminologyReview: true }],
    operationalStatus: [
      coded("OPERATIONAL", "Operational"),
      coded("PARTIALLY_OPERATIONAL", "Partially operational"),
      coded("NOT_OPERATIONAL", "Not operational"),
    ],
    serviceCategory: [
      coded("OPD", "OPD"),
      coded("MATERNITY", "Maternity"),
      coded("ART", "ART"),
      coded("TB", "TB"),
      coded("IMMUNISATION", "Immunisation"),
      coded("PHARMACY", "Pharmacy"),
      coded("LAB", "Laboratory"),
      coded("RADIOLOGY", "Radiology"),
      coded("THEATRE", "Theatre"),
      coded("EMERGENCY", "Emergency"),
      coded("TELEMEDICINE", "Telemedicine"),
      coded("OUTREACH", "Outreach"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    licenceStatus: [
      coded("LICENSED", "Licensed"),
      coded("PENDING", "Pending"),
      coded("EXPIRED", "Expired"),
      coded("REVOKED", "Revoked"),
    ],
    inspectionStatus: [
      coded("NOT_INSPECTED", "Not inspected"),
      coded("PASSED", "Passed"),
      coded("FAILED", "Failed"),
      coded("CONDITIONAL", "Conditional"),
    ],
    connectivityType: [
      coded("FIBRE", "Fibre"),
      coded("MOBILE_BROADBAND", "Mobile broadband"),
      coded("SATELLITE", "Satellite"),
      coded("MICROWAVE", "Microwave"),
      coded("NONE", "None"),
      coded("UNKNOWN", "Unknown"),
    ],
    powerBackupType: [
      coded("SOLAR", "Solar"),
      coded("GENERATOR", "Generator"),
      coded("INVERTER", "Inverter"),
      coded("UPS", "UPS"),
      coded("NONE", "None"),
      coded("UNKNOWN", "Unknown"),
    ],
    deploymentStatus: [
      coded("NOT_DEPLOYED", "Not deployed"),
      coded("ROLLOUT", "Rollout"),
      coded("LIVE", "Live"),
    ],
    readinessStatus: [
      coded("LOW", "Low"),
      coded("MEDIUM", "Medium"),
      coded("HIGH", "High"),
    ],
    localityType: [
      coded("VILLAGE", "Village"),
      coded("SUBURB", "Suburb"),
      coded("GROWTH_POINT", "Growth point"),
      coded("SETTLEMENT", "Settlement"),
      coded("FARM", "Farm"),
      coded("MINE", "Mine"),
      coded("SCHOOL_AREA", "School area"),
      coded("CHIEF_AREA", "Chief / headman area"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
  },
});

write("terminology/coverage-payment-value-sets.schema.json", {
  id: "impilo.value_sets.coverage_payment.v1",
  entries: {
    coverageStatus: [
      coded("NO_COVER", "No cover"),
      coded("MEDICAL_AID", "Medical aid"),
      coded("PRIVATE_INSURANCE", "Private insurance"),
      coded("EMPLOYER_COVER", "Employer cover"),
      coded("GOVERNMENT_PROGRAMME", "Government programme"),
      coded("DONOR_PROGRAMME", "Donor / programme cover"),
      coded("SOCIAL_PROTECTION", "Social protection cover"),
      coded("SELF_PAY", "Self-pay"),
      coded("UNKNOWN", "Unknown"),
    ],
    payerType: [
      coded("MEDICAL_AID", "Medical aid"),
      coded("PRIVATE_INSURER", "Private insurer"),
      coded("EMPLOYER", "Employer"),
      coded("GOVERNMENT", "Government"),
      coded("DONOR", "Donor"),
      coded("SELF", "Self"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    principalMemberRelationship: [
      coded("SELF", "Self"),
      coded("SPOUSE", "Spouse"),
      coded("PARENT_GUARDIAN", "Parent / guardian"),
      coded("EMPLOYER", "Employer"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    dependentStatus: [
      coded("PRINCIPAL", "Principal member"),
      coded("SPOUSE", "Spouse dependent"),
      coded("CHILD", "Child dependent"),
      coded("ADULT", "Adult dependent"),
      coded("OTHER", "Other dependent"),
    ],
    benefitClass: [
      coded("OUTPATIENT", "Outpatient"),
      coded("INPATIENT", "Inpatient"),
      coded("MATERNITY", "Maternity"),
      coded("PHARMACY", "Pharmacy"),
      coded("DIAGNOSTICS", "Diagnostics"),
      coded("CHRONIC", "Chronic care"),
      coded("EMERGENCY", "Emergency"),
      coded("DENTAL", "Dental"),
      coded("OPTICAL", "Optical"),
      coded("OTHER", "Other / specify", { requiresSpecifyText: true, terminologyReview: true }),
    ],
    coPaymentRule: [
      coded("NONE", "None"),
      coded("FIXED_AMOUNT", "Fixed amount"),
      coded("PERCENTAGE", "Percentage"),
      coded("UNKNOWN", "Unknown"),
      coded("SCHEME_DETERMINED", "Scheme-determined"),
    ],
    claimsDestination: [
      coded("MUSHEX", "MusheX"),
      coded("DIRECT_INSURER", "Direct insurer claim"),
      coded("EMPLOYER", "Employer claim"),
      coded("MANUAL", "Manual claim"),
      coded("NOT_CLAIMABLE", "Not claimable"),
    ],
    coverageVerificationStatus: [
      coded("UNVERIFIED", "Unverified"),
      coded("CARD_SEEN", "Card seen"),
      coded("DIGITALLY_VERIFIED", "Digitally verified"),
      coded("EMPLOYER_CONFIRMED", "Employer confirmed"),
      coded("INSURER_VERIFIED", "Insurer verified"),
      coded("EXPIRED", "Expired"),
    ],
    eligibilityCheckConsent: [
      coded("GRANTED", "Granted"),
      coded("REFUSED", "Refused"),
      coded("DEFERRED", "Deferred"),
    ],
    paymentMethod: [
      coded("CASH", "Cash"),
      coded("CARD", "Card"),
      coded("MOBILE_MONEY", "Mobile money"),
      coded("BANK_TRANSFER", "Bank transfer"),
      coded("WALLET", "Wallet"),
      coded("VOUCHER", "Voucher"),
    ],
    billingArrangement: [
      coded("SELF_PAY", "Self-pay"),
      coded("MEDICAL_AID", "Medical aid"),
      coded("CAPITATION", "Capitation"),
      coded("BUNDLED", "Bundled payment"),
      coded("NO_CHARGE", "No charge"),
      coded("SUBSIDISED", "Subsidised"),
      coded("PROGRAMME_FUNDED", "Programme-funded"),
    ],
  },
});

write("terminology/countries.iso3166-1.schema.json", {
  id: "impilo.terminology.iso3166_1.v1",
  description: "Wrapper schema for countries.iso3166-1.json rows.",
  recordShape: { name: "string", alpha2: "string", alpha3: "string", numeric: "string" },
  zimbabwe: { alpha2: "ZW", alpha3: "ZWE", numeric: "716", name: "Zimbabwe" },
});

write("terminology/zimbabwe-provinces.schema.json", {
  id: "impilo.terminology.zw.admin1.v1",
  referencesSeed: "terminology/zimbabwe-provinces.seed.json",
});

write("terminology/zimbabwe-districts.schema.json", {
  id: "impilo.terminology.zw.admin2.v1",
  referencesSeed: "terminology/data/zimbabwe-districts.seed.json",
  note: "District codes must be imported from COD-AB / ZIMSTAT — seed file starts empty.",
});

write("terminology/zimbabwe-wards.schema.json", {
  id: "impilo.terminology.zw.admin3.v1",
  referencesSeed: "terminology/data/zimbabwe-wards.seed.json",
  note: "Ward codes must be imported from COD-AB (~1961 wards) — seed file starts empty.",
});

write("manifest.json", {
  version: "1.0.0",
  templates: [
    "client/client-core.schema.json",
    "client/client-registration-mode.schema.json",
    "client/client-ehr-registration.schema.json",
    "client/client-self-registration.schema.json",
    "client/client-assisted-registration.schema.json",
    "client/client-emergency-registration.schema.json",
    "client/client-coverage.schema.json",
    "provider/provider-person.schema.json",
    "provider/provider-professional-profile.schema.json",
    "provider/provider-facility-affiliation.schema.json",
    "provider/provider-work-context.schema.json",
    "facility/facility-core.schema.json",
    "facility/facility-location.schema.json",
    "facility/facility-services.schema.json",
    "facility/facility-licensing.schema.json",
    "facility/facility-digital-readiness.schema.json",
    "facility/facility-locality-gazetteer.schema.json",
    "terminology/countries.iso3166-1.schema.json",
    "terminology/zimbabwe-provinces.schema.json",
    "terminology/zimbabwe-districts.schema.json",
    "terminology/zimbabwe-wards.schema.json",
    "terminology/client-registration-value-sets.schema.json",
    "terminology/provider-registration-value-sets.schema.json",
    "terminology/facility-registration-value-sets.schema.json",
    "terminology/coverage-payment-value-sets.schema.json",
  ],
});

console.log("Done.");
