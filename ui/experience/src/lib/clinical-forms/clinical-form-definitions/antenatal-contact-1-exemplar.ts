import type { ClinicalFormDefinition } from "../types";
import { ANC_LOINC } from "../terminology-bindings/anc-codes";
import { ANC_INDICATORS } from "../indicator-mapping/anc-indicators";
import { WHO_DAK_ANTENATAL_CARE_REF } from "../dak-mapping/who-dak-antenatal-care-v1";

/**
 * Exemplar: first antenatal contact — structured, coded, DAK-aligned (subset).
 * Replaces free-text chief complaint with coded reason + optional note.
 */
export const ANTENATAL_CONTACT_1_FORM: ClinicalFormDefinition = {
  id: "impilo.anc.contact1.v1",
  version: "1.0.0",
  title: "Antenatal care — first contact (structured)",
  description:
    "WHO ANC-aligned exemplar: history, screening placeholders, counselling, referral decision. " +
    "Use alongside national guidelines; not a complete DAK import.",
  dakReference: WHO_DAK_ANTENATAL_CARE_REF,
  healthDomain: "MATERNAL",
  programme: "ANC",
  encounterTypes: ["ANTENATAL", "ANC", "MATERNITY", "OBSTETRICS"],
  minAgeMonths: 120,
  sexApplicability: "ALL",
  providerRoles: ["MIDWIFE", "NURSE", "CLINICIAN", "DOCTOR"],
  offlineCapable: true,
  localization: { defaultLocale: "en", supportedLocales: ["en"] },
  audit: {
    dataCustodian: "MOHCC",
    sensitivity: "SENSITIVE",
  },
  sections: [
    {
      id: "history",
      title: "Obstetric & presenting history",
      description: "Core data elements for first ANC contact (DAK-aligned subset).",
      fields: [
        {
          id: "presenting_complaint",
          linkId: "presenting-complaint",
          label: "Main reason for visit",
          kind: "coded_single",
          terminologyBinding: { system: "http://snomed.info/sct", code: "267036007", display: "Presenting complaint" },
          options: [
            { code: "booking", display: "Routine ANC booking", terminologyCode: "424441002", terminologySystem: "http://snomed.info/sct" },
            { code: "unplanned_pregnancy", display: "Unplanned pregnancy counselling" },
            { code: "symptoms", display: "Symptoms (vomiting, pain, bleeding)" },
            { code: "previous_high_risk", display: "Follow-up of prior high-risk pregnancy" },
            { code: "other", display: "Other (specify in notes)" },
          ],
          allowAuxiliaryNarrative: true,
          auxiliaryNarrativeLabel: "Additional clinical detail (optional)",
          validation: { required: true },
          fhir: { questionnaireItemLinkId: "presenting-complaint" },
          indicators: [{ programmeIndicatorCode: ANC_INDICATORS.ANC1_CONTACT }],
        },
        {
          id: "gravida",
          linkId: "gravida",
          label: "Gravida",
          kind: "numeric_with_unit",
          unit: "{count}",
          terminologyBinding: { system: "http://loinc.org", code: ANC_LOINC.gravida },
          validation: { required: false, min: 0, max: 20 },
          visibility: { requireSex: ["FEMALE"], excludeAgeBands: ["NEONATAL", "INFANT", "CHILD"] },
          fhir: { questionnaireItemLinkId: "gravida", fhirElementId: "Observation.component" },
        },
        {
          id: "para",
          linkId: "para",
          label: "Para (live births)",
          kind: "numeric_with_unit",
          unit: "{count}",
          terminologyBinding: { system: "http://loinc.org", code: ANC_LOINC.para },
          validation: { min: 0, max: 20 },
          visibility: { requireSex: ["FEMALE"], excludeAgeBands: ["NEONATAL", "INFANT", "CHILD"] },
          fhir: { questionnaireItemLinkId: "para" },
        },
        {
          id: "lmp",
          linkId: "lmp",
          label: "Last menstrual period",
          kind: "date",
          terminologyBinding: { system: "http://loinc.org", code: ANC_LOINC.lmp },
          visibility: { requireSex: ["FEMALE"], excludeAgeBands: ["NEONATAL", "INFANT", "CHILD"] },
          fhir: { questionnaireItemLinkId: "lmp" },
        },
      ],
    },
    {
      id: "screening",
      title: "Screening (placeholders)",
      fields: [
        {
          id: "syphilis_screen",
          linkId: "syphilis-screen",
          label: "Syphilis screening status",
          kind: "segmented",
          options: [
            { code: "done_negative", display: "Done — negative" },
            { code: "done_positive", display: "Done — positive / reactive" },
            { code: "ordered", display: "Ordered / pending" },
            { code: "declined", display: "Declined" },
          ],
          validation: { required: true },
          terminologyBinding: { system: "http://loinc.org", code: ANC_LOINC.syphilisScreen },
          fhir: { questionnaireItemLinkId: "syphilis-screen" },
          indicators: [{ programmeIndicatorCode: ANC_INDICATORS.SYPHILIS_SCREEN_DONE }],
        },
        {
          id: "hiv_status_known",
          linkId: "hiv-status",
          label: "HIV status documented",
          kind: "coded_single",
          options: [
            { code: "negative", display: "Negative" },
            { code: "positive", display: "Positive" },
            { code: "unknown", display: "Unknown / not yet tested" },
          ],
          validation: { required: true },
          terminologyBinding: { system: "http://loinc.org", code: ANC_LOINC.hivStatus },
          fhir: { questionnaireItemLinkId: "hiv-status" },
          indicators: [{ programmeIndicatorCode: ANC_INDICATORS.HIV_STATUS_DOCUMENTED }],
        },
      ],
    },
    {
      id: "counselling",
      title: "Counselling & education",
      fields: [
        {
          id: "danger_signs_education",
          linkId: "danger-signs",
          label: "Danger signs discussed (select all that apply)",
          kind: "coded_multi",
          options: [
            { code: "vaginal_bleeding", display: "Heavy vaginal bleeding" },
            { code: "severe_headache", display: "Severe headache / visual disturbance" },
            { code: "severe_abdo_pain", display: "Severe abdominal pain" },
            { code: "reduced_fetal_movement", display: "Reduced fetal movement" },
            { code: "breathlessness", display: "Breathlessness / chest pain" },
          ],
          validation: { required: false },
          fhir: { questionnaireItemLinkId: "danger-signs" },
        },
        {
          id: "partner_involvement",
          linkId: "partner",
          label: "Partner involvement documented",
          kind: "coded_single",
          options: [
            { code: "present", display: "Partner present / engaged" },
            { code: "not_present", display: "Partner not present" },
            { code: "not_applicable", display: "Not applicable" },
          ],
          fhir: { questionnaireItemLinkId: "partner" },
          indicators: [{ programmeIndicatorCode: ANC_INDICATORS.PARTNER_INVOLVEMENT_DOCUMENTED }],
        },
      ],
    },
    {
      id: "referral",
      title: "Referral & follow-up",
      fields: [
        {
          id: "referral_need",
          linkId: "referral-need",
          label: "Referral need",
          kind: "coded_single",
          options: [
            { code: "none", display: "None — continue routine ANC" },
            { code: "higher_level_anc", display: "Higher-level ANC facility" },
            { code: "emergency_obstetrics", display: "Emergency obstetric unit" },
            { code: "social_support", display: "Social / protection support" },
          ],
          validation: { required: true },
          fhir: { questionnaireItemLinkId: "referral-need" },
        },
        {
          id: "clinical_narrative",
          linkId: "narrative",
          label: "Clinical narrative (justification / unusual findings)",
          kind: "text",
          description: "Free text reserved for narrative, unusual findings, or medico-legal justification.",
          validation: { maxLength: 4000, required: false },
          fhir: { questionnaireItemLinkId: "narrative" },
        },
      ],
    },
  ],
};
