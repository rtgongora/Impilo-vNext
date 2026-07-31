// Decorative-control baseline — <button> elements that do nothing when clicked.
//
// Recorded 2026-07-28, when the check that finds them was written. Every entry is a control a user
// can click that has no behaviour: no handler, no submit, not disabled. They have hover states and
// labels, so they are indistinguishable from working controls to the person using them.
//
// THIS IS NOT AN EXEMPTION LIST. Wiring a control means deciding what it should do — which mutation,
// which confirmation, which failure message — and that belongs to the lane that owns the surface,
// not to whoever wrote the check. Guessing 70 of those would put wrong behaviour behind real
// buttons, which is worse than none.
//
// What it buys: any NEW decorative control fails the build. This list can only shrink. Removing an
// entry means wiring the control, making it a link, or disabling it with a visible reason — never
// just deleting the line.
//
// The ones worth doing first are the labelled ones, because the label is the promise:
//   "Save Draft" / "Preview Summary"                OutcomeSection      — discharge outcome
//   "Save Update" / "Link Contacts" / "Generate Line List"  SurveillanceTab — outbreak response
//   "Resubmit"                                       BillingPanel
//   "Edit" / "Add Member" / "New Assessment"         EHR chart tabs
export const DECORATIVE_CONTROL_BASELINE = [
  "src/app/caregiving/dependants/page.tsx:29",
  "src/app/caregiving/notifications/page.tsx:21",
  "src/app/caregiving/notifications/page.tsx:25",
  "src/app/caregiving/tasks/page.tsx:29",
  "src/app/developer/clients/page.tsx:28",
  "src/app/developer/sandbox/page.tsx:42",
  "src/app/developer/sandbox/page.tsx:46",
  "src/app/discover/providers/page.tsx:36",
  "src/app/discover/providers/page.tsx:45",
  "src/app/discover/services/page.tsx:37",
  "src/app/ehr/[patientId]/assessments/page.tsx:163",
  "src/app/ehr/[patientId]/functional-status/page.tsx:145",
  "src/app/ehr/[patientId]/goals/page.tsx:249",
  "src/app/home/credentials/page.tsx:155",
  "src/app/home/credentials/page.tsx:158",
  "src/app/home/page.tsx:1938",
  "src/app/home/page.tsx:1941",
  "src/app/home/page.tsx:1944",
  "src/app/lab/results/page.tsx:101",
  "src/app/monitoring/care-plans/page.tsx:30",
  "src/app/monitoring/provider-dashboard/page.tsx:56",
  "src/app/support/tickets/page.tsx:146",
  "src/app/support/tickets/page.tsx:151",
  "src/app/wellness/programs/page.tsx:47",
  "src/components/clinical/ActiveCDSBanner.tsx:234",
  "src/components/clinical/ClinicalToolbar.tsx:108",
  "src/components/clinical/ClinicalToolbar.tsx:81",
  "src/components/ehr/EncounterDocumentsSheet.tsx:159",
  "src/components/ehr/EncounterDocumentsSheet.tsx:163",
  "src/components/ehr/EncounterDocumentsSheet.tsx:286",
  "src/components/ehr/EncounterDocumentsSheet.tsx:292",
  "src/components/ehr/EncounterDocumentsSheet.tsx:317",
  "src/components/ehr/EncounterDocumentsSheet.tsx:338",
  "src/components/ehr/EncounterDocumentsSheet.tsx:342",
  "src/components/ehr/sections/AssessmentSection.tsx:776",
  "src/components/ehr/sections/OutcomeSection.tsx:127",
  "src/components/ehr/sections/OutcomeSection.tsx:128",
  "src/components/ehr/sections/OutcomeSection.tsx:180",
  "src/components/ehr/sections/OutcomeSection.tsx:191",
  "src/components/ehr/sections/OutcomeSection.tsx:202",
  "src/components/ehr/sections/OutcomeSection.tsx:230",
  "src/components/ehr/sections/OutcomeSection.tsx:243",
  "src/components/ehr/sections/OutcomeSection.tsx:299",
  "src/components/ehr/sections/OutcomeSection.tsx:303",
  "src/components/ehr/sections/OutcomeSection.tsx:345",
  "src/components/ehr/sections/OutcomeSection.tsx:375",
  "src/components/lab/LabResultsSystem.tsx:169",
  "src/components/lab/LabResultsSystem.tsx:173",
  "src/components/lab/LabResultsSystem.tsx:299",
  "src/components/layout/UtilityStrip.tsx:63",
  "src/components/layout/UtilityStrip.tsx:73",
  "src/components/notifications/NotificationsCommsHub.tsx:469",
  "src/components/notifications/NotificationsCommsHub.tsx:472",
  "src/components/notifications/NotificationsCommsHub.tsx:477",
  "src/components/ward/WardManagementPanel.tsx:287",
  "src/components/ward/WardManagementPanel.tsx:506",
  "src/components/workspace-ops/BillingPanel.tsx:349",
  "src/components/workspace-ops/HRShiftsPanel.tsx:309",
  "src/components/workspace-ops/StockManagementPanel.tsx:426",
  "src/components/workspace-ops/StockManagementPanel.tsx:487",
  "src/features/core-transaction/components.tsx:188",
  "src/features/core-transaction/components.tsx:458",
];
